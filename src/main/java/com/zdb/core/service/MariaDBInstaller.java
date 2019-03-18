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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.zdb.core.collector.MetaDataCollector;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesConstants;
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
import com.zdb.core.domain.ZDBConfig;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.ZDBConfigRepository;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.ExecUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.mariadb.MariaDBAccount;
import com.zdb.mariadb.MariaDBConfiguration;
import com.zdb.mariadb.MariaDBConnection;

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
public class MariaDBInstaller extends ZDBInstallerAdapter {
	private static String storageClass;

	@Value("${chart.mariadb.storageClass:ibmc-block-silver}")
	public void setStorageClass(String storageType) {
		storageClass = storageType;
	}
	
	@Autowired
	private MetaDataCollector metaDataCollector;
	
	@Autowired
	protected ZDBConfigRepository zdbConfigRepository;
		
	private static final String DEFAULT_ROOT_PASSWORD = "zdb12#$";
	private static final String DEFAULT_USER = "admin";
	private static final String DEFAULT_USER_PASSWORD = "zdbadmin12#$";
	private static final String DEFAULT_DATABASE_NAME = "mydb";
	private static final String DEFAULT_STORAGE_SIZE = "20Gi";
	private static final String DEFAULT_VERSION = "10.2.14";
	
	public void doInstall(Exchange exchange) {
		String chartUrl = exchange.getProperty(Exchange.CHART_URL, String.class);
		
		ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
		
		ZDBRepository metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);

//		ReleaseManager releaseManager = null;
		
		RequestEvent event = getRequestEvent(exchange);
		event.setOperation(RequestEvent.CREATE);
		
		try (
				DefaultKubernetesClient client = K8SUtil.kubernetesClient();
				Tiller tiller = new Tiller(client);
				ReleaseManager releaseManager = new ReleaseManager(tiller);
				){ 
//			chartUrl = "file:///Users/a06919/git/charts/stable/mariadb/mariadb-4.2.0.tgz";
//			chartUrl = "file:///Users/a06919/mariadb-4.2.2.tgz";
			/////////////////////////
			// chart 정로 로딩
			final URI uri = URI.create(chartUrl);
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}

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
				
				String mariadbVersion = service.getVersion() == null ? DEFAULT_VERSION : service.getVersion();
				
				String mariadbDatabase = mariaDBConfig.getMariadbDatabase() == null ? DEFAULT_DATABASE_NAME : mariaDBConfig.getMariadbDatabase();
				String mariadbUser = mariaDBConfig.getMariadbUser() == null ? DEFAULT_USER : mariaDBConfig.getMariadbUser();
				String mariadbPassword = mariaDBConfig.getMariadbPassword() == null ? DEFAULT_USER_PASSWORD : mariaDBConfig.getMariadbPassword();
				
				PersistenceSpec[] persistenceSpec = service.getPersistenceSpec();
				
				String m = persistenceSpec[0].getPodType();
				String masterStorageClass = persistenceSpec[0].getStorageClass() == null ? storageClass : persistenceSpec[0].getStorageClass();
				String masterSize = persistenceSpec[0].getSize() == null ? DEFAULT_STORAGE_SIZE : persistenceSpec[0].getSize();
				
				
				PodSpec[] podSpec = service.getPodSpec();
				
				String master = podSpec[0].getPodType();
				ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
				String masterCpu = masterSpec.getCpu();
				String masterMemory = masterSpec.getMemory();
				
				int clusterSlaveCount = service.getClusterSlaveCount() == 0 ? 1 : service.getClusterSlaveCount();
				
				String masterNodeAffinityValues = masterSpec.getWorkerPool();
				
				inputJson = inputJson.replace("${image.tag}", mariadbVersion); // db version
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

