package com.zdb.core.service;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.zdb.core.domain.Connection;
import com.zdb.core.domain.ConnectionInfo;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesOperations;
import com.zdb.core.domain.Mycnf;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.MycnfRepository;
import com.zdb.core.repository.ZDBMariaDBAccountRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.mariadb.MariaDBAccount;
import com.zdb.mariadb.MariaDBShutDownUtil;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * ZDBRestService Implementation
 * 
 * @author 06919
 *
 */
@Service("mariadbService")
@Slf4j
@Configuration
public class MariaDBServiceImpl extends AbstractServiceImpl {

	@Value("${chart.mariadb.url}")
	public void setChartUrl(String url) {
		chartUrl = url;
	}
	
	@Autowired
	private ZDBMariaDBAccountRepository zdbMariaDBAccountRepository;

//	@Autowired
//	private ZDBMariaDBConfigRepository zdbMariaDBConfigRepository;
	
	@Autowired
	private ZDBReleaseRepository releaseRepository;
	
	@Autowired
	private MycnfRepository configRepository;

	@Override
	public Result getDeployment(String namespace, String serviceName) {

		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			if (client != null) {
				final URI uri = URI.create(chartUrl);
				final URL url = uri.toURL();
				Chart.Builder chart = null;
				try (final URLChartLoader chartLoader = new URLChartLoader()) {
					chart = chartLoader.load(url);
				}

				String chartName = chart.getMetadata().getName();
				String deploymentName = serviceName + "-" + chartName;

				log.debug("deploymentName: {}", deploymentName);
				Deployment deployment = client.inNamespace(namespace).extensions().deployments().withName(deploymentName).get();

				if (deployment != null) {
					return new Result("", Result.OK).putValue(IResult.DEPLOYMENT, deployment);
				} else {
					log.debug("no deployment. namespace: {}, releaseName: {}", namespace, serviceName);
				}
			}
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue("deployment", "");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#deploymentList()
	 */
	@Override
	public Result getDeployments(String namespace, String serviceType) throws Exception {
		return super.getDeployments(namespace, serviceType);
	}
		
	@Override
	public Result updateScale(String txId, final ZDBEntity service) throws Exception {

		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();

		event.setTxId(txId);
		event.setServiceName(service.getServiceName());
		event.setServiceType(ZDBType.MariaDB.getName());
		event.setNamespace(service.getNamespace());
		event.setEventType(EventType.Update.name());
		event.setOpertaion(KubernetesOperations.SCALE_REPLICATION_CONTROLLER_OPERATION);
		event.setStartTime(new Date(System.currentTimeMillis()));

		Result result = new Result(txId);

		ReleaseManager releaseManager = null;
		try {
			final URI uri = URI.create(chartUrl);
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}

			String chartVersion = chart.getMetadata().getVersion();

			// 서비스 명 체크
			if (!K8SUtil.isServiceExist(service.getNamespace(), service.getServiceName())) {
				String msg = "서비스가 존재하지 않습니다. [" + service.getServiceName() + "]";

				log.error(msg);

				event.setChartVersion(chartVersion);
				event.setResultMessage(msg);
				event.setStatus(IResult.ERROR);
				event.setStatusMessage("Update Scale 오류");
				event.setEndTIme(new Date(System.currentTimeMillis()));

				ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);

				return new Result(txId, IResult.ERROR, msg);
			}

			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);
			
			final UpdateReleaseRequest.Builder requestBuilder = UpdateReleaseRequest.newBuilder();
			requestBuilder.setTimeout(300L);
			requestBuilder.setName(service.getServiceName());
			requestBuilder.setWait(false);
			
			requestBuilder.setReuseValues(true);

			hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();

			InputStream is = new ClassPathResource("mariadb/update_values.template").getInputStream();
			
