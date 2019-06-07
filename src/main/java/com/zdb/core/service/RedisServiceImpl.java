package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.zdb.core.collector.MetaDataCollector;
import com.zdb.core.domain.Connection;
import com.zdb.core.domain.ConnectionInfo;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBRedisConfig;
import com.zdb.core.job.EventAdapter;
import com.zdb.core.job.Job;
import com.zdb.core.job.Job.JobResult;
import com.zdb.core.job.JobExecutor;
import com.zdb.core.job.JobHandler;
import com.zdb.core.job.JobParameter;
import com.zdb.core.job.ServiceOnOffJob;
import com.zdb.core.repository.ZDBRedisConfigRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.ResourceChecker;
import com.zdb.core.util.PodManager;
import com.zdb.redis.RedisConfiguration;
import com.zdb.redis.RedisConnection;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * ZDBRestService Implementation
 * 
 * @author 06919
 *
 */
@org.springframework.stereotype.Service("redisService")
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
	
	@Autowired
	private SimpMessagingTemplate messageSender;
	
	@Autowired
	private MetaDataCollector metaDataCollector;

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
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
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
		Result result = new Result(txId);
		ReleaseManager releaseManager = null;
		String historyValue = "";
		try {
			final URI uri = URI.create(chartUrl);
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}

			ReleaseMetaData releaseName = releaseRepository.findByReleaseName(service.getServiceName());

			// 서비스 명 체크
			if (releaseName == null) {
				String msg = "서비스가 존재하지 않습니다. [" + service.getServiceName() + "]";
				return new Result(txId, IResult.ERROR, msg);
			}

			// 가용 리소스 체크
			// 현재보다 작으면ok
			// 현재보다 크면 커진 사이즈 만큼 가용량 체크 
			// zdb 노드 가용 리소스 체크 로직 추가 (2019-06-07)
			boolean availableResource = false;
			
			try {
				availableResource = isAvailableScaleUp(service);
			} catch (Exception e) {
				return new Result(txId, IResult.ERROR, e.getMessage());
			}
			
			if(!availableResource) {
				return new Result(txId, IResult.ERROR, "가용 리소스가 부족합니다.");
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

			PodSpec[] podSpec = service.getPodSpec();
			
			String master = podSpec[0].getPodType();
			ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
			String masterResourceType = masterSpec.getResourceType();
			String masterCpu = masterSpec.getCpu();
			String masterMemory = masterSpec.getMemory();
			
			String slave = master;//podSpec[1].getPodType();
			ResourceSpec slaveSpec = masterSpec;//podSpec[1].getResourceSpec()[0];
			String slaveCpu = masterCpu;//slaveSpec.getCpu();
			String slaveMemory = masterMemory;//slaveSpec.getMemory();
			
			InputStream is = new ClassPathResource("redis/update_values.template").getInputStream();
			
			String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());
			
			// 2018-10-04 추가
			// 환경설정 변경 이력 
			historyValue = compareResources(service.getNamespace(), service.getServiceName(), service);
			
			inputJson = inputJson.replace("${master.resources.requests.cpu}", masterCpu);// input , *******   필수값  
			inputJson = inputJson.replace("${master.resources.requests.memory}", masterMemory);// input *******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.cpu}", slaveCpu);// input*******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.memory}", slaveMemory);// input *******   필수값 
			inputJson = inputJson.replace("${master.resources.limits.cpu}", masterCpu);// input , *******   필수값  
			inputJson = inputJson.replace("${master.resources.limits.memory}", masterMemory);// input *******   필수값 
			inputJson = inputJson.replace("${slave.resources.limits.cpu}", slaveCpu);// input*******   필수값 
			inputJson = inputJson.replace("${slave.resources.limits.memory}", slaveMemory);// input *******   필수값 
			
			valuesBuilder.setRaw(inputJson);

			
