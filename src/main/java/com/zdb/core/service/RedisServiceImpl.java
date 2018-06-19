package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.Gson;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.zdb.core.domain.Connection;
import com.zdb.core.domain.ConnectionInfo;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesOperations;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBRedisConfig;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.ZDBRedisConfigRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.redis.RedisConfiguration;
import com.zdb.redis.RedisConnection;
import com.zdb.redis.RedisSecret;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.ListReleasesRequest;
import hapi.services.tiller.Tiller.ListReleasesResponse;
import hapi.services.tiller.Tiller.UninstallReleaseRequest;
import hapi.services.tiller.Tiller.UninstallReleaseResponse;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * ZDBRestService Implementation
 * 
 * @author 06919
 *
 */
@Service("redisService")
@Slf4j
@Configuration
public class RedisServiceImpl extends AbstractServiceImpl {

	@Value("${chart.redis.url}")
	public void setChartUrl(String url) {
		chartUrl = url;
	}

	@Autowired
	private ZDBReleaseRepository releaseRepository;

	@Autowired
	private ZDBRedisConfigRepository zdbRedisConfigRepository;

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
				Deployment deployment = client.inNamespace(namespace).extensions().deployments()
						.withName(deploymentName).get();

//				Deployment deployment = client.inNamespace(namespace).extensions().deployments().withName(serviceName).get();

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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#createService(com.zdb.core.domain.
	 * ZDBEntity)
	 */
	@Override
	public synchronized Result createDeployment(String txId, final ZDBEntity service) throws Exception {
		return super.createDeployment(txId, service);
	}

	@Override
	public Result updateScale(String txId, final ZDBEntity service) throws Exception {

		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();

		event.setTxId(txId);
		event.setServiceName(service.getServiceName());
		event.setServiceType(service.getServiceType());
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

				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

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

			Map<String, Object> master = new HashMap<String, Object>();
			Map<String, Object> slave = new HashMap<String, Object>();
			Map<String, Object> masterResource = new HashMap<String, Object>();
			Map<String, Object> masterResourceRequests = new HashMap<String, Object>();
			Map<String, Object> masterResourceLimits = new HashMap<String, Object>();
			Map<String, Object> slaveResource = new HashMap<String, Object>();
			Map<String, Object> slaveResourceRequests = new HashMap<String, Object>();
			Map<String, Object> slaveResourceLimits = new HashMap<String, Object>();

			Map<String, Object> values = new HashMap<String, Object>();

			if (service.getPodSpec() != null) {
				PodSpec[] podSpec = service.getPodSpec();

				for (PodSpec pod : podSpec) {
					if (pod.getPodType().equals("master")) {
						for (ResourceSpec resource : pod.getResourceSpec()) {
							if (resource.getResourceType().equals("requests")) {
								masterResourceRequests.put("cpu", resource.getCpu() +"m");
								masterResourceRequests.put("memory", resource.getMemory() + "Mi");

								masterResource.put("requests", masterResourceRequests);
							} else if (resource.getResourceType().equals("limits")) {
								masterResourceLimits.put("cpu", resource.getCpu() +"m");
								masterResourceLimits.put("memory", resource.getMemory() + "Mi");

								masterResource.put("limits", masterResourceLimits);
							}
						}
					} else if (pod.getPodType().equals("slave")) {
						for (ResourceSpec resource : pod.getResourceSpec()) {
							if (resource.getResourceType().equals("requests")) {
								slaveResourceRequests.put("cpu", resource.getCpu() +"m");
								slaveResourceRequests.put("memory", resource.getMemory() + "Mi");

								slaveResource.put("requests", slaveResourceRequests);
							} else if (resource.getResourceType().equals("limits")) {
								slaveResourceLimits.put("cpu", resource.getCpu() +"m");
								slaveResourceLimits.put("memory", resource.getMemory() + "Mi");

								slaveResource.put("limits", slaveResourceLimits);
							}
						}
					}
				}

				master.put("resources", masterResource);
				slave.put("resources", slaveResource);

				values.put("master", master);
				values.put("slave", slave);
			}

//			int slaveCount = service.getClusterSlaveCount();
//
//			Map<String, Object> clusterInfo = new HashMap<String, Object>();
//			clusterInfo.put("slaveCount", slaveCount);
//			values.put("cluster", clusterInfo);

			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			options.setPrettyFlow(true);

			Yaml yaml = new Yaml(options);
			String valueYaml = yaml.dump(values);

			log.info("****** YAML Values : " + valueYaml);

			valuesBuilder.setRaw(valueYaml);

			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

			log.info(service.getServiceName() + " update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();

			if (release != null) {
				ReleaseMetaData releaseMeta = new ReleaseMetaData();
				releaseMeta.setAction("UPDATE");
				releaseMeta.setApp(release.getChart().getMetadata().getName());
				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()));
				releaseMeta.setNamespace(service.getNamespace());
				releaseMeta.setReleaseName(service.getServiceName());
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				//releaseMeta.setDescription(release.getInfo().getDescription());
				// releaseMeta.setInputValues(valuesBuilder.getRaw());
				releaseMeta.setInputValues(valueYaml);
				//releaseMeta.setNotes(release.getInfo().getStatus().getNotes());

				log.info(new Gson().toJson(releaseMeta));

				releaseRepository.save(releaseMeta);
			}

			log.info(service.getServiceName() + " update success!");
			result = new Result(txId, IResult.RUNNING, "Update request. [" + service.getServiceName() + "]")
					.putValue(IResult.UPDATE, release);

			event.setResultMessage(service.getServiceName() + " update success.");
			event.setStatus(IResult.RUNNING);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

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
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

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

	@Override
	public Result updateScaleOut(String txId, final ZDBEntity service) throws Exception {
		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();

		event.setTxId(txId);
		event.setServiceName(service.getServiceName());
		event.setServiceType(service.getServiceType());
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

				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

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

			Map<String, Object> values = new HashMap<String, Object>();

			int slaveCount = service.getClusterSlaveCount();

			Map<String, Object> clusterInfo = new HashMap<String, Object>();
			clusterInfo.put("slaveCount", slaveCount);
			values.put("cluster", clusterInfo);
			
			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			options.setPrettyFlow(true);

			Yaml yaml = new Yaml(options);
			String valueYaml = yaml.dump(values);

			log.info("****** YAML Values : " + valueYaml);

			valuesBuilder.setRaw(valueYaml);

			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

			log.info(service.getServiceName() + " update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();

			if (release != null) {
				ReleaseMetaData releaseMeta = new ReleaseMetaData();
				releaseMeta.setAction("UPDATE");
				releaseMeta.setApp(release.getChart().getMetadata().getName());
				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()));
				releaseMeta.setNamespace(service.getNamespace());
				releaseMeta.setReleaseName(service.getServiceName());
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				//releaseMeta.setDescription(release.getInfo().getDescription());
				// releaseMeta.setInputValues(valuesBuilder.getRaw());
				releaseMeta.setInputValues(valueYaml);
				//releaseMeta.setNotes(release.getInfo().getStatus().getNotes());

				log.info(new Gson().toJson(releaseMeta));

				releaseRepository.save(releaseMeta);
			}

			log.info(service.getServiceName() + " update success!");
			result = new Result(txId, IResult.RUNNING, "Update request. [" + service.getServiceName() + "]")
					.putValue(IResult.UPDATE, release);

			event.setResultMessage(service.getServiceName() + " update success.");
			event.setStatus(IResult.RUNNING);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

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
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

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
	 * @see com.zdb.core.service.ZDBRestService#deleteService(java.lang.String,
	 * java.lang.String)
	 */
