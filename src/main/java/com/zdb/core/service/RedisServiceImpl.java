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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Mycnf;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBRedisConfig;
import com.zdb.core.repository.ZDBRedisConfigRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.NamespaceResourceChecker;
import com.zdb.redis.RedisConfiguration;
import com.zdb.redis.RedisConnection;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
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
			boolean availableResource = isAvailableScaleUp(service);
			
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

						boolean availableResource = NamespaceResourceChecker.isAvailableResource(service.getNamespace(),
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
					String[] params = line.split(" ");
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
				if (redisConfigMap.get("notify-keyspace-events").equals("\"\"")) {
					zdbRedisConfig.setNotifyKeyspaceEvents("");
				} else {
					zdbRedisConfig.setNotifyKeyspaceEvents(redisConfigMap.get("notify-keyspace-events"));
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
					String[] params = line.split(" ");
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
				if (config.get("notify-keyspace-events").equals("")) {
					redisConfigMap.put("notify-keyspace-events", config.get("\"\""));
				} else {
					redisConfigMap.put("notify-keyspace-events", config.get("notify-keyspace-events"));
				}
				
				StringBuilder mapAsString = new StringBuilder("");
			    for (String key : redisConfigMap.keySet()) {
			        mapAsString.append(key + " " + redisConfigMap.get(key) + "\n");
			    }
			    mapAsString.delete(mapAsString.length()-2, mapAsString.length()).append("");
				String redisConfigMapAsString = mapAsString.toString();
				
				String configMapName = configMap.getMetadata().getName();
				client.configMaps().inNamespace(namespace).withName(configMapName).edit().addToData("redis-config", redisConfigMapAsString).done();
			}
			result = new Result(txId, IResult.OK, "환경설정 변경 완료: " + historyValue);		
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
					String[] params = line.split(" ");
					if(params.length == 2) {
						String key = params[0].trim();
						String value = params[1].trim();
						currentConfigMap.put(key, value);
						
						String newValue = newConfig.get(key);
						
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
}
  
