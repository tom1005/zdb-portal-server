package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
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
import com.zdb.core.domain.Connection;
import com.zdb.core.domain.ConnectionInfo;
import com.zdb.core.domain.IResult;
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
		Result result = new Result(txId);

		ReleaseManager releaseManager = null;
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
			
			Secret secret  = K8SUtil.getSecret(service.getNamespace(), service.getServiceName());
			String password = new String();
			
			if (secret != null) {
				Map<String, String> data = secret.getData();

				if (!data.isEmpty()) {
					password = new String(Base64.getDecoder().decode(data.get("redis-password").getBytes()));
				}
			}			
			
			values.put("password", password );
			
			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			options.setPrettyFlow(true);

			Yaml yaml = new Yaml(options);
			String valueYaml = yaml.dump(values);
 
			log.info("****** YAML Values : " + valueYaml);
 
			valuesBuilder.setRaw(valueYaml);

			log.info(service.getServiceName() + " update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();

			if (release != null) {
				ReleaseMetaData releaseMeta = releaseName;
				if(releaseMeta == null) {
					releaseMeta = new ReleaseMetaData();
				}
				releaseMeta.setAction("UPDATE");
				releaseMeta.setApp(release.getChart().getMetadata().getName());
				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
				releaseMeta.setChartName(release.getChart().getMetadata().getName());
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()));
				releaseMeta.setNamespace(service.getNamespace());
				releaseMeta.setReleaseName(service.getServiceName());
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				releaseMeta.setDescription(release.getInfo().getDescription());
			    releaseMeta.setInputValues(valuesBuilder.getRaw());
			    releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
			    releaseMeta.setManifest(release.getManifest());
				releaseMeta.setInputValues(valueYaml);
				releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));

				log.info(new Gson().toJson(releaseMeta));

				releaseRepository.save(releaseMeta);
			}

			log.info(service.getServiceName() + " update success!");
			result = new Result(txId, IResult.OK, "scale-up request.").putValue(IResult.UPDATE, release);
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "Unauthorized", null);
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
				return new Result(txId, Result.UNAUTHORIZED, "Unauthorized", null);
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
	 * @return
	 * 
	 * @author chanhokim
	 */
	@Override
	public Result getDBVariables(String txId, String namespace, String serviceName) {
		Result result = Result.RESULT_OK(txId);
		Jedis redisConnection = null;

		try {
			redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "master"); 

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
	public Result updateDBVariables(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
		Result result = Result.RESULT_OK(txId);
		Jedis redisConnection = null;

		try {
			result = updateConfig(txId, namespace, serviceName, config); 
			
			result.putValue(IResult.REDIS_CONFIG, config);
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "Unauthorized", null);
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
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			StringBuffer redisConfig = new StringBuffer();
			redisConfig.append("bind 0.0.0.0").append("\n");
			redisConfig.append("logfile /opt/bitnami/redis/logs/redis-server.log").append("\n");
			redisConfig.append("pidfile /opt/bitnami/redis/tmp/redis.pid").append("\n");
			redisConfig.append("dir /opt/bitnami/redis/data").append("\n");
			redisConfig.append("rename-command FLUSHDB FDB").append("\n");
			redisConfig.append("rename-command FLUSHALL FALL").append("\n");			

			Jedis redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "master"); 
			
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
			
			Resource<ConfigMap, DoneableConfigMap> configMapResource = client.configMaps().inNamespace(namespace).withName(serviceName + "-redis-config");

		    ConfigMap configMap = configMapResource.createOrReplace(new ConfigMapBuilder().
		          withNewMetadata().withName(serviceName + "-redis-config").endMetadata().
		          addToData("redis-config", redisConfig.toString()).
		          build());			
			
			// Set Redis master config.
			redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "master");

			if (redisConnection == null) {
				throw new Exception("Cannot connect Redis(Master). Namespace: " + namespace + ", Service Name: " + serviceName);
			}
			RedisConfiguration.setConfig(zdbRedisConfigRepository, redisConnection, namespace, serviceName, config); 
			
			
			// Set Redis slave config.
			if (releaseMetaData != null && releaseMetaData.getClusterEnabled()) {
				redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "slave");

				if (redisConnection == null) {
					throw new Exception("Cannot connect Redis(Slave). Namespace: " + namespace + ", Service Name: " + serviceName);
				}
				RedisConfiguration.setConfig(zdbRedisConfigRepository, redisConnection, namespace, serviceName, config); 
			}
			result = new Result(txId, IResult.OK, "Redis config update request.");			
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			result = new Result(txId, IResult.ERROR, "Redis config update fail. - " + e.getLocalizedMessage());
		}
		
		return result;
	}	
}
  