//	@Override
//	public synchronized Result deleteServiceInstance(String txId, String namespace, String serviceName)
//			throws Exception {
//
//		// 서비스 요청 정보 기록
//		RequestEvent event = new RequestEvent();
//
//		event.setTxId(txId);
//		event.setServiceName(serviceName);
//		event.setServiceType(ZDBType.Redis.getName());
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
//			ServiceOverview serviceOverview = K8SUtil.getServiceWithName(namespace, ZDBType.Redis.getName(),
//					serviceName);
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
//			final Future<UninstallReleaseResponse> releaseFuture = releaseManager
//					.uninstall(uninstallRequestBuilder.build());
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
//				// { // StatefulSet 삭제
//				// List<StatefulSet> statefulSets = serviceOverview.getStatefulSets();
//				//
//				// for (StatefulSet sfs : statefulSets) {
//				// client.inNamespace(namespace).apps().statefulSets().withName(sfs.getMetadata().getName()).delete();
//				// }
//				// }
//				//
//				// { // Pod 삭제
//				// List<Pod> pods = serviceOverview.getPods();
//				//
//				// for (Pod pod : pods) {
//				// client.inNamespace(namespace).pods().withName(pod.getMetadata().getName()).delete();
//				// }
//				// }
//				//
//				{ // pvc 삭제
//					List<PersistentVolumeClaim> persistentVolumeClaims = serviceOverview.getPersistentVolumeClaims();
//
//					for (PersistentVolumeClaim pvc : persistentVolumeClaims) {
//						client.inNamespace(namespace).persistentVolumeClaims().withName(pvc.getMetadata().getName())
//								.delete();
//					}
//				}
//				//
//				// {
//				// // Expose Service 삭제.
//				// List<io.fabric8.kubernetes.api.model.Service> serviceList =
//				// serviceOverview.getServices();
//				// for (io.fabric8.kubernetes.api.model.Service svc : serviceList) {
//				// boolean deleteService = K8SUtil.doDeleteService(namespace,
//				// svc.getMetadata().getName());
//				// log.info("{} delete. {}", svc.getMetadata().getName(), deleteService);
//				// }
//				// }
//				// { // config 삭제
//				// MariaDBConfiguration.deleteConfig(zdbMariaDBConfigRepository, namespace,
//				// serviceName);
//				// }
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
//				log.info("!!!" + new Gson().toJson(event));
//
//				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
//			} else {
//				event.setResultMessage(e.getMessage());
//
//				log.info("!!!" + new Gson().toJson(event));
//				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
//			}
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//
//			event.setResultMessage("Service [" + serviceName + "] delete error.\n" + e.getMessage());
//			event.setStatus(IResult.ERROR);
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//
//			log.info("!!!" + new Gson().toJson(event));
//
//			return new Result(txId, IResult.ERROR, "Service [" + serviceName + "] delete error.", e);
//		} finally {
//			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//
//			if (releaseManager != null) {
//				releaseManager.close();
//			}
//		}
//
//	}


	@Override
	public Result deletePersistentVolumeClaimsService(String txId, String namespace, String serviceName, String pvcName)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result getPersistentVolumeClaims(String namespace) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result getPersistentVolumeClaim(String namespace, String pvcName) throws Exception {
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
		ConnectionInfo info = new ConnectionInfo("redis");

		List<Secret> secrets = K8SUtil.getSecrets(namespace, serviceName);
		if (secrets == null || secrets.isEmpty()) {
			return new Result("", Result.OK).putValue(IResult.CONNECTION_INFO, info);
		}

		for (Secret secret : secrets) {
			Map<String, String> secretData = secret.getData();
			String credetial = secretData.get("redis-password");

			info.setCredetial(credetial);
		}

		List<Connection> connectionList = new ArrayList<>();

		List<io.fabric8.kubernetes.api.model.Service> services = K8SUtil.getServices(namespace, serviceName);

		for (io.fabric8.kubernetes.api.model.Service service : services) {
			Connection connection = new Connection();

			try {
				String role = service.getSpec().getSelector().get("role");
				if (!("master".equals(role) || "slave".equals(role))) {
					continue;
				}

				connection.setRole(role);
				connection.setServiceName(service.getMetadata().getName());
				connection.setConnectionType(service.getMetadata().getAnnotations()
						.get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type"));

				String ip = new String();

				if ("ClusterIP".equals(service.getSpec().getType())) {
					ip = service.getSpec().getClusterIP();
				} else if ("LoadBalancer".equals(service.getSpec().getType())) {
					ip = service.getStatus().getLoadBalancer().getIngress().get(0).getIp();
				}

				connection.setIpAddress(ip);
				// info.setDomain(ip);

				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort serivicePort : ports) {
					connection.setPort(serivicePort.getPort().intValue());
					info.setPort(serivicePort.getPort().intValue());
					break;
				}

				String connString = info.getConnectionString(connection);
				String connLine = info.getConnectionLine(connection);

				connection.setConnectionString(connString);
				connection.setConnectionLine(connLine);

				connectionList.add(connection);
			} catch (Exception e) {
				String clusterIP = service.getSpec().getClusterIP();
				connection.setIpAddress(clusterIP);
				// info.setDomain(clusterIP);
				connection.setConnectionType("private");

				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort serivicePort : ports) {
					if ("redis".equals(serivicePort.getName())) {
						connection.setPort(serivicePort.getPort().intValue());
						info.setPort(serivicePort.getPort().intValue());
						break;
					}
				}
				connectionList.add(connection);
			}
		}

		info.setConnectionList(connectionList);

		// ReleaseMetaData findByReleaseName =
		// releaseRepository.findByReleaseName(serviceName);
		// if( findByReleaseName != null) {
		// info.setDbName(findByReleaseName.getDbname());
		// }
		//
		// if(info.getDbName() == null || info.getDbName().length() == 0) {
		// String mariaDBDatabase = MariaDBAccount.getMariaDBDatabase(namespace,
		// serviceName);
		// info.setDbName(mariaDBDatabase);
		// }

		return new Result("", Result.OK).putValue(IResult.CONNECTION_INFO, info);

	}

	@Override
	public Result getPodResources(String namespace, String serviceType, String serviceName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * 
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @return
	 * 
	 * @author chanhokim
	 */
	@Override
	public Result getDBVariables(String txId, String namespace, String serviceName) {
		Result result = Result.RESULT_OK(txId);
		Jedis redisConnection = null;

		try {
			redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "external"); 

			if (redisConnection == null) {
				throw new Exception("Cannot connect redis. Namespace: " + namespace + ", ServiceName: " + serviceName);
			}

			ZDBRedisConfig config = new ZDBRedisConfig();
			config.setTimeout(RedisConfiguration.getConfig(redisConnection, "timeout"));
			config.setTcpKeepalive(RedisConfiguration.getConfig(redisConnection, "tcp-keepalive"));
			config.setMaxmemoryPolicy(RedisConfiguration.getConfig(redisConnection, "maxmemory-policy"));
			config.setMaxmemorySamples(RedisConfiguration.getConfig(redisConnection, "maxmemory-samples"));
			config.setSlowlogLogSlowerThan(RedisConfiguration.getConfig(redisConnection, "slowlog-log-slower-than"));
			config.setSlowlogMaxLen(RedisConfiguration.getConfig(redisConnection, "slowlog-max-len"));
			config.setNotifyKeyspaceEvents(RedisConfiguration.getConfig(redisConnection, "notify-keyspace-events"));
			config.setHashMaxZiplistEntries(RedisConfiguration.getConfig(redisConnection, "hash-max-ziplist-entries"));
			config.setHashMaxZiplistValue(RedisConfiguration.getConfig(redisConnection, "hash-max-ziplist-value"));
			config.setListMaxZiplistSize(RedisConfiguration.getConfig(redisConnection, "list-max-ziplist-size"));
			config.setZsetMaxZiplistEntries(RedisConfiguration.getConfig(redisConnection, "zset-max-ziplist-entries"));
			config.setZsetMaxZiplistValue(RedisConfiguration.getConfig(redisConnection, "zset-max-ziplist-value"));

			result = new Result(txId, IResult.OK, "");
			result.putValue(IResult.REDIS_CONFIG, config);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		} finally {
			if (redisConnection != null) {
				redisConnection.close();
			}
		}
		return result;
	}

	/**
	 * 
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @return
	 * 
	 * @author chanhokim
	 */
	@Override
	public Result updateDBVariables(final String txId, final String namespace, final String serviceName,
			Map<String, String> config) throws Exception {
		Result result = Result.RESULT_OK(txId);
		Jedis redisConnection = null;

		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setEventType(EventType.UpdateDBConfig.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setStatusMessage("Update Redis Configuration");

		try {
			event.setServiceType(K8SUtil.getChartName(namespace, serviceName));
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

			redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "external");
			if (redisConnection == null) {
				throw new Exception("Cannot connect Redis. Namespace: " + namespace + ", Service Name: " + serviceName);
			}
			RedisConfiguration.setConfig(zdbRedisConfigRepository, redisConnection, namespace, serviceName, config); 

			result = updateConfig(txId, namespace, serviceName, config); 
			
			result.putValue(IResult.REDIS_CONFIG, config);
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			event.setStatus(IResult.ERROR);
			event.setStatusMessage("DBConfiguration 적용 오류");
			event.setEndTIme(new Date(System.currentTimeMillis()));

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				event.setResultMessage("Unauthorized");
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				event.setResultMessage(e.getMessage());
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (JedisException e) {
			log.error(e.getMessage(), e);

			event.setResultMessage(e.getMessage());
			event.setStatusMessage("DBConfiguration 적용 오류");
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			return Result.RESULT_FAIL(txId, e);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			event.setResultMessage(e.getMessage());
			event.setStatusMessage("DBConfiguration 적용 오류");
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			return Result.RESULT_FAIL(txId, e);
		} finally {
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			if (redisConnection != null) {
				redisConnection.close();
			}
		}
		return result;
	}

	@Override
	public Result getServiceCheckAlive(String namespace, String serviceType, String serviceName) throws Exception {
		Jedis redisConnection = null;
		
		try {
			redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "external");

			if ("PONG".equals(redisConnection.ping())) {
				return new Result("", Result.OK).putValue(IResult.SERVICE_STATUS, "healthy");
			}			
	    } catch (Exception e) {
	        log.error(e.getMessage(), e);
	        return new Result("", Result.WARNING).putValue(IResult.SERVICE_STATUS, "unhealthy");
	    } finally {
	        if (redisConnection != null) {
	          redisConnection.close();
	        }
	    }
		
		return new Result("", Result.WARNING).putValue(IResult.SERVICE_STATUS, "unhealthy");
	}
	
	/**
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @param config
	 * @return
	 * @throws Exception
	 */
	public Result updateConfig(String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();

		event.setTxId(txId);
		event.setNamespace(namespace);
		event.setServiceName(serviceName);
		event.setServiceType(ZDBType.Redis.getName());
		event.setEventType(EventType.Update.name());
		event.setOpertaion(KubernetesOperations.UPDATE_CONFIGMAP_OPERATION);
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
			if (!K8SUtil.isServiceExist(namespace, serviceName)) {
				String msg = "서비스가 존재하지 않습니다. [" + serviceName + "]";

				log.error(msg);

				event.setChartVersion(chartVersion);
				event.setResultMessage(msg);
				event.setStatus(IResult.ERROR);
				event.setStatusMessage("Update Config 오류");
				event.setEndTIme(new Date(System.currentTimeMillis()));

				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

				return new Result(txId, IResult.ERROR, msg);
			}
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

			final UpdateReleaseRequest.Builder requestBuilder = UpdateReleaseRequest.newBuilder();
			requestBuilder.setTimeout(300L);
			requestBuilder.setName(serviceName);
			requestBuilder.setWait(true);

			requestBuilder.setReuseValues(true);
			//requestBuilder.setRecreate(true);
		
			hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();

			StringBuffer redisConfig = new StringBuffer();
			redisConfig.append("bind 0.0.0.0").append("\n");
			redisConfig.append("logfile /opt/bitnami/redis/logs/redis-server.log").append("\n");
			redisConfig.append("pidfile /opt/bitnami/redis/tmp/redis.pid").append("\n");
			redisConfig.append("dir /opt/bitnami/redis/data").append("\n");
			redisConfig.append("rename-command FLUSHDB FDB").append("\n");
			redisConfig.append("rename-command FLUSHALL FALL").append("\n");			

			// TO-DO
			Jedis redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "external"); 
			
			if (redisConnection == null) {
				throw new Exception("Cannot connect redis. Namespace: " + namespace + ", ServiceName: " + serviceName);
			}			
			
			redisConfig.append("appendonly ").append(RedisConfiguration.getConfig(redisConnection, "appendonly")).append("\n");

			for (Map.Entry<String, String> entry : config.entrySet()) {
				if ("save".equals(entry.getKey())){
					if ("true".equals(entry.getValue())) {
						redisConfig.append("save 900 1 300 10 60 10000").append("\n");
					} else {
						redisConfig.append("save \"\"").append("\n");
					}
				} else if ("notify-keyspace-events".equals(entry.getKey())) {
					if (entry.getValue() == null || entry.getValue() == "") {
						redisConfig.append("redisConfig \"\"").append("\n");
					}
				
				} else {
					redisConfig.append(entry.getKey() + " " + entry.getValue()).append("\n");
				}
			}
			
			Map<String, Object> values = new HashMap<String, Object>();
			Map<String, Object> master = new HashMap<String, Object>(); 
	        master.put("config", redisConfig.toString());
			values.put("master", master);				

			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			options.setPrettyFlow(true);				
			
			Yaml yaml = new Yaml(options);
			String valueYaml = yaml.dump(values);

			valuesBuilder.setRaw(valueYaml);

			log.info("redis.cnf : \n" + valueYaml);
			log.info(serviceName + " update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();

			if (release != null) {
				log.info(release.getConfig().getRaw());
				log.info(serviceName + " config update success!");
				result = new Result(txId, IResult.RUNNING, "config update request. [" + serviceName + "]").putValue(IResult.CONFIG_UPDATE, release);
			} else {
				result = new Result(txId, IResult.ERROR, "config update fail. [" + serviceName + "]");
			}

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
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

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
}
  
