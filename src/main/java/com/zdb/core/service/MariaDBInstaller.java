package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesConstants;
import com.zdb.core.domain.KubernetesOperations;
import com.zdb.core.domain.MariaDBConfig;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ScheduleEntity;
import com.zdb.core.domain.ServiceSpec;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.repository.TagRepository;
import com.zdb.core.repository.ZDBMariaDBAccountRepository;
import com.zdb.core.repository.ZDBMariaDBConfigRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.mariadb.MariaDBAccount;
import com.zdb.mariadb.MariaDBConfiguration;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseResponse;
import hapi.services.tiller.Tiller.UninstallReleaseRequest;
import hapi.services.tiller.Tiller.UninstallReleaseResponse;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MariaDBInstaller implements ZDBInstaller {
	
	@Autowired
	private ZDBMariaDBAccountRepository accountRepository;

	@Autowired
	private ZDBMariaDBConfigRepository configRepository;
	
	@Autowired
	private ZDBReleaseRepository releaseRepository;
	
	@Autowired
	private ZDBRepository metaRepository;

	@Autowired
	private TagRepository tagRepository;
	
	@Autowired
	private K8SService k8sService;

	@Autowired
	private DiskUsageRepository diskUsageRepository;
	
	@Autowired
	@Qualifier("backupProvider")
	private BackupProviderImpl backupProvider;
	
	private static String storageClass;

	@Value("${chart.mariadb.storageClass:ibmc-block-silver}")
	public void setStorageClass(String storageType) {
		storageClass = storageType;
	}
	
		
	private static final String DEFAULT_ROOT_PASSWORD = "zdb12#$";
	private static final String DEFAULT_USER = "admin";
	private static final String DEFAULT_USER_PASSWORD = "zdbadmin12#$";
	private static final String DEFAULT_DATABASE_NAME = "mydb";
	private static final String DEFAULT_STORAGE_CLASS = "ibmc-block-silver";
	private static final String DEFAULT_STORAGE_SIZE = "20Gi";
	
	public void doInstall(Exchange exchange) {
		String chartUrl = exchange.getProperty(Exchange.CHART_URL, String.class);
		
		ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
		ZDBRepository metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);

		ReleaseManager releaseManager = null;
		
		try{ 
//			chartUrl = "file:///Users/a06919/git/charts/stable/mariadb/mariadb-4.2.0.tgz";
			/////////////////////////
			// chart 정로 로딩
			final URI uri = URI.create(chartUrl);
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}

			String chartName = chart.getMetadata().getName();
			String chartVersion = chart.getMetadata().getVersion();

			RequestEvent event = getRequestEvent(exchange);
			
			event.setChartName(chartName);
			event.setChartVersion(chartVersion);
			event.setOpertaion(KubernetesOperations.CREATE_DEPLOYMENT);
			event.setResultMessage("");
			event.setStatusMessage("서비스명 중복 체크.");
			
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

			final InstallReleaseRequest.Builder requestBuilder = InstallReleaseRequest.newBuilder();
			if (requestBuilder != null) {
				requestBuilder.setTimeout(300L);
				requestBuilder.setName(service.getServiceName().trim().toLowerCase());
				requestBuilder.setNamespace(service.getNamespace().trim().toLowerCase());

				requestBuilder.setWait(false);

				hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();

				InputStream is = new ClassPathResource("mariadb/create_values.template").getInputStream();
				
				String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());
				
				String rootPassword = service.getPassword() == null ? DEFAULT_ROOT_PASSWORD : service.getPassword();
				MariaDBConfig mariaDBConfig = service.getMariaDBConfig();
				
				boolean isClusterEnabled = service.isClusterEnabled();
				
				// service expose type : public or private
				boolean isPublicEnabled = false;
				ServiceSpec[] serviceSpec = service.getServiceSpec();
				if(serviceSpec != null && serviceSpec.length > 0) {
					String loadBalancerType = serviceSpec[0].getLoadBalancerType();
					if("public".equals(loadBalancerType)) {
						isPublicEnabled = true;
					}
				} else {
					isPublicEnabled = false;
				}
				
				String mariadbDatabase = mariaDBConfig.getMariadbDatabase() == null ? DEFAULT_DATABASE_NAME : mariaDBConfig.getMariadbDatabase();
				String mariadbUser = mariaDBConfig.getMariadbUser() == null ? DEFAULT_USER : mariaDBConfig.getMariadbUser();
				String mariadbPassword = mariaDBConfig.getMariadbPassword() == null ? DEFAULT_USER_PASSWORD : mariaDBConfig.getMariadbPassword();
				
				PersistenceSpec[] persistenceSpec = service.getPersistenceSpec();
				
				String m = persistenceSpec[0].getPodType();
				String masterStorageClass = persistenceSpec[0].getStorageClass() == null ? storageClass : persistenceSpec[0].getStorageClass();
				String masterSize = persistenceSpec[0].getSize() == null ? DEFAULT_STORAGE_SIZE : persistenceSpec[0].getSize();
				
				String s = persistenceSpec[1].getPodType();
				String slaveStorageClass = persistenceSpec[1].getStorageClass() == null ? DEFAULT_STORAGE_CLASS : persistenceSpec[1].getStorageClass();
				String slaveSize = persistenceSpec[1].getSize() == null ? DEFAULT_STORAGE_SIZE : persistenceSpec[1].getSize();
				
				PodSpec[] podSpec = service.getPodSpec();
				
				String master = podSpec[0].getPodType();
				ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
				String masterCpu = masterSpec.getCpu();
				String masterMemory = masterSpec.getMemory();
				
				String slave = podSpec[1].getPodType();
				ResourceSpec slaveSpec = podSpec[1].getResourceSpec()[0];
				String slaveCpu = slaveSpec.getCpu();
				String slaveMemory = slaveSpec.getMemory();
				int clusterSlaveCount = service.getClusterSlaveCount() == 0 ? 1 : service.getClusterSlaveCount();
				
				inputJson = inputJson.replace("${rootUser.password}", rootPassword); // configmap
				inputJson = inputJson.replace("${replication.enabled}", ""+isClusterEnabled); // configmap
				inputJson = inputJson.replace("${db.user}", mariadbUser);// configmap
				inputJson = inputJson.replace("${db.password}", mariadbPassword); // configmap
				inputJson = inputJson.replace("${db.name}", mariadbDatabase);// input        *******   필수값 
				inputJson = inputJson.replace("${master.persistence.storageClass}", masterStorageClass);// configmap
				inputJson = inputJson.replace("${master.persistence.size}", masterSize);// input , *******   필수값 
				inputJson = inputJson.replace("${master.resources.requests.cpu}", masterCpu);// input , *******   필수값  
				inputJson = inputJson.replace("${master.resources.requests.memory}", masterMemory);// input *******   필수값 
				inputJson = inputJson.replace("${master.resources.limits.cpu}", masterCpu);// input , *******   필수값  
				inputJson = inputJson.replace("${master.resources.limits.memory}", masterMemory);// input *******   필수값 
				inputJson = inputJson.replace("${slave.persistence.storageClass}", slaveStorageClass);// configmap
				inputJson = inputJson.replace("${slave.persistence.size}", slaveSize); // input *******   필수값 
				inputJson = inputJson.replace("${slave.resources.requests.cpu}", slaveCpu);// input*******   필수값 
				inputJson = inputJson.replace("${slave.resources.requests.memory}", slaveMemory);// input *******   필수값 
				inputJson = inputJson.replace("${slave.resources.limits.cpu}", slaveCpu);// input*******   필수값 
				inputJson = inputJson.replace("${slave.resources.limits.memory}", slaveMemory);// input *******   필수값 
				inputJson = inputJson.replace("${slave.replicas}", clusterSlaveCount+"");// input *******   필수값 
				inputJson = inputJson.replace("${service.master.publicip.enabled}", isPublicEnabled+"");// input *******   필수값 
				if(isClusterEnabled) {
					inputJson = inputJson.replace("${service.slave.publicip.enabled}", isPublicEnabled+"");// input *******   필수값 
				} else {
					inputJson = inputJson.replace("${service.slave.publicip.enabled}", "false");// input *******   필수값 
					
				}
				inputJson = inputJson.replace("${buffer.pool.size}", K8SUtil.getBufferSize(masterMemory));// 자동계산 *******   필수값 
				
				String characterSet = service.getCharacterSet();
				inputJson = inputJson.replace("${character.set.server}", characterSet == null || characterSet.isEmpty() ? "utf8" : characterSet);
				
				if("utf8".equalsIgnoreCase(characterSet)) {
					inputJson = inputJson.replace("${collation.server}", "utf8_general_ci");
				} else if("euckr".equalsIgnoreCase(characterSet)) {
					inputJson = inputJson.replace("${collation.server}", "euckr_korean_ci");
				} else if("utf8mb4".equalsIgnoreCase(characterSet)) {
					inputJson = inputJson.replace("${collation.server}", "utf8mb4_general_ci");
				} else if("utf16".equalsIgnoreCase(characterSet)) {
					inputJson = inputJson.replace("${collation.server}", "utf16_general_ci");
				}
				
				valuesBuilder.setRaw(inputJson);
				
				final Future<InstallReleaseResponse> releaseFuture = releaseManager.install(requestBuilder, chart);
				final Release release = releaseFuture.get().getRelease();

				if (release != null) {
					ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
					if(releaseMeta == null) {
						releaseMeta = new ReleaseMetaData();
					}
					releaseMeta.setAction("CREATE");
					releaseMeta.setApp(release.getChart().getMetadata().getName());
					releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
					releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
					releaseMeta.setChartName(release.getChart().getMetadata().getName());
					releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds() * 1000L));
					releaseMeta.setNamespace(service.getNamespace());
					releaseMeta.setReleaseName(service.getServiceName());
					String status = release.getInfo().getStatus().getCode().name();
					if("DEPLOYED".equals(status)) {
						status = "CREATING";
					} 
					releaseMeta.setStatus(status);
					releaseMeta.setDescription(release.getInfo().getDescription());
					releaseMeta.setInputValues(valuesBuilder.getRaw());
					releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
					releaseMeta.setDbname(mariadbDatabase);
					releaseMeta.setManifest(release.getManifest());
					releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));
					releaseMeta.setPublicEnabled(isPublicEnabled);
					releaseMeta.setUserId(service.getRequestUserId());

					log.info(new Gson().toJson(releaseMeta));

					releaseRepository.save(releaseMeta);

					Tag typeTag = new Tag();
					typeTag.setNamespace(service.getNamespace());
					typeTag.setReleaseName(service.getServiceName());
					typeTag.setTagName(service.getServiceType());		
					
					tagRepository.save(typeTag);
					