				if(persistenceSpec != null && persistenceSpec.length > 1) {
					String slaveStorageClass = persistenceSpec[1].getStorageClass() == null ? storageClass : persistenceSpec[1].getStorageClass();
					String slaveSize = persistenceSpec[1].getSize() == null ? DEFAULT_STORAGE_SIZE : persistenceSpec[1].getSize();

					String slave = podSpec[1].getPodType();
					ResourceSpec slaveSpec = podSpec[1].getResourceSpec()[0];
					String slaveCpu = slaveSpec.getCpu();
					String slaveMemory = slaveSpec.getMemory();
					String slaveNodeAffinityValues = slaveSpec.getWorkerPool();

					inputJson = inputJson.replace("${slave.persistence.storageClass}", slaveStorageClass);// configmap
					inputJson = inputJson.replace("${slave.persistence.size}", slaveSize); // input *******   필수값 
					inputJson = inputJson.replace("${slave.resources.requests.cpu}", slaveCpu);// input*******   필수값 
					inputJson = inputJson.replace("${slave.resources.requests.memory}", slaveMemory);// input *******   필수값 
					inputJson = inputJson.replace("${slave.resources.limits.cpu}", slaveCpu);// input*******   필수값 
					inputJson = inputJson.replace("${slave.resources.limits.memory}", slaveMemory);// input *******   필수값 
					inputJson = inputJson.replace("${slave.replicas}", clusterSlaveCount+"");// input *******   필수값 
					inputJson = inputJson.replace("${slave.affinity.nodeAffinity.values}", slaveNodeAffinityValues);
				}
				
				inputJson = inputJson.replace("${service.master.publicip.enabled}", isPublicEnabled+"");// input *******   필수값 
				if(isClusterEnabled) {
					inputJson = inputJson.replace("${service.slave.publicip.enabled}", isPublicEnabled+"");// input *******   필수값 
				} else {
					inputJson = inputJson.replace("${service.slave.publicip.enabled}", "false");// input *******   필수값 
					
				}
				inputJson = inputJson.replace("${buffer.pool.size}", K8SUtil.getBufferSize(masterMemory));// 자동계산 *******   필수값 
				inputJson = inputJson.replace("${master.antiAffinity}", "hard"); // 향후 input으로 받을 예정
				inputJson = inputJson.replace("${master.affinity.nodeAffinity.values}", masterNodeAffinityValues);
				
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
					releaseMeta.setAppVersion(mariadbVersion);
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
							MariaDBConnection connection = null;
							