//			Map<String, Object> master = new HashMap<String, Object>();
//			Map<String, Object> slave = new HashMap<String, Object>();
//			Map<String, Object> masterResource = new HashMap<String, Object>();
//			Map<String, Object> masterResourceRequests = new HashMap<String, Object>();
//			Map<String, Object> masterResourceLimits = new HashMap<String, Object>();
//			Map<String, Object> slaveResource = new HashMap<String, Object>();
//			Map<String, Object> slaveResourceRequests = new HashMap<String, Object>();
//			Map<String, Object> slaveResourceLimits = new HashMap<String, Object>();
//
//			Map<String, Object> values = new HashMap<String, Object>();
//
//			if (service.getPodSpec() != null) {
//				PodSpec[] podSpec = service.getPodSpec();
//
//				for (PodSpec pod : podSpec) {
//					if (pod.getPodType().equals("master")) {
//						for (ResourceSpec resource : pod.getResourceSpec()) {
//							if (resource.getResourceType().equals("requests")) {
//								masterResourceRequests.put("cpu", resource.getCpu() +"m");
//								masterResourceRequests.put("memory", resource.getMemory() + "Mi");
//
//								masterResource.put("requests", masterResourceRequests);
//							} else if (resource.getResourceType().equals("limits")) {
//								masterResourceLimits.put("cpu", resource.getCpu() +"m");
//								masterResourceLimits.put("memory", resource.getMemory() + "Mi");
//
//								masterResource.put("limits", masterResourceLimits);
//							}
//						}
//					} else if (pod.getPodType().equals("slave")) {
//						for (ResourceSpec resource : pod.getResourceSpec()) {
//							if (resource.getResourceType().equals("requests")) {
//								slaveResourceRequests.put("cpu", resource.getCpu() +"m");
//								slaveResourceRequests.put("memory", resource.getMemory() + "Mi");
//
//								slaveResource.put("requests", slaveResourceRequests);
//							} else if (resource.getResourceType().equals("limits")) {
//								slaveResourceLimits.put("cpu", resource.getCpu() +"m");
//								slaveResourceLimits.put("memory", resource.getMemory() + "Mi");
//
//								slaveResource.put("limits", slaveResourceLimits);
//							}
//						}
//					}
//				}
//
//				master.put("resources", masterResource);
//				slave.put("resources", slaveResource);
//
//				values.put("master", master);
//				values.put("slave", slave);
//				
//				Map<String, Object> serviceAccount = new HashMap<String, Object>();
//				
//				serviceAccount.put("create", false);
//				values.put("serviceAccount", serviceAccount);
//			}
//			
//			Secret secret  = K8SUtil.getSecret(service.getNamespace(), service.getServiceName());
//			String password = new String();
//			
//			if (secret != null) {
//				Map<String, String> data = secret.getData();
//
//				if (!data.isEmpty()) {
//					password = new String(Base64.getDecoder().decode(data.get("redis-password").getBytes()));
//				}
//			}			
//			
//			values.put("password", password );
//			
//			DumperOptions options = new DumperOptions();
//			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
//			options.setPrettyFlow(true);
//
//			Yaml yaml = new Yaml(options);
//			String valueYaml = yaml.dump(values);
// 
//			log.info("****** YAML Values : " + valueYaml);
 