//					if (service.getPurpose() != null) {
//						Tag purposeTag = new Tag();
//						purposeTag.setNamespace(service.getNamespace());
//						purposeTag.setReleaseName(service.getServiceName());
//						purposeTag.setTagName(service.getPurpose().toLowerCase());
//						
//						tagRepository.save(purposeTag);
//					}						
					
					// TODO 생성 UI에서 선택 할 수 있는 옵션 추가 후 적용.
					// LB목록 조회 및 추가 주문 로직 구현 필요.
					exchange.setProperty(KubernetesConstants.KUBERNETES_SERVICE_NAME, service.getServiceName());
					exchange.setProperty(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, service.getNamespace());

					ZDBMariaDBAccount account = MariaDBAccount.createAdminAccount(accountRepository, service.getNamespace(), service.getServiceName(), mariadbUser, mariadbPassword);
					if (account == null) {
						log.error("cannot insert admin account into DB.");
					}

					// admin 권한 변경 
					
					Thread.sleep(5000);
					
					final CountDownLatch lacth = new CountDownLatch(1);

					new Thread(new Runnable() {

						@Override
						public void run() {
							long s = System.currentTimeMillis();
							
							try {
								while((System.currentTimeMillis() - s) < 10 * 60 * 1000) {
									Thread.sleep(3000);
									List<Pod> pods = k8sService.getPods(service.getNamespace(), service.getServiceName());
									boolean isAllReady = true;
									for(Pod pod : pods) {
										boolean isReady = K8SUtil.IsReady(pod);
										isAllReady = isAllReady && isReady;
									}
									
									ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
									if(isAllReady) {
										if(releaseMeta != null) {
											releaseMeta.setStatus("DEPLOYED");
											releaseRepository.save(releaseMeta);
										}
										lacth.countDown();
										System.out.println("------------------------------------------------- service create success! ------------------------------------------------- ");
										break;
									} else {
										if(releaseMeta != null) {
											releaseMeta.setStatus("CREATING");
											releaseRepository.save(releaseMeta);
										}
									}
								}
							} catch (Exception e) {
								log.error(e.getMessage(), e);
							}

						}
					}).start();
					
					lacth.await(600, TimeUnit.SECONDS);
					
					if(lacth.getCount() == 0) {
						log.info("update admin grant option.");
						MariaDBAccount.updateAdminPrivileges(service.getNamespace(), service.getServiceName(), account.getUserId());
						
						if(service.isBackupEnabled()) {
							// 스케줄 등록...
							ScheduleEntity schedule = new ScheduleEntity();
							schedule.setNamespace(service.getNamespace());
							schedule.setServiceType(service.getServiceType());
							schedule.setServiceName(service.getServiceName());
							schedule.setStartTime("01:00");
							schedule.setStorePeriod(2);
							schedule.setUseYn("Y");
							backupProvider.saveSchedule(exchange.getProperty(Exchange.TXID, String.class), schedule);
						}
					} else {
						log.error("{} > {} > {} 권한 변경 실패!", service.getNamespace(), service.getServiceName(), account.getUserId());
					}
					
				} else {
					event.setStatus(IResult.ERROR);
					event.setResultMessage("DB 생성 오류");
					
					ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
				}
			}
			
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			RequestEvent event = getRequestEvent(exchange);
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				event.setResultMessage("Unauthorized");
			} else {
				event.setResultMessage(e.getMessage());
			}
			
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			RequestEvent event = getRequestEvent(exchange);
			
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));
			
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
		}

	}
	
	/**
	 * @param exchange
	 * @return
	 */
	public RequestEvent getRequestEvent(Exchange exchange) {
		ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
		String txId = exchange.getProperty(Exchange.TXID, String.class);

		RequestEvent requestEvent = metaRepository.findByTxId(txId);
		if (requestEvent == null) {
			requestEvent = new RequestEvent();
			requestEvent.setTxId(txId);
			requestEvent.setNamespace(service.getNamespace());
			requestEvent.setServiceName(service.getServiceName());
			requestEvent.setServiceType(service.getServiceType());
			requestEvent.setStartTime(new Date(System.currentTimeMillis()));
			requestEvent.setEventType(EventType.Deployment.name());
		}

		return requestEvent;
	}
	
	@Override
	public void doUnInstall(Exchange exchange) {
		String txId = exchange.getProperty(Exchange.TXID, String.class);
		String serviceName = exchange.getProperty(Exchange.SERVICE_NAME, String.class);
		String namespace = exchange.getProperty(Exchange.NAMESPACE, String.class);
		
		ZDBRepository metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);
		
		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();

		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setServiceType(ZDBType.MariaDB.getName());
		event.setNamespace(namespace);
		event.setEventType(EventType.Delete.name());
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setOpertaion(KubernetesOperations.DELETE_SERVICE_INSTANCE);

		ReleaseManager releaseManager = null;
		try {
			DefaultKubernetesClient client = (DefaultKubernetesClient) K8SUtil.kubernetesClient().inNamespace(namespace);

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);
			
			Iterable<ReleaseMetaData> releaseList = releaseRepository.findAll();
			
			String chartName = null;

			for (ReleaseMetaData release : releaseList) {
				if (namespace.equals(release.getNamespace()) && serviceName.equals(release.getReleaseName())) {
					chartName = release.getApp();
					break;
				}
			}
			
			if (chartName == null) {
				String msg = "설치된 서비스가 존재하지 않습니다.";
				event.setResultMessage(msg);
				event.setStatusMessage("서비스 삭제 실패");
				event.setStatus(IResult.ERROR);
				event.setEndTime(new Date(System.currentTimeMillis()));
				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

			    return;
			}

			final UninstallReleaseRequest.Builder uninstallRequestBuilder = UninstallReleaseRequest.newBuilder();

			uninstallRequestBuilder.setName(serviceName.trim().toLowerCase()); // set releaseName
			uninstallRequestBuilder.setPurge(true); // --purge

			ReleaseMetaData findByReleaseName = releaseRepository.findByReleaseName(serviceName);
			if (findByReleaseName != null) {
				findByReleaseName.setStatus("DELETING");
				findByReleaseName.setUpdateTime(new Date(System.currentTimeMillis()));
				releaseRepository.save(findByReleaseName);
			}
			
			Result result = new Result(txId, IResult.OK, "Delete Service instance. [" + serviceName + "]");
			final Future<UninstallReleaseResponse> releaseFuture = releaseManager.uninstall(uninstallRequestBuilder.build());
			
			if (releaseFuture != null) {
				final Release release = releaseFuture.get().getRelease();
				result.putValue(IResult.DELETE, release);

				ReleaseMetaData releaseMeta = new ReleaseMetaData();
				releaseMeta.setAction("DELETE");
				releaseMeta.setApp(release.getChart().getMetadata().getName());
				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
				releaseMeta.setChartName(release.getChart().getMetadata().getName());
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()));
				releaseMeta.setNamespace(namespace);
				releaseMeta.setReleaseName(serviceName);
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				releaseMeta.setDescription(release.getInfo().getDescription());
				releaseMeta.setManifest(release.getManifest());
				releaseMeta.setStatus("DELETED");
				releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));

				log.info(new Gson().toJson(releaseMeta));

				findByReleaseName = releaseRepository.findByReleaseName(serviceName);
				if (findByReleaseName != null) {
					findByReleaseName.setStatus(release.getInfo().getStatus().getCode().name());
					findByReleaseName.setUpdateTime(new Date(System.currentTimeMillis()));
					releaseRepository.save(findByReleaseName);
				} else {
					releaseRepository.save(releaseMeta);
				}