			String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());
			
			PodSpec[] podSpec = service.getPodSpec();
			
			String master = podSpec[0].getPodType();
			ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
			String masterResourceType = masterSpec.getResourceType();
			String masterCpu = masterSpec.getCpu();
			String masterMemory = masterSpec.getMemory();
			
			String slave = podSpec[1].getPodType();
			ResourceSpec slaveSpec = podSpec[1].getResourceSpec()[0];
			String slaveCpu = slaveSpec.getCpu();
			String slaveMemory = slaveSpec.getMemory();
			
			inputJson = inputJson.replace("${master.resources.requests.cpu}", masterCpu);// input , *******   필수값  
			inputJson = inputJson.replace("${master.resources.requests.memory}", masterMemory);// input *******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.cpu}", slaveCpu);// input*******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.memory}", slaveMemory);// input *******   필수값 
			inputJson = inputJson.replace("${master.resources.limits.cpu}", masterCpu);// input , *******   필수값  
			inputJson = inputJson.replace("${master.resources.limits.memory}", masterMemory);// input *******   필수값 
			inputJson = inputJson.replace("${slave.resources.limits.cpu}", slaveCpu);// input*******   필수값 
			inputJson = inputJson.replace("${slave.resources.limits.memory}", slaveMemory);// input *******   필수값 
			
			valuesBuilder.setRaw(inputJson);

			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);

			log.info(service.getServiceName() + " update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();
			
			if (release != null) {
				ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
				if(releaseMeta == null) {
					releaseMeta = new ReleaseMetaData();
				}
				releaseMeta.setAction("UPDATE");
				releaseMeta.setApp(release.getChart().getMetadata().getName());
				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
				releaseMeta.setChartName(release.getChart().getMetadata().getName());
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds() * 1000L));
				releaseMeta.setNamespace(service.getNamespace());
				releaseMeta.setReleaseName(service.getServiceName());
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				releaseMeta.setDescription(release.getInfo().getDescription());
				releaseMeta.setInputValues(valuesBuilder.getRaw());
				releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
				releaseMeta.setManifest(release.getManifest());
				releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));

				log.info(new Gson().toJson(releaseMeta));
				
				releaseRepository.save(releaseMeta);
			}

			log.info(service.getServiceName() + " update success!");
			result = new Result(txId, IResult.RUNNING, "Update request. [" + service.getServiceName() + "]").putValue(IResult.UPDATE, release);

			event.setResultMessage(service.getServiceName() + " update success.");
			event.setStatus(IResult.RUNNING);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);

		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				event.setResultMessage("Unauthorized");
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				event.setResultMessage(e.getMessage());
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			return Result.RESULT_FAIL(txId, e);
		} finally {
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);

			if (releaseManager != null) {
				try {
					releaseManager.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}

		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#deleteService(java.lang.String, java.lang.String)
	 */
//	@Override
//	public synchronized Result deleteServiceInstance(String txId, String namespace, String serviceName) throws Exception {
//
//		// 서비스 요청 정보 기록
//		RequestEvent event = new RequestEvent();
//
//		event.setTxId(txId);
//		event.setServiceName(serviceName);
//		event.setServiceType(ZDBType.MariaDB.getName());
//		event.setNamespace(namespace);
//		event.setEventType(EventType.Delete.name());
//		event.setStartTime(new Date(System.currentTimeMillis()));
//		event.setOpertaion(KubernetesOperations.DELETE_SERVICE_INSTANCE);
//
//		ReleaseManager releaseManager = null;
//		try {
//			DefaultKubernetesClient client = (DefaultKubernetesClient) K8SUtil.kubernetesClient()
//					.inNamespace(namespace);
//
//			final Tiller tiller = new Tiller(client);
//			releaseManager = new ReleaseManager(tiller);
//
//			final ListReleasesRequest.Builder requestBuilder = ListReleasesRequest.newBuilder();
//			Iterator<ListReleasesResponse> requestBuilderList = releaseManager.list(requestBuilder.build());
//
//			String chartName = null;
//			while (requestBuilderList.hasNext()) {
//				ListReleasesResponse ent = requestBuilderList.next();
//				List<Release> releaseList = ent.getReleasesList();
//
//				for (Release release : releaseList) {
//					if (namespace.equals(release.getNamespace()) && serviceName.equals(release.getName())) {
//						chartName = release.getChart().getMetadata().getName();
//						break;
//					}
//				}
//			}
//			ServiceOverview serviceOverview = K8SUtil.getServiceWithName(namespace, ZDBType.MariaDB.getName(), serviceName);
//
//			if (chartName == null || serviceOverview == null) {
//
//				String msg = "설치된 서비스가 존재하지 않습니다.";
//				event.setResultMessage(msg);
//				event.setStatusMessage("서비스 삭제 실패");
//				event.setStatus(IResult.ERROR);
//				event.setEndTIme(new Date(System.currentTimeMillis()));
//				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//
//				return new Result(txId, IResult.ERROR, msg);
//			}
//
//			final UninstallReleaseRequest.Builder uninstallRequestBuilder = UninstallReleaseRequest.newBuilder();
//
//			uninstallRequestBuilder.setName(serviceName); // set releaseName
//			uninstallRequestBuilder.setPurge(true); // --purge
//
//			Result result = new Result(txId, IResult.OK, "Delete Service instance. [" + serviceName + "]");
//			final Future<UninstallReleaseResponse> releaseFuture = releaseManager.uninstall(uninstallRequestBuilder.build());
//			
//			if (releaseFuture != null) {
//				final Release release = releaseFuture.get().getRelease();
//				result.putValue(IResult.DELETE, release);
//
//				ReleaseMetaData releaseMeta = new ReleaseMetaData();
//				releaseMeta.setAction("DELETE");
//				releaseMeta.setApp(release.getChart().getMetadata().getName());
//				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
//				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
//				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()));
//				releaseMeta.setName(serviceName);
//				releaseMeta.setNamespace(namespace);
//				releaseMeta.setReleaseName(serviceName);
//				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
//				releaseMeta.setDescription(release.getInfo().getDescription());
//				releaseMeta.setManifest(release.getManifest());
//
//				log.info(new Gson().toJson(releaseMeta));
//
//				ReleaseMetaData findByReleaseName = releaseRepository.findByReleaseName(serviceName);
//				if (findByReleaseName != null) {
//					findByReleaseName.setStatus(release.getInfo().getStatus().getCode().name());
//					findByReleaseName.setUpdateTime(new Date(System.currentTimeMillis()));
//					releaseRepository.save(findByReleaseName);
//				} else {
//					releaseRepository.save(releaseMeta);
//				}
//
////				{ // StatefulSet 삭제
////					List<StatefulSet> statefulSets = serviceOverview.getStatefulSets();
////
////					for (StatefulSet sfs : statefulSets) {
////						client.inNamespace(namespace).apps().statefulSets().withName(sfs.getMetadata().getName()).delete();
////					}
////				}
////				
////				{ // Pod 삭제
////					List<Pod> pods = serviceOverview.getPods();
////
////					for (Pod pod : pods) {
////						client.inNamespace(namespace).pods().withName(pod.getMetadata().getName()).delete();
////					}
////				}
////				
//				{ // pvc 삭제
//					List<PersistentVolumeClaim> persistentVolumeClaims = serviceOverview.getPersistentVolumeClaims();
//
//					for (PersistentVolumeClaim pvc : persistentVolumeClaims) {
//						client.inNamespace(namespace).persistentVolumeClaims().withName(pvc.getMetadata().getName()).delete();
//					}
//				}
////
////				{
////					// Expose Service 삭제.
////					List<io.fabric8.kubernetes.api.model.Service> serviceList = serviceOverview.getServices();
////					for (io.fabric8.kubernetes.api.model.Service svc : serviceList) {
////						boolean deleteService = K8SUtil.doDeleteService(namespace, svc.getMetadata().getName());
////						log.info("{} delete. {}", svc.getMetadata().getName(), deleteService);
////					}
////				}
//				{ // account 삭제
//					MariaDBAccount.deleteAccounts(zdbMariaDBAccountRepository, namespace, serviceName);
//				}
//
//				{ // config 삭제
//					MariaDBConfiguration.deleteConfig(zdbMariaDBConfigRepository, namespace, serviceName);
//				}
//			} else {
//				return new Result(txId, IResult.ERROR, "Service 삭제 오류!");
//			}
//
//			return result;
//		} catch (FileNotFoundException | KubernetesClientException e) {
//			log.error(e.getMessage(), e);
//
//			event.setStatus(IResult.ERROR);
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//
//			if (e.getMessage().indexOf("Unauthorized") > -1) {
//				event.setResultMessage("Unauthorized");
//				
//				log.info("!!!"+new Gson().toJson(event));
//				
//				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
//			} else {
//				event.setResultMessage(e.getMessage());
//				
//				log.info("!!!"+new Gson().toJson(event));
//				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
//			}
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//
//			event.setResultMessage("Service [" + serviceName + "] delete error.\n" + e.getMessage());
//			event.setStatus(IResult.ERROR);
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//
//			log.info("!!!"+new Gson().toJson(event));
//			
//			return new Result(txId, IResult.ERROR, "Service [" + serviceName + "] delete error.", e);
//		} finally {
//			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//			
//			if(releaseManager != null){
//				releaseManager.close();
//			}
//		}
//
//	}

	/**
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @param client
	 */
//	private void deletePersistentVolumeClaim(Exchange exchange) {
//		
//		String txId = exchange.getProperty(Exchange.TXID, String.class);
//		String namespace = exchange.getProperty(Exchange.NAMESPACE, String.class);
//		String serviceName = exchange.getProperty(Exchange.SERVICE_NAME, String.class);
//		
//		exchange.getProperty(Exchange.SERVICE_NAME, String.class);
//		
//		ServiceOverview serviceOverview = exchange.getProperty(Exchange.SERVICE_OVERVIEW, ServiceOverview.class);
//		
//		
//		List<Watch> watchList = new ArrayList<>();
//		
//		RequestEvent event = new RequestEvent();
//		try {
//			event.setTxId(txId);
//			event.setServiceName(serviceName);
//			event.setServiceType(ZDBType.MariaDB.getName());
//			event.setNamespace(namespace);
//			event.setEventType(EventType.Delete.name());
//			event.setOpertaion(KubernetesOperations.DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION);
//			
////			CountDownLatch closeLatch = new CountDownLatch(persistentVolumeClaims.size());
////			
////			// TODO service name 으로 PVC 조회후 삭제...
////			
////			// metaRepository, closeLatch, KubernetesOperations.DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION, txId, namespace, serviceName, pvcName
////			ZDBEventWatcher<PersistentVolumeClaim> eventWatcher = new ZDBEventWatcher<PersistentVolumeClaim>(
////					KubernetesOperations.DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION,
////					closeLatch,
////					exchange
////					);
//
//			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
//			
//			List<PersistentVolumeClaim> persistentVolumeClaims = serviceOverview.getPersistentVolumeClaims();
//			
//			for(PersistentVolumeClaim pvc : persistentVolumeClaims) {
//				event.setStatus(IResult.RUNNING);
//				event.setStatusMessage("PVC 삭제 요청");
//				event.setResultMessage("PVC 삭제 요청 [" +namespace +" > "+pvc.getMetadata().getName()+ "]");
//				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//				
//				client.inNamespace(namespace).persistentVolumeClaims().withName(pvc.getMetadata().getName()).delete();
//			}
//			
//			
////			closeLatch.await(30, TimeUnit.SECONDS);
//			
//		} catch (Exception e) {
//			event.setStatus(IResult.ERROR);
//			
//			log.error(e.getMessage(), e);
//			
//			event.setResultMessage("PVC 삭제 요청중 오류. [" +namespace +" > "+ serviceName +"'s pvc > "+e.getMessage()+ "]");
//			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//		} finally {
////			for(Watch w : watchList) {
////				w.close();
////			}
//			
//			event.setResultMessage("PVC 삭제 완료. [" +namespace +" > "+serviceName+ "'s pvc]");
//			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//		}
//	}

//	/*
//	 * (non-Javadoc)
//	 * 
//	 * @see com.zdb.core.service.ZDBRestService#createPersistentVolumeClaimsService(java.lang.String, com.zdb.core.domain.ZDBEntity)
//	 */
//	@Override
//	public Result createPersistentVolumeClaim(String txId, String namespace, String serviceName, PersistenceSpec persistenceSpec) throws Exception {
//		return super.createPersistentVolumeClaim(txId, namespace, serviceName, persistenceSpec);
//	}

	/**
	 * 
	 */
	public Result getDBVariables(final String txId, final String namespace, final String releaseName) {
		Result result = Result.RESULT_OK(txId);

		Iterable<Mycnf> findAll = configRepository.findAll();
		
		if(findAll == null || !findAll.iterator().hasNext()) {
			InitData.initData(configRepository);
			findAll = configRepository.findAll();
		}
		
		Map<String, Mycnf> mycnfMap = new TreeMap<>();
		for (Mycnf mycnf : findAll) {
			mycnfMap.put(mycnf.getName(), mycnf);
		}
		
		Map<String, String> systemConfigMap = new HashMap<>();
		
		try {
			List<ConfigMap> configMaps = K8SUtil.getConfigMaps(namespace, releaseName);
			if(configMaps != null && !configMaps.isEmpty()) {
				ConfigMap map = configMaps.get(0);
				Map<String, String> data = map.getData();
				String cnf = data.get("my.cnf");
				String[] split = cnf.split("\n");
				
				for (String line : split) {
					String[] params = line.split("=");
					if(params.length == 2) {
						String key = params[0].trim();
						String value = params[1].trim();
						if(key.startsWith("#")) {
							continue;
						}
						systemConfigMap.put(key, value);
					}
				}
				
				for (Iterator<String> iterator = mycnfMap.keySet().iterator(); iterator.hasNext();) {
					String key = iterator.next();
					Mycnf mycnf = mycnfMap.get(key);
					
					String value = systemConfigMap.get(key);
					if (value != null && !value.isEmpty()) {
						if(mycnf != null) {
							mycnf.setValue(value);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}
		
		result = new Result(txId, IResult.OK, "");
		result.putValue(IResult.MARIADB_CONFIG, mycnfMap.values());
		
//		try {
//			
//			ServiceOverview serviceWithName = getServiceWithName(namespace, ZDBType.MariaDB.getName(), releaseName);
//			ZDBStatus status = serviceWithName.getStatus();
//			
//			if(status == ZDBStatus.RED) {
//				ZDBMariaDBConfig findByReleaseName = zdbMariaDBConfigRepository.findByReleaseName(releaseName);
//				if (findByReleaseName != null) {
//					Map<String, String> configMap = new HashMap<>();
//					configMap.put("eventScheduler", findByReleaseName.getEventScheduler() == null ? MariaDBConfiguration.DEFAULT_EVENT_SCHEDULER : findByReleaseName.getEventScheduler());
//					configMap.put("groupConcatMaxLen", findByReleaseName.getGroupConcatMaxLen() == null ? MariaDBConfiguration.DEFAULT_GROUP_CONCAT_MAX_LEN : findByReleaseName.getGroupConcatMaxLen());
//					configMap.put("maxConnections", findByReleaseName.getMaxConnections() == null ? MariaDBConfiguration.DEFAULT_MAX_CONNECTIONS : findByReleaseName.getMaxConnections());
//					configMap.put("waitTimeout", findByReleaseName.getWaitTimeout() == null ? MariaDBConfiguration.DEFAULT_WAIT_TIMEOUT : findByReleaseName.getWaitTimeout());
//					configMap.put("releaseName", releaseName);
//
//					result = new Result(txId, IResult.OK, "");
//					result.putValue(IResult.MARIADB_CONFIG, configMap);
//				} else {
//					return new Result(txId, IResult.ERROR, "DB 실행중이지 않습니다. 상태를 확인하세요.");
//				}
//			} else {
//
//				String clusterIPAndPort = K8SUtil.getClusterIpAndPort(namespace, releaseName);
//				log.debug("serviceName: {}, ClusterIpAndPort: {}", releaseName, clusterIPAndPort);
//
//				connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
//				if (connection == null) {
//					throw new Exception("cannot connect mariadb. namespace: " + namespace + ", serviceName: " + releaseName);
//				}
//
//				String maxConnections = MariaDBConfiguration.getConfig(zdbMariaDBConfigRepository, connection.getStatement(), namespace, releaseName, "max_connections");
//				String eventScheduler = MariaDBConfiguration.getConfig(zdbMariaDBConfigRepository, connection.getStatement(), namespace, releaseName, "event_scheduler");
//				String groupConcatMaxLen = MariaDBConfiguration.getConfig(zdbMariaDBConfigRepository, connection.getStatement(), namespace, releaseName, "group_concat_max_len");
//				String waitTimeout = MariaDBConfiguration.getConfig(zdbMariaDBConfigRepository, connection.getStatement(), namespace, releaseName, "wait_timeout");
//
//				Map<String, String> configMap = new HashMap<>();
//				configMap.put("eventScheduler", eventScheduler == null ? MariaDBConfiguration.DEFAULT_EVENT_SCHEDULER : eventScheduler);
//				configMap.put("groupConcatMaxLen", groupConcatMaxLen == null ? MariaDBConfiguration.DEFAULT_GROUP_CONCAT_MAX_LEN : groupConcatMaxLen);
//				configMap.put("maxConnections", maxConnections == null ? MariaDBConfiguration.DEFAULT_MAX_CONNECTIONS : maxConnections);
//				configMap.put("waitTimeout", waitTimeout == null ? MariaDBConfiguration.DEFAULT_WAIT_TIMEOUT : waitTimeout);
//				configMap.put("releaseName", releaseName);
//
//				// config 현행
//				MariaDBConfiguration.setConfig(zdbMariaDBConfigRepository, connection.getStatement(), namespace, releaseName, configMap);
//
//				result = new Result(txId, IResult.OK, "");
//				result.putValue(IResult.MARIADB_CONFIG, configMap);
//			}
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//
//			return Result.RESULT_FAIL(txId, e);
//		} finally {
//			if (connection != null) {
//				connection.close();
//			}
//		}

		return result;
	}

	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPersistentVolumeClaims(java.lang.String)
	 */
	@Override
	public Result getPersistentVolumeClaims(final String namespace) throws Exception {
		try {
			List<PersistentVolumeClaim> pvcs = K8SUtil.getPersistentVolumeClaims(namespace);
			
			if (pvcs != null) {
				return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, pvcs);
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, "");
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPersistentVolumeClaim(java.lang.String, java.lang.String)
	 */
	@Override
	public Result getPersistentVolumeClaim(final String namespace, final String pvcName) throws Exception {
		try {
			PersistentVolumeClaim pvc = K8SUtil.getPersistentVolumeClaim(namespace, pvcName);
			
			if (pvc != null) {
				return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIM, pvc);
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.ERROR);
	}

//	public Result updateDBVariables(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
//		Result result = Result.RESULT_OK(txId);
//		MariaDBConnection connection = null;
//
//		// 서비스 요청 정보 기록
//		RequestEvent event = new RequestEvent();
//		event.setTxId(txId);
//		event.setServiceName(serviceName);
//		event.setEventType(EventType.UpdateDBConfig.name());
//		event.setNamespace(namespace);
//		event.setStartTime(new Date(System.currentTimeMillis()));
//		event.setStatusMessage("DB Configuration 갱신.");
//		
//		try {
//			event.setServiceType(K8SUtil.getChartName(namespace, serviceName));
//			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//			
//			ServiceOverview serviceWithName = getServiceWithName(namespace, ZDBType.MariaDB.getName(), serviceName);
//			ZDBStatus status = serviceWithName.getStatus();
//			
//			if(status == ZDBStatus.RED) {
//				return new Result(txId, IResult.ERROR, "DB 실행중이지 않습니다. 상태를 확인하세요.");
//			}
//
//			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
//			if (connection == null) {
//				throw new Exception("cannot connect mariadb. namespace: " + namespace + ", serviceName: " + serviceName);
//			}
//			
//			
////			MariaDBConfiguration.setConfig(zdbMariaDBConfigRepository, connection.getStatement(), namespace, serviceName, config);
//
//			result = new Result(txId, IResult.OK, "");
//			result.putValue(IResult.MARIADB_CONFIG, config);
//		} catch (FileNotFoundException | KubernetesClientException e) {
//			log.error(e.getMessage(), e);
//
//			event.setStatus(IResult.ERROR);
//			event.setStatusMessage("DBConfiguration 적용 오류");
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//
//			if (e.getMessage().indexOf("Unauthorized") > -1) {
//				event.setResultMessage("Unauthorized");
//				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
//			} else {
//				event.setResultMessage(e.getMessage());
//				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
//			}
//		} catch (SQLException e) {
//			log.error(e.getMessage(), e);
//
//			event.setResultMessage(e.getMessage());
//			event.setStatusMessage("DBConfiguration 적용 오류");
//			event.setStatus(IResult.ERROR);
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//
//			return Result.RESULT_FAIL(txId, e);
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//
//			event.setResultMessage(e.getMessage());
//			event.setStatusMessage("DBConfiguration 적용 오류");
//			event.setStatus(IResult.ERROR);
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//
//			return Result.RESULT_FAIL(txId, e);
//		} finally {
//			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//
//			if (connection!= null) {
//				connection.close();
//			}
//		}
//
//		return result;
//	}

	public Result getDBInstanceAccounts(String txId, String namespace, String serviceName) throws Exception {
		Result result = Result.RESULT_OK(txId);
		
		try {
			List<ZDBMariaDBAccount> accounts = MariaDBAccount.getAccounts(zdbMariaDBAccountRepository, namespace, serviceName);
			if (accounts == null || accounts.isEmpty()) {
				log.warn("no account. namespace: {}, serviceName: {}", namespace, serviceName);
				result.putValue(IResult.ACCOUNTS, Collections.emptyList());
			} else {
				result.putValue(IResult.ACCOUNTS, accounts);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}

	public Result createDBUser(final String txId, final String namespace, final String serviceName, final ZDBMariaDBAccount account) throws Exception {
		Result result = Result.RESULT_OK(txId);
		
		try {
			// TODO:
			if (null == MariaDBAccount.createAccount(zdbMariaDBAccountRepository, namespace, serviceName, account)) {
				log.error("FAIL: creating new account. namespace: {}, serviceName: {}, accountId: {}", namespace, serviceName, account.getUserId());
				Exception e = new Exception("creating new account failed. accountId: " + account.getUserId());
				result = Result.RESULT_FAIL(txId, e);
				throw e;
			}
			result.putValue(IResult.ACCOUNT, account);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}
	
	public Result updateDBUser(String txId, String namespace, String serviceName, ZDBMariaDBAccount account)
			throws Exception {
		Result result = Result.RESULT_OK(txId);

		try {
			ZDBMariaDBAccount accountBefore = MariaDBAccount.getAccount(zdbMariaDBAccountRepository, namespace, serviceName, account.getUserId());
			if (accountBefore == null) {
				return Result.RESULT_FAIL(txId, new Exception("no user. userId: " + account.getUserId()));
			}

			if (null == MariaDBAccount.updateAccount(zdbMariaDBAccountRepository, namespace, serviceName, accountBefore, account)) {
				log.error("FAIL: cannot update an account. namespace: {}, serviceName: {}, accountId: {}", namespace, serviceName, account.getUserId());
				return Result.RESULT_FAIL(txId, new Exception("cannot update an account. userId: " + account.getUserId()));
			}
			
			result.putValue(IResult.ACCOUNT, account);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}
	
	public Result updateAdminPassword(String txId, String namespace, String serviceName, String newPwd)
			throws Exception {
		Result result = Result.RESULT_OK(txId);

		try {
			String userId = "admin";

			if (null == MariaDBAccount.updateAdminPassword(zdbMariaDBAccountRepository, namespace, serviceName, newPwd)) {
				log.error("FAIL: cannot update an account. namespace: {}, serviceName: {}, accountId: {}", namespace, serviceName, userId);
				return Result.RESULT_FAIL(txId, new Exception("cannot update an account. userId: " + userId));
			}
			
			//result.putValue(IResult.ACCOUNT, "");
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}
	
	public Result deleteDBInstanceAccount(String txId, String namespace, String serviceName, String accountId)
			throws Exception {
		Result result = Result.RESULT_OK(txId);
		
		try {
			MariaDBAccount.deleteAccount(zdbMariaDBAccountRepository, namespace, serviceName, accountId);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}
		return result;
	}
	
	@Override
	public Result deletePersistentVolumeClaimsService(String txid, String namespace, String serviceName, String pvcName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public Result getConnectionInfo(String namespace, String serviceType, String serviceName) throws Exception {
		ConnectionInfo info = new ConnectionInfo("mariadb");

		List<Secret> secrets = K8SUtil.getSecrets(namespace, serviceName);
		if( secrets == null || secrets.isEmpty()) {
			return new Result("", Result.ERROR).putValue(IResult.CONNECTION_INFO, info);
		}
		
		for(Secret secret : secrets) {
			Map<String, String> secretData = secret.getData();
			String credetial = secretData.get("mariadb-password");
			
			info.setCredetial(credetial);
		}
		
		List<Connection> connectionList = new ArrayList<>();
		
		info.setConnectionList(connectionList);
		
		List<io.fabric8.kubernetes.api.model.Service> services = K8SUtil.getServices(namespace, serviceName);
		for (io.fabric8.kubernetes.api.model.Service service : services) {
			Connection connection = new Connection();
			
			try {
				String role = service.getMetadata().getLabels().get("component");
				if(!("master".equals(role)||"slave".equals(role)
				)){
					continue;
				}				
				
				connection.setRole(role);
				connection.setServiceName(service.getMetadata().getName());
				connection.setConnectionType(service.getMetadata().getAnnotations().get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type"));
				
				String ip = new String();
				
				if ("LoadBalancer".equals(service.getSpec().getType())) {
					ip = service.getStatus().getLoadBalancer().getIngress().get(0).getIp();
				} else if ("ClusterIP".equals(service.getSpec().getType())) {
					ip = service.getSpec().getClusterIP();
				}
				
				connection.setIpAddress(ip);

				ReleaseMetaData findByReleaseName = releaseRepository.findByReleaseName(serviceName);
				if( findByReleaseName != null) {
					info.setDbName(findByReleaseName.getDbname());
				} 
				
				if(info.getDbName() == null || info.getDbName().length() == 0) {
					String mariaDBDatabase = MariaDBAccount.getMariaDBDatabase(namespace, serviceName);
					info.setDbName(mariaDBDatabase);
				}				
				
				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort serivicePort : ports) {
					if (!"mysql".equals(serivicePort.getName())) {
						break;
					} else {
						connection.setPort(serivicePort.getPort().intValue());
						info.setPort(serivicePort.getPort().intValue());
						break;
					}
				}
				
				String connString = info.getConnectionString(connection);
				String connLine = info.getConnectionLine(connection);
				
				connection.setConnectionString(connString);
				connection.setConnectionLine(connLine);
				
				connectionList.add(connection);
			} catch (Exception e) {
				String clusterIP = service.getSpec().getClusterIP();
				connection.setIpAddress(clusterIP);
				if(connection.getConnectionType() == null) {
					connection.setConnectionType("private");
				}

				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort serivicePort : ports) {
					if ("mysql".equals(serivicePort.getName())) {
						connection.setPort(serivicePort.getPort().intValue());
						info.setPort(serivicePort.getPort().intValue());
						break;
					}
				}
				connectionList.add(connection);
			}
		}			
		
		return new Result("", Result.OK).putValue(IResult.CONNECTION_INFO, info);

	}

	@Override
	public Result getServiceCheckAlive(String namespace, String serviceType, String serviceName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @param config
	 * @return
	 * @throws Exception
	 */
	public Result updateConfig(String txId, String namespace, String serviceName, Map<String, String> config) throws Exception {
		Result result = null;

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			Iterable<Mycnf> findAll = configRepository.findAll();

			if (findAll == null || !findAll.iterator().hasNext()) {
				InitData.initData(configRepository);
				findAll = configRepository.findAll();
			}

			InputStream is = new ClassPathResource("mariadb/mycnf.template").getInputStream();
			String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());

			for (Mycnf mycnf : findAll) {
				inputJson = inputJson.replace("${" + mycnf.getName() + "}", config.get(mycnf.getName()) == null ? mycnf.getValue() : config.get(mycnf.getName()));
			}

			List<ConfigMap> items = client.inNamespace(namespace).configMaps().withLabel("release", serviceName).list().getItems();
			for (ConfigMap configMap : items) {
				String configMapName = configMap.getMetadata().getName();

				client.configMaps().inNamespace(namespace).withName(configMapName).edit().addToData("my.cnf", inputJson).done();
			}

			// shutdown and pod delete (restart)
			MariaDBShutDownUtil.getInstance().doShutdownAndDeleteAllPods(namespace, serviceName);

			result = new Result(txId, IResult.OK, "config update request. [" + serviceName + "]");

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "config update fail. [" + serviceName + "]");
		}

		return result;
	}

	@Override
	public Result updateScaleOut(String txId, ZDBEntity zdbEntity) throws Exception {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.AbstractServiceImpl#reStartPod(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	public Result reStartPod(String txId, String namespace, String serviceName, String podName) throws Exception {
		Result result = null;

		try {
			// shutdown and pod delete (restart)
			MariaDBShutDownUtil.getInstance().doShutdownAndDeletePod(namespace, serviceName, podName);
			result = new Result(txId, IResult.OK, "config update request. [" + serviceName + "]");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "config update fail. [" + serviceName + "]");
		}

		return result;
	}

}