//			valuesBuilder.setRaw(valueYaml);

			log.info(service.getServiceName() + " update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();

			if (release != null) {
//				ReleaseMetaData releaseMeta = releaseName;
//				if(releaseMeta == null) {
//					releaseMeta = new ReleaseMetaData();
//				}
//				releaseMeta.setAction("UPDATE");
//				releaseMeta.setApp(release.getChart().getMetadata().getName());
//				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
//				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
//				releaseMeta.setChartName(release.getChart().getMetadata().getName());
//				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()));
//				releaseMeta.setNamespace(service.getNamespace());
//				releaseMeta.setReleaseName(service.getServiceName());
//				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
//				releaseMeta.setDescription(release.getInfo().getDescription());
//			    releaseMeta.setInputValues(valuesBuilder.getRaw());
//			    releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
//			    releaseMeta.setManifest(release.getManifest());
////				releaseMeta.setInputValues(valueYaml);
//				releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));
//
//				log.info(new Gson().toJson(releaseMeta));
//
//				releaseRepository.save(releaseMeta);
				
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
			result = new Result(txId, IResult.OK, historyValue).putValue(IResult.UPDATE, release);
			if(!historyValue.isEmpty()) {
				result.putValue(Result.HISTORY, historyValue);
			}
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
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
		Result result = new Result(txId);

		try {
			// 서비스 명 체크
			
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(service.getServiceName());
			
			if (releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다. [" + service.getServiceName() + "]";
				return new Result(txId, IResult.ERROR, msg);
			}

			int slaveCount = service.getClusterSlaveCount();
			
			// 가용 리소스 체크
			ZDBEntity podResources = K8SUtil.getPodResources(service.getNamespace(), service.getServiceType(), service.getServiceName());
			PodSpec[] currentPodSpecs = podResources.getPodSpec();
			
			// scale out 의 경우에만 체크 
			if ((currentPodSpecs.length - 1) < slaveCount) {
				int reqCpu = 0;
				int reqMem = 0;

				if (currentPodSpecs != null && currentPodSpecs.length > 0) {
					ResourceSpec[] resourceSpec = currentPodSpecs[0].getResourceSpec();
					String currentCpu = resourceSpec[0].getCpu();
					String currentMemory = resourceSpec[0].getMemory();

					try {
						reqCpu = K8SUtil.convertToCpu(currentCpu);
						reqMem = K8SUtil.convertToMemory(currentMemory);

						boolean availableResource = ResourceChecker.isAvailableResource(service.getNamespace(),
								service.getRequestUserId(), reqCpu, reqMem);
						if (!availableResource) {
							result.setCode(IResult.ERROR);
							result.setMessage("가용 리소스가 부족합니다.");

							return result;
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
			}
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
 		
			List<Deployment> deployments = client.extensions().deployments().inNamespace(service.getNamespace()).withLabel("release", service.getServiceName()).list().getItems();
			
			String releaseName = new String();
			
			for (Deployment deployment : deployments) {
				releaseName = deployment.getMetadata().getName();
				break;
			}
			
			client.extensions().deployments().inNamespace(service.getNamespace()).withName(releaseName).scale(slaveCount);
			result.setCode(IResult.OK);
			result.setMessage("scale-out request.");
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}	
	
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

		List<Service> services = K8SUtil.getServices(namespace, serviceName);

		for (Service service : services) {
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
				if(connection.getConnectionType() == null) {
					connection.setConnectionType("private");
				}

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
	 * @return result
	 *
	 * Redis의 ConfigMap에서 Redis Configuration 값을 읽어옵니다.
	 */
	@Override
	public Result getDBVariables(String txId, String namespace, String serviceName) {
		Result result = Result.RESULT_OK(txId);
		Map<String, String> redisConfigMap = new HashMap<>();

		try {
			List<ConfigMap> configMaps = K8SUtil.getConfigMaps(namespace, serviceName);
			
			if(configMaps != null && !configMaps.isEmpty()) {
				ConfigMap configMap = configMaps.get(0);
				Map<String, String> data = configMap.getData();
				String redisConf = data.get("redis-config");
				String[] split = redisConf.split("\n");
				
				for (String line : split) {
					String[] params = line.trim().split(" ");
					if(params.length == 2) {
						String key = params[0].trim();
						String value = params[1].trim();
						redisConfigMap.put(key, value);
					}
				}

				ZDBRedisConfig zdbRedisConfig = new ZDBRedisConfig();
				zdbRedisConfig.setTimeout(redisConfigMap.get("timeout"));
				zdbRedisConfig.setTcpKeepalive(redisConfigMap.get("tcp-keepalive"));
				zdbRedisConfig.setMaxmemoryPolicy(redisConfigMap.get("maxmemory-policy"));
				zdbRedisConfig.setMaxmemorySamples(redisConfigMap.get("maxmemory-samples"));
				zdbRedisConfig.setSlowlogLogSlowerThan(redisConfigMap.get("slowlog-log-slower-than"));
				zdbRedisConfig.setSlowlogMaxLen(redisConfigMap.get("slowlog-max-len"));
				zdbRedisConfig.setHashMaxZiplistEntries(redisConfigMap.get("hash-max-ziplist-entries"));
				zdbRedisConfig.setHashMaxZiplistValue(redisConfigMap.get("hash-max-ziplist-value"));
				zdbRedisConfig.setListMaxZiplistSize(redisConfigMap.get("list-max-ziplist-size"));
				zdbRedisConfig.setZsetMaxZiplistEntries(redisConfigMap.get("zset-max-ziplist-entries"));
				zdbRedisConfig.setZsetMaxZiplistValue(redisConfigMap.get("zset-max-ziplist-value"));
//				if (redisConfigMap.get("notify-keyspace-events").equals("\"\"")) {
//					zdbRedisConfig.setNotifyKeyspaceEvents("");
//				} else {
//					zdbRedisConfig.setNotifyKeyspaceEvents(redisConfigMap.get("notify-keyspace-events"));
//				}
				if (redisConfigMap.containsKey("notify-keyspace-events")) {
					String notify_keyspace_events = redisConfigMap.get("notify-keyspace-events");
					if(notify_keyspace_events == null || notify_keyspace_events.trim().isEmpty()) {
						zdbRedisConfig.setNotifyKeyspaceEvents("\"\"");
					} else {
						zdbRedisConfig.setNotifyKeyspaceEvents(notify_keyspace_events.trim());
					}
				} else {
					zdbRedisConfig.setNotifyKeyspaceEvents("");
				}

				result = new Result(txId, IResult.OK, "");
				result.putValue(IResult.REDIS_CONFIG, zdbRedisConfig);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		}
		return result;
	}
	
	@Override
	public Result getAllDBVariables(String txId, String namespace, String serviceName) {
		Result result = Result.RESULT_OK(txId);
		Jedis redisConnection = null;
		
		try {
			redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "master"); 

			if (redisConnection == null) {
				throw new Exception("Cannot connect redis. Namespace: " + namespace + ", ServiceName: " + serviceName);
			}
			
			List<String> config = RedisConfiguration.getAllConfig(redisConnection);
			
			List<String> configList = new ArrayList<>();
			
			for (int i=0; i<config.size(); i+=2) {
				configList.add(config.get(i) + " = " + config.get(i+1));
			}

			result = new Result(txId, IResult.OK, "");
			result.putValue(IResult.REDIS_CONFIG, configList.toArray());
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
	public Result updateDBVariables(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
		Result result = Result.RESULT_OK(txId);
		Jedis redisConnection = null;

		try {
			result = updateConfig(txId, namespace, serviceName, config); 
			
			result.putValue(IResult.REDIS_CONFIG, config);
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (JedisException e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
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

	@Override
	public Result getServiceCheckAlive(String namespace, String serviceType, String serviceName) throws Exception {
		Jedis redisConnection = null;
		
		try {
			redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "master");

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
		Result result = new Result(txId);
		
		try {
			// 서비스 유무 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}

			// Redis에 접속하여 설정 변경
			Jedis redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "master"); 
			
			if (redisConnection == null) {
				throw new Exception("Cannot connect Redis(Master). Namespace: " + namespace + ", ServiceName: " + serviceName);
			}
			
			RedisConfiguration.setConfig(zdbRedisConfigRepository, redisConnection, namespace, serviceName, config); 
			
			if (releaseMetaData != null && releaseMetaData.getClusterEnabled()) {
				redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "slave");
				if (redisConnection == null) {
					throw new Exception("Cannot connect Redis(Slave). Namespace: " + namespace + ", Service Name: " + serviceName);
				}
				RedisConfiguration.setConfig(zdbRedisConfigRepository, redisConnection, namespace, serviceName, config);
			}
			
			// 변경된 값 확인
			String historyValue = compareVariables(namespace, serviceName, config);
			
			// ConfigMap 변경
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			List<ConfigMap> configMaps = K8SUtil.getConfigMaps(namespace, serviceName);
			Map<String, String> redisConfigMap = new HashMap<>();
			if(configMaps != null && !configMaps.isEmpty()) {
				ConfigMap configMap = configMaps.get(0);
				Map<String, String> data = configMap.getData();
				String redisConf = data.get("redis-config");
				String[] split = redisConf.split("\n");
				for (String line : split) {
					String[] params = line.trim().split(" ");
					if(params.length == 2) {
						String key = params[0].trim();
						String value = params[1].trim();
						redisConfigMap.put(key, value);
					}
				}
				redisConfigMap.put("timeout", config.get("timeout"));
				redisConfigMap.put("tcp-keepalive", config.get("tcp-keepalive"));
				redisConfigMap.put("maxmemory-policy", config.get("maxmemory-policy"));
				redisConfigMap.put("maxmemory-samples", config.get("maxmemory-samples"));
				redisConfigMap.put("slowlog-log-slower-than", config.get("slowlog-log-slower-than"));
				redisConfigMap.put("slowlog-max-len", config.get("slowlog-max-len"));
				redisConfigMap.put("hash-max-ziplist-entries", config.get("hash-max-ziplist-entries"));
				redisConfigMap.put("hash-max-ziplist-value", config.get("hash-max-ziplist-value"));
				redisConfigMap.put("list-max-ziplist-size", config.get("list-max-ziplist-size"));
				redisConfigMap.put("zset-max-ziplist-entries", config.get("zset-max-ziplist-entries"));
				redisConfigMap.put("zset-max-ziplist-value", config.get("zset-max-ziplist-value"));
				redisConfigMap.put("notify-keyspace-events", config.get("notify-keyspace-events") == null || config.get("notify-keyspace-events").trim().length() == 0 ? "\"\"" : config.get("notify-keyspace-events"));
//				if (config.get("notify-keyspace-events").equals("")) {
//					redisConfigMap.put("notify-keyspace-events", config.get("\"\""));
//				} else {
//					redisConfigMap.put("notify-keyspace-events", config.get("notify-keyspace-events"));
//				}
				
				InputStream is = new ClassPathResource("redis/config_values.template").getInputStream();
				String inputValue = IOUtils.toString(is, StandardCharsets.UTF_8.name());

				redisConfigMap.put("bind","0.0.0.0");
				redisConfigMap.put("logfile","/opt/bitnami/redis/logs/redis-server.log");
				redisConfigMap.put("pidfile","/opt/bitnami/redis/tmp/redis.pid");
				redisConfigMap.put("dir","/opt/bitnami/redis/data");
				redisConfigMap.put("maxmemory","192mb");
				
//				StringBuilder mapAsString = new StringBuilder("");
			    for (String key : redisConfigMap.keySet()) {
//			        mapAsString.append(key + " " + redisConfigMap.get(key) + "\n");
			    	inputValue = inputValue.replace("${" + key + "}", redisConfigMap.get(key));
			    }
//			    mapAsString.delete(mapAsString.length()-2, mapAsString.length()).append("");
//				String redisConfigMapAsString = mapAsString.toString();
				
				String configMapName = configMap.getMetadata().getName();
				client.configMaps().inNamespace(namespace).withName(configMapName).edit().addToData("redis-config", inputValue).done();
			}
			result = new Result(txId, IResult.OK, "환경설정 변경");	
			if (!historyValue.toString().isEmpty()) {
				result.putValue(Result.HISTORY, historyValue);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "환경설정 변경 요청 오류 - " + e.getLocalizedMessage());
		}
		
		return result;
	}

	@Override
	public Result getMycnf(String namespace, String releaseName) {
		// do nothing
		return null;
	}

	@Override
	public Result getUserGrants(String namespace, String serviceType, String releaseName) {
		return null;
	}	
	
	private String compareVariables(String namespace, String serviceName, Map<String, String> newConfig) {
		Map<String, String> currentConfigMap = new HashMap<>();
		
		StringBuffer sb = new StringBuffer();
		
		try {
			List<ConfigMap> configMaps = K8SUtil.getConfigMaps(namespace, serviceName);
			if(configMaps != null && !configMaps.isEmpty()) {
				ConfigMap map = configMaps.get(0);
				Map<String, String> data = map.getData();
				String cnf = data.get("redis-config");
				String[] split = cnf.split("\n");
				
				for (String line : split) {
					String[] params = line.trim().split(" ");
					if(params.length == 2) {
						String key = params[0].trim();
						String value = params[1].trim();
						currentConfigMap.put(key, value);
						
						String newValue = newConfig.get(key);
						if("notify-keyspace-events".equals(key)) {
							if(newValue == null || newValue.trim().isEmpty()) {
								newValue = "\"\"";
							}
						}
						if(value != null && newValue != null && !value.equals(newValue)) {
							sb.append(key).append(" : ").append(value).append(" → ").append(newValue).append("\n");
						}
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return sb.toString();
	}
	
	@Override
	public Result serviceOff(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception {

		Result result = new Result(txId);

		String historyValue = "";
		
		try {
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}

			log.info(stsName + " service shutdown.");
			
			List<Job> jobList = new ArrayList<>();
			JobParameter param = new JobParameter();
			param.setNamespace(namespace);
			param.setServiceType(serviceType);
			param.setServiceName(serviceName);
			param.setStatefulsetName(stsName);
			param.setToggle(0);
			param.setTxId(txId);

			ServiceOnOffJob job1 = new ServiceOnOffJob(param);
			
			jobList.add(job1);
			
			if (jobList.size() > 0) {
				CountDownLatch latch = new CountDownLatch(jobList.size());

				JobExecutor storageScaleExecutor = new JobExecutor(latch, txId);

				final String _historyValue = String.format("서비스 종료(%s)", stsName);

				EventAdapter eventListener = new EventAdapter(txId) {

					@Override
					public void onEvent(Job job, String event) {
						log.info(job.getJobName() + "  onEvent - " + event);
					}

					@Override
					public void status(Job job, String status) {
						//log.info(job.getJobName() + " : " + status);
					}

					@Override
					public void done(Job job, JobResult code, String message, Throwable e) {
						if (jobList.contains(job)) {
							RequestEvent event = new RequestEvent();

							event.setTxId(txId);
							event.setStartTime(new Date(System.currentTimeMillis()));
							event.setServiceType(serviceType);
							event.setNamespace(namespace);
							event.setServiceName(serviceName);
							event.setOperation(RequestEvent.SERVICE_OFF);
							event.setUserId("SYSTEM");
							try {
								if (code == JobResult.ERROR) {
									event.setStatus(IResult.ERROR);
									event.setResultMessage(job.getJobName() + " 처리 중 오류가 발생했습니다. " + e != null ? "["+e.getMessage()+"]" : "" + ")");
								} else {
									
									List<StatefulSet> statefulSets = K8SUtil.getStatefulSets(namespace, serviceName);
									metaDataCollector.save(statefulSets);
									
									event.setStatus(IResult.OK);
									if (message == null || message.isEmpty()) {
										event.setResultMessage(job.getJobName() + " 정상 처리되었습니다.");
									} else {
										event.setResultMessage(message);
									}
								}
								event.setHistory(_historyValue);
								event.setEndTime(new Date(System.currentTimeMillis()));
								ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
								JobHandler.getInstance().removeListener(this);
							
								ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
								
								Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
								messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
								
							} catch (MessagingException e1) {
								e1.printStackTrace();
							} catch (Exception e1) {
								e1.printStackTrace();
							}	
						}
					}

				};

				JobHandler.getInstance().addListener(eventListener);

				storageScaleExecutor.execTask(jobList.toArray(new Job[] {}));

				log.info(serviceName + " 서비스 셧다운 요청.");
				result = new Result(txId, IResult.RUNNING, stsName + "<br>서비스를 셧다운 합니다.");
			} else {
				result = new Result(txId, IResult.ERROR, "서비스 셧다운 실행 오류.");
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
		}
		
		return result;
	
	}
	
	@Override
	public Result serviceOn(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception {
		Result result = new Result(txId);

		String historyValue = "";
		
		try {
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}

			log.info(stsName + " service on.");
			
			List<Pod> pods = K8SUtil.getPods(namespace, serviceName);
			
			List<Job> jobList = new ArrayList<>();
			for (Pod p : pods) {
				
				if(p.getMetadata().getName().startsWith(stsName)) {
					
					boolean isReady = PodManager.isReady(p);
					
					if(isReady) {
						log.error("서비스가 실행 중입니다.");
						
						return new Result(txId, Result.ERROR, "서비스가 실행 중입니다.");
					}
					break;
				}
			}
				
			JobParameter param = new JobParameter();
			param.setNamespace(namespace);
			param.setServiceType(serviceType);
			param.setServiceName(serviceName);
			param.setStatefulsetName(stsName);
			param.setToggle(1);
			param.setTxId(txId);

			ServiceOnOffJob job1 = new ServiceOnOffJob(param);
			
			jobList.add(job1);
			
			if (jobList.size() > 0) {
				CountDownLatch latch = new CountDownLatch(jobList.size());

				JobExecutor storageScaleExecutor = new JobExecutor(latch, txId);

				final String _historyValue = String.format("서비스 시작(%s)", stsName);

				EventAdapter eventListener = new EventAdapter(txId) {

					@Override
					public void onEvent(Job job, String event) {
						log.info(job.getJobName() + "  onEvent - " + event);
					}

					@Override
					public void status(Job job, String status) {
						//log.info(job.getJobName() + " : " + status);
					}

					@Override
					public void done(Job job, JobResult code, String message, Throwable e) {
						if (jobList.contains(job)) {
							RequestEvent event = new RequestEvent();

							event.setTxId(txId);
							event.setStartTime(new Date(System.currentTimeMillis()));
							event.setServiceType(serviceType);
							event.setNamespace(namespace);
							event.setServiceName(serviceName);
							event.setOperation(RequestEvent.SERVICE_ON);
							event.setUserId("SYSTEM");
							try {
								if (code == JobResult.ERROR) {
									event.setStatus(IResult.ERROR);
									event.setResultMessage(job.getJobName() + " 처리 중 오류가 발생했습니다. (" + e.getMessage() + ")");
								} else {
									
									List<StatefulSet> statefulSets = K8SUtil.getStatefulSets(namespace, serviceName);
									metaDataCollector.save(statefulSets);
									
									event.setStatus(IResult.OK);
									if (message == null || message.isEmpty()) {
										event.setResultMessage(job.getJobName() + " 정상 처리되었습니다.");
									} else {
										event.setResultMessage(message);
									}
								}
								event.setHistory(_historyValue);
								event.setEndTime(new Date(System.currentTimeMillis()));
								ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
	
								JobHandler.getInstance().removeListener(this);
							
							// send websocket
							
								ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
								
								Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
								messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
								
							} catch (MessagingException e1) {
								e1.printStackTrace();
							} catch (Exception e1) {
								e1.printStackTrace();
							}
							
						}
					}

				};

				JobHandler.getInstance().addListener(eventListener);

				storageScaleExecutor.execTask(jobList.toArray(new Job[] {}));

				log.info(serviceName + " 서비스 시작 요청.");
				result = new Result(txId, IResult.RUNNING, stsName + "<br>서비스를 시작 합니다.");
			} else {
				result = new Result(txId, IResult.ERROR, "서비스 시작 실행 오류.");
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
		}
		
		return result;
	}

	@Override
	public Result serviceChangeMasterToSlave(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		Result result = null;
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			
			StringBuffer history = new StringBuffer();
			
			Pod pod = k8sService.getPod(namespace, serviceName, "slave");
			
			if( pod == null || !PodManager.isReady(pod)) {
				log.error("{} 의 Slave 가 존재하지 않거나 서비스 가용 상태가 아닙니다.", serviceName);
				return new Result(txId, Result.ERROR, serviceName + "의 Slave 가 존재하지 않거나 서비스 가용 상태가 아닙니다.");
			}
			
			
			MixedOperation<Service, ServiceList, DoneableService, Resource<Service, DoneableService>> services = client.inNamespace(namespace).services();
			
			List<Service> items = services.withLabel("release", serviceName).list().getItems();
			
			List<Service> masterItems = new ArrayList<>();
			
			if(items.size() > 0) {
				for (Service service : items) {
					String name = service.getMetadata().getName();
					if(name.indexOf("-master") > -1) {
						masterItems.add(service);
					}
				}
			}
			
			if(masterItems.size() > 0) {
				log.info("서비스 전환 대상 서비스가 {}개 존재합니다.", masterItems.size());
				for (Service service : masterItems) {
					log.info("	- {}", service.getMetadata().getName());
				}
			} else {
				log.error("서비스 전환 대상 서비스가 없습니다.");
				return new Result(txId, Result.ERROR, "서비스 전환 대상 서비스가 없습니다.");
			}
			
			for (Service service : masterItems) {
				RestTemplate rest = K8SUtil.getRestTemplate();
				String idToken = K8SUtil.getToken();
				String masterUrl = K8SUtil.getMasterURL();
				
				HttpHeaders headers = new HttpHeaders();
				headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
				headers.set("Authorization", "Bearer " + idToken);
				headers.set("Content-Type", "application/json-patch+json");
				
//					{ "spec": { "selector": { "component": "slave", } } }
				
				String data = "[{\"op\":\"replace\",\"path\":\"/spec/selector/role\",\"value\":\"slave\"}]";
			    
				HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

				String endpoint = masterUrl + "/api/v1/namespaces/{namespace}/services/{name}";
				ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, service.getMetadata().getName());
				
				if (response.getStatusCode() == HttpStatus.OK) {
					result = new Result(txId, Result.OK, "서비스 L/B가 Slave 인스턴스로 전환 되었습니다.");
					if (!history.toString().isEmpty()) {
						result.putValue(Result.HISTORY, history.toString() +" 가 Master 에서 Slave 로 전환 되었습니다.\nMaster 서비스로 연결된 App.은 Slave 노드에 읽기 모드로 동작 합니다.");
					}
					
					List<Service> svcList = K8SUtil.getServices(namespace, serviceName);
					metaDataCollector.save(svcList);
					
					// send websocket
					try {
						ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
						
						Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
						messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
					} catch (MessagingException e1) {
						e1.printStackTrace();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				} else {
					System.err.println("HttpStatus ; " + response.getStatusCode());
					
					log.error(response.getBody());
					result = new Result(txId, Result.ERROR, "서비스 전환 오류.");
				}
			}
			
			return result;	
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public Result serviceChangeSlaveToMaster(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		Result result = null;
		
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			
			StringBuffer history = new StringBuffer();

			MixedOperation<io.fabric8.kubernetes.api.model.Service, ServiceList, DoneableService, Resource<io.fabric8.kubernetes.api.model.Service, DoneableService>> services = client.inNamespace(namespace).services();
			
			List<Service> items = services.withLabel("release", serviceName).list().getItems();
			
			List<Service> masterItems = new ArrayList<>();
			
			if(items.size() > 0) {
				for (Service service : items) {
					String name = service.getMetadata().getName();
					if(name.indexOf("-master") > -1) {
						masterItems.add(service);
					}
				}
			}
			
			if(masterItems.size() > 0) {
				log.info("서비스 전환 대상 서비스가 {}개 존재합니다.", masterItems.size());
				for (Service service : masterItems) {
					log.info("	- {}", service.getMetadata().getName());
				}
			} else {
				log.error("서비스 전환 대상 서비스가 없습니다.");
				return new Result(txId, Result.ERROR, "서비스 전환 대상 서비스가 없습니다.");
			}
			
			for (Service service : masterItems) {
				RestTemplate rest = K8SUtil.getRestTemplate();
				String idToken = K8SUtil.getToken();
				String masterUrl = K8SUtil.getMasterURL();
				
				HttpHeaders headers = new HttpHeaders();
				headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
				headers.set("Authorization", "Bearer " + idToken);
				headers.set("Content-Type", "application/json-patch+json");
				
//					{ "spec": { "selector": { "role": "slave", } } }
				String data = "[{\"op\":\"replace\",\"path\":\"/spec/selector/role\",\"value\":\"master\"}]";
			    
				HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

				String name = service.getMetadata().getName();
				String endpoint = masterUrl + "/api/v1/namespaces/{namespace}/services/{name}";
				ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, name);
				
				if (response.getStatusCode() == HttpStatus.OK) {
					
//					result = new Result(txId, Result.OK, "서비스 전환 완료(slave->master)");
					result = new Result(txId, Result.OK, "서비스 L/B가 마스터로 전환 되었습니다.");
					if (!history.toString().isEmpty()) {
						result.putValue(Result.HISTORY, history.toString() +" 가 슬레이브에서 마스터로 전환 되었습니다.");
					}
					
					List<Service> svcList = K8SUtil.getServices(namespace, serviceName);
					metaDataCollector.save(svcList);
					
					// websocket send.
					try {
						ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
						
						Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
						messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
					} catch (MessagingException e1) {
						e1.printStackTrace();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				} else {
					System.err.println("HttpStatus ; " + response.getStatusCode());
					
					log.error(response.getBody());
					result = new Result(txId, Result.ERROR, "서비스 전환 오류.");
				}
			}
			
			return result;	
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public Result getFileLog(String namespace, String serviceName,String logType, String dates) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
  