//				{ // StatefulSet 삭제
//					List<StatefulSet> statefulSets = serviceOverview.getStatefulSets();
//
//					for (StatefulSet sfs : statefulSets) {
//						client.inNamespace(namespace).apps().statefulSets().withName(sfs.getMetadata().getName()).delete();
//					}
//				}
//				
//				{ // Pod 삭제
//					List<Pod> pods = serviceOverview.getPods();
//
//					for (Pod pod : pods) {
//						client.inNamespace(namespace).pods().withName(pod.getMetadata().getName()).delete();
//					}
//				}
//				
				{ // pvc 삭제
					List<PersistentVolumeClaim> persistentVolumeClaims = K8SUtil.getPersistentVolumeClaims(namespace, serviceName);

					for (PersistentVolumeClaim pvc : persistentVolumeClaims) {
						client.inNamespace(namespace).persistentVolumeClaims().withName(pvc.getMetadata().getName()).delete();
					}
				}
//
//				{
//					// Expose Service 삭제.
//					List<io.fabric8.kubernetes.api.model.Service> serviceList = serviceOverview.getServices();
//					for (io.fabric8.kubernetes.api.model.Service svc : serviceList) {
//						boolean deleteService = K8SUtil.doDeleteService(namespace, svc.getMetadata().getName());
//						log.info("{} delete. {}", svc.getMetadata().getName(), deleteService);
//					}
//				}
				{ // account 삭제
					MariaDBAccount.deleteAccounts(accountRepository, namespace, serviceName);
				}

				{ // config 삭제
					MariaDBConfiguration.deleteConfig(configRepository, namespace, serviceName);
				}
				
				// disk usage 정보 삭제처리 
				diskUsageRepository.deleteByNamespaceAndReleaseName(namespace, serviceName);
				
				// tag 정보 삭제 
				tagRepository.deleteByNamespaceAndReleaseName(namespace, serviceName);
				
				// Backup Resource 삭제 요청
				backupProvider.removeServiceResource(txId, namespace, ZDBType.MariaDB.getName(), serviceName);
			} else {
				String msg = "설치된 서비스가 존재하지 않습니다.";
				event.setResultMessage(msg);
				event.setStatusMessage("서비스 삭제 실패");
				event.setStatus(IResult.ERROR);
				event.setEndTime(new Date(System.currentTimeMillis()));
				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

//				return new Result(txId, IResult.ERROR, "Service 삭제 오류!");
			    return;
			}

		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				event.setResultMessage("Unauthorized");
			} else {
				event.setResultMessage(e.getMessage());
			}
			
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));
			
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
		} finally {
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
			if(releaseManager != null){
				try {
					releaseManager.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}
}