							try {
								while((System.currentTimeMillis() - s) < 10 * 60 * 1000) {
									Thread.sleep(3000);
									List<Pod> pods = k8sService.getPods(service.getNamespace(), service.getServiceName());
									boolean isAllReady = true;
									for(Pod pod : pods) {
										boolean isReady = K8SUtil.IsReady(pod);
										isAllReady = isAllReady && isReady;
									}
									
									metaDataCollector.save(pods);
									
									ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
									
									if(isAllReady) {
										if(releaseMeta != null) {
											while(true) {
												try(DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
//													connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
													
													List<Pod> items = K8SUtil.kubernetesClient().inNamespace(service.getNamespace()).pods()
															.withLabel("release", service.getServiceName())
															.withLabel("component", "master")
															.list().getItems();
													
													String podName = "";
													for (Pod pod : items) {
														podName = pod.getMetadata().getName();
													}
													
													String cmd = "mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"show databases;\"";
													
													String result = new ExecUtil().exec(client, service.getNamespace(), podName, "mariadb", cmd);
													
													if(result != null && result.indexOf("mysql") > -1) {
														break;
													}
												}catch(Exception e) {
													Thread.sleep(5000);
												}
												
//												try {
//													connection = MariaDBConnection.getRootMariaDBConnection(service.getNamespace(), service.getServiceName());
//													break;
//												}catch(Exception e) {
//													Thread.sleep(5000);
//												}
											}
											releaseMeta.setStatus("DEPLOYED");
											releaseRepository.save(releaseMeta);
										}
										lacth.countDown();
										log.info("-------------------------- service create success! -- ["+service.getNamespace() +" > "+ service.getServiceName() +"]");
										
										messageSender.sendToClient("mariadb installer");
										messageSender.sendToClientRefresh(service.getServiceName());
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
							} finally {
								if(connection != null) {
									connection.close();
								}
							}

						}
					}).start();
					
					lacth.await(600, TimeUnit.SECONDS);
					
					if(lacth.getCount() > 0) {
						event.setStatus(IResult.ERROR);
						event.setResultMessage("서비스 생성 실패. - 10분 타임아웃 ["+service.getNamespace() +" > "+ service.getServiceName() +"]");
						event.setEndTime(new Date(System.currentTimeMillis()));
						
						releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
						if(releaseMeta != null) {
							if("CREATING".equals(releaseMeta.getStatus())) {
								releaseMeta.setStatus("ERROR");
								releaseMeta.setDescription("서비스 생성 실패.(타임아웃)["+service.getNamespace() +" > "+ service.getServiceName() +"]");
								releaseRepository.save(releaseMeta);
							}
						}
						
						log.error("{} > {} > {} 권한 변경 실패!", service.getNamespace(), service.getServiceName(), account.getUserId());
					}
					
					log.info("update admin grant option.");
					try {
						
						Pod pod = k8sService.getPod(service.getNamespace(), service.getServiceName(), "master");
						if(pod != null) {
							if(K8SUtil.IsReady(pod)) {
								MariaDBAccount.updateAdminPrivileges(service.getNamespace(), service.getServiceName(), account.getUserId());
							}
							log.info("admin 권한 적용 완료 ["+service.getNamespace() +" > "+ service.getServiceName() +"]");
						} else {
							log.error("권한 적용 오류 -  master db is null." + service.getNamespace() +" > "+ service.getServiceName());
						}
					} catch (Exception e) {
						log.error("권한 적용 오류 ["+service.getNamespace() +" > "+ service.getServiceName() +"]", e.getMessage()); 
					}
					
					if(service.isBackupEnabled()) {
						ScheduleEntity schedule = new ScheduleEntity();
						schedule.setNamespace(service.getNamespace());
						schedule.setServiceType(service.getServiceType());
						schedule.setServiceName(service.getServiceName());
						
						List<ZDBConfig> zdbConfigList = zdbConfigRepository.findByNamespace(service.getNamespace());
						if(zdbConfigList.isEmpty()) {
							zdbConfigList = zdbConfigRepository.findByNamespace("global");
							if(zdbConfigList.isEmpty()) {
								schedule.setStartTime(ZDBConfigService.backupTimeValue);
								schedule.setStorePeriod(Integer.parseInt(ZDBConfigService.backupDuratioValue));
							} else {
								zdbConfigList.forEach(zdbConfig -> {
									if (zdbConfig.getConfig().equals(ZDBConfigService.backupTimeConfig))
										schedule.setStartTime(zdbConfig.getValue());
									else if (zdbConfig.getConfig().equals(ZDBConfigService.backupDurationConfig))
										schedule.setStorePeriod(Integer.parseInt(zdbConfig.getValue()));
								});
							}
						} else {
							zdbConfigList.forEach(zdbConfig -> {
								if (zdbConfig.getConfig().equals(ZDBConfigService.backupTimeConfig))
									schedule.setStartTime(zdbConfig.getValue());
								else if (zdbConfig.getConfig().equals(ZDBConfigService.backupDurationConfig))
									schedule.setStorePeriod(Integer.parseInt(zdbConfig.getValue()));
							});
						}
						
						schedule.setUseYn("Y");
						schedule.setDeleteYn("N");
						backupProvider.saveSchedule(exchange.getProperty(Exchange.TXID, String.class), schedule);
					}
					
					event.setStatus(IResult.OK);
					event.setResultMessage("서비스 생성 완료 ["+service.getNamespace() +" > "+ service.getServiceName() +"]");
					event.setEndTime(new Date(System.currentTimeMillis()));
					
					messageSender.sendToClient("mariadb installer");
				} else {
					event.setStatus(IResult.ERROR);
					event.setResultMessage("Installation failed." );
					event.setEndTime(new Date(System.currentTimeMillis()));
					
					messageSender.sendToClient("mariadb installer");
				}
				messageSender.sendToClientRefresh(service.getServiceName());
			}
			
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				event.setResultMessage("Unauthorized");
			} else {
				event.setResultMessage("Resource not found. ["+e.getMessage() +"]");
			}
			
			saveReleaseError(service.getServiceName(), e);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));

			saveReleaseError(service.getServiceName(), e);
		} finally {
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

		RequestEvent requestEvent = new RequestEvent();
		requestEvent.setTxId(txId);
		requestEvent.setNamespace(service.getNamespace());
		requestEvent.setServiceName(service.getServiceName());
		requestEvent.setServiceType(service.getServiceType());
		requestEvent.setStartTime(new Date(System.currentTimeMillis()));

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
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setOperation(RequestEvent.DELETE);

//		ReleaseManager releaseManager = null;
		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();
				Tiller tiller = new Tiller(client);
				ReleaseManager releaseManager = new ReleaseManager(tiller);) {
				client.inNamespace(namespace);
			
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			
			if (releaseMetaData == null) {
				String msg = "설치된 서비스가 존재하지 않습니다.";
				event.setResultMessage(msg);
				event.setStatus(IResult.ERROR);
				event.setEndTime(new Date(System.currentTimeMillis()));
				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			    return;
			} 

			final UninstallReleaseRequest.Builder uninstallRequestBuilder = UninstallReleaseRequest.newBuilder();

			uninstallRequestBuilder.setName(serviceName.trim().toLowerCase()); // set releaseName
			uninstallRequestBuilder.setPurge(true); // --purge

			if (releaseMetaData.getStatus().equals("ERROR")) {
				releaseMetaData.setStatus("DELETED");
				releaseMetaData.setUpdateTime(new Date(System.currentTimeMillis()));
				releaseRepository.save(releaseMetaData);
				return;
			} else {
				releaseMetaData.setStatus("DELETING");
				releaseMetaData.setUpdateTime(new Date(System.currentTimeMillis()));
				releaseRepository.save(releaseMetaData);
			}
			
			Result result = new Result(txId, IResult.OK);
			final Future<UninstallReleaseResponse> releaseFuture = releaseManager.uninstall(uninstallRequestBuilder.build());
			
			if (releaseFuture != null) {
				Release release = releaseFuture.get().getRelease();
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

				releaseMetaData = releaseRepository.findByReleaseName(serviceName);
				if (releaseMetaData != null) {
					releaseMetaData.setStatus(release.getInfo().getStatus().getCode().name());
					releaseMetaData.setUpdateTime(new Date(System.currentTimeMillis()));
					releaseRepository.save(releaseMetaData);
				} else {
					releaseRepository.save(releaseMeta);
				}

				{ // pvc 삭제
					List<PersistentVolumeClaim> persistentVolumeClaims = K8SUtil.getPersistentVolumeClaims(namespace, serviceName);

					for (PersistentVolumeClaim pvc : persistentVolumeClaims) {
						client.inNamespace(namespace).persistentVolumeClaims().withName(pvc.getMetadata().getName()).delete();
					}
				}
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
				
				try {
					// Backup Resource 삭제 요청
					backupProvider.removeServiceResource(txId, namespace, ZDBType.MariaDB.getName(), serviceName);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
				
				// TODO Event 정보 삭제
//				metaRepository.deleteByNamespaceAndReleaseName
				
				event.setResultMessage("서비스 삭제 완료 ["+namespace +" > "+ serviceName +"]");
				event.setStatus(IResult.OK);
				event.setEndTime(new Date(System.currentTimeMillis()));
			} else {
				String msg = "설치된 서비스가 존재하지 않습니다. ["+namespace +" > "+ serviceName +"]";
				event.setResultMessage(msg);
				event.setStatus(IResult.ERROR);
				event.setEndTime(new Date(System.currentTimeMillis()));
				
				releaseMetaData.setStatus("DELETED");
				releaseMetaData.setUpdateTime(new Date(System.currentTimeMillis()));
				releaseRepository.save(releaseMetaData);
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
			saveDeleteReleaseError(serviceName, e);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));
			
			saveDeleteReleaseError(serviceName, e);
			
		} finally {
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
			messageSender.sendToClient("mariadb installer");
			messageSender.sendToClientRefresh(serviceName);
		}
	}
	
}
