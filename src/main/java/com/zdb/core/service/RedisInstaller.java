package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesConstants;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ScheduleEntity;
import com.zdb.core.domain.ServiceSpec;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.K8SUtil;

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
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RedisInstaller  extends ZDBInstallerAdapter {
	private static String storageClass;

	@Value("${chart.redis.storageClass:ibmc-block-silver}")
	public void setStorageClass(String storageType) {
		storageClass = storageType;
	}
	
	/**
	 * @param exchange
	 */
	@Override
	public void doInstall(Exchange exchange) {
		String chartUrl = exchange.getProperty(Exchange.CHART_URL, String.class);
		
		ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
		ZDBRepository metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);

		ReleaseManager releaseManager = null; 
		RequestEvent event = getRequestEvent(exchange);
		
		try{ 
			
//			chartUrl = "file:///Users/a06919/redis-3.6.5.tgz";
			
			// chart 정보 로딩
			final URI uri = URI.create(chartUrl);
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}

			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			final Tiller tiller = new Tiller(client, "kube-system");
			releaseManager = new ReleaseManager(tiller);

			final InstallReleaseRequest.Builder requestBuilder = InstallReleaseRequest.newBuilder();
			
			if (requestBuilder != null) {
				requestBuilder.setTimeout(300L);
				requestBuilder.setName(service.getServiceName());
				requestBuilder.setNamespace(service.getNamespace());
				requestBuilder.setWait(false);

				hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();

				Map<String, Object> values = new HashMap<String, Object>();

//				image:
//					  registry: docker.io
//					  repository: bitnami/redis
//					  tag: 4.0.9
//					  ## Specify a imagePullPolicy
//					  ## Defaults to 'Always' if image tag is 'latest', else set to 'IfNotPresent'
//					  ## ref: http://kubernetes.io/docs/user-guide/images/#pre-pulling-images
//					  ##
//					  pullPolicy: Always
//					  ## Optionally specify an array of imagePullSecrets.
//					  ## Secrets must be manually created in the namespace.
//					  ## ref: https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/
//					  ##
//					  pullSecrets:
//					    - myRegistrKeySecretName
//				registry.au-syd.bluemix.net/cloudzdb/mysqld-exporter:v0.10.0
				
				//TODO 환경변수 처리.
				// Set Redis Version
				Map<String, Object> imageMap = new HashMap<String, Object>();
				imageMap.put("tag", service.getVersion());
				imageMap.put("registry", "registry.au-syd.bluemix.net");
				imageMap.put("repository", "cloudzdb/redis");
				
				List<String> secretName = new ArrayList<>();
				secretName.add("zdb-system-secret");
				imageMap.put("pullSecrets", secretName);

				// cluster: enabled: true
				Map<String, Object> clusterMap = new HashMap<String, Object>();
				clusterMap.put("enabled", service.isClusterEnabled());
				if(service.isClusterEnabled()) {
					clusterMap.put("slaveCount", 0);
				}
				Map<String, Object> master 					 = new HashMap<String, Object>();
				Map<String, Object> masterPodLabels 		 = new HashMap<String, Object>();
				Map<String, Object> masterPersistence 		 = new HashMap<String, Object>();
				Map<String, Object> masterService 			 = new HashMap<String, Object>();
				Map<String, Object> masterServiceAnnotations = new HashMap<String, Object>();
				Map<String, Object> masterResource 			 = new HashMap<String, Object>();
				Map<String, Object> masterResourceRequests 	 = new HashMap<String, Object>();
				Map<String, Object> masterResourceLimits 	 = new HashMap<String, Object>();
				Map<String, Object> slave 					 = new HashMap<String, Object>();
				Map<String, Object> slaveService 			 = new HashMap<String, Object>();
				Map<String, Object> slaveServiceAnnotations  = new HashMap<String, Object>();
				Map<String, Object> slaveResource 			 = new HashMap<String, Object>();
				Map<String, Object> slaveResourceRequests 	 = new HashMap<String, Object>();
				Map<String, Object> slaveResourceLimits 	 = new HashMap<String, Object>();
				Map<String, Object> metricsService 			 = new HashMap<String, Object>();
				Map<String, Object> metrics 				 = new HashMap<String, Object>();
				Map<String, String> metricServiceAnnotations = new HashMap<String, String>();
				Map<String, Object> metricsResources         = new HashMap<String, Object>();
				Map<String, Object> metricsResourcesRequests = new HashMap<String, Object>();
				Map<String, Object> metricsResourcesLimits   = new HashMap<String, Object>();

				Boolean isPublicEnabled	= false;
				int assignedMemory = 0;
				
				// Set Pod Resource
				PodSpec[] podSpec = service.getPodSpec();
				for (PodSpec pod: podSpec) {
					if (pod.getPodType().equals("master")) {
						for (ResourceSpec resource: pod.getResourceSpec()) {
							if (resource.getResourceType().equals("requests")) {
								masterResourceRequests.put("cpu"	, resource.getCpu() + "m");
								masterResourceRequests.put("memory"	, resource.getMemory() + "Mi");
								
								assignedMemory = Integer.parseInt(resource.getMemory());
								
								masterResource.put("requests", masterResourceRequests);
							} else if (resource.getResourceType().equals("limits")) {
								masterResourceLimits.put("cpu"		, resource.getCpu() + "m");
								masterResourceLimits.put("memory"	, resource.getMemory() + "Mi");
								
								masterResource.put("limits", masterResourceLimits);
							}
						}
					} else if (pod.getPodType().equals("slave")) {
						for (ResourceSpec resource: pod.getResourceSpec()) {
							if (resource.getResourceType().equals("requests")) {
								slaveResourceRequests.put("cpu"	    , resource.getCpu() + "m");
								slaveResourceRequests.put("memory"	, resource.getMemory() + "Mi");
								
								slaveResource.put("requests", slaveResourceRequests);
							} else if (resource.getResourceType().equals("limits")) {
								slaveResourceLimits.put("cpu"		, resource.getCpu() + "m");
								slaveResourceLimits.put("memory"	, resource.getMemory() + "Mi");
								
								slaveResource.put("limits", slaveResourceLimits);
							}

						}
					}
				}				
				
				// Expose Service
				ServiceSpec[] serviceSpec = service.getServiceSpec();
				for (ServiceSpec serviceInfo: serviceSpec) {					
					if (serviceInfo.getPodType().equals("master")) {
						masterServiceAnnotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", "private");						
						masterService.put("type"		, "LoadBalancer");
						masterService.put("annotations"	, masterServiceAnnotations);
						
						if ("public".equals(serviceInfo.getLoadBalancerType())) {
							isPublicEnabled = true;
						}
						
						slaveServiceAnnotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", "private");						
						slaveService.put("type"			, "LoadBalancer");
						slaveService.put("annotations"	, slaveServiceAnnotations);		
						
						if ("public".equals(serviceInfo.getLoadBalancerType())) {
							masterService.put("publicEnabled", true);
							slaveService.put("publicEnabled", true);
						}
					}
				} 
								
				metricServiceAnnotations.put("prometheus.io/scrape", "true");
				metricServiceAnnotations.put("prometheus.io/port", "9121");
				metricsService.put("type"			, "ClusterIP");
				metricsService.put("annotations"	, metricServiceAnnotations);						
				metrics.put("service", metricsService);
				
				metricsResourcesRequests.put("cpu", "100m");
				metricsResourcesRequests.put("memory", "64Mi");
				metricsResourcesLimits.put("cpu", "300m");
				metricsResourcesLimits.put("memory", "128Mi");
				metricsResources.put("requests", metricsResourcesRequests);
				metricsResources.put("limits", metricsResourcesLimits);
				metrics.put("resources", metricsResources);
				
				//TODO 환경변수 처리.
				metrics.put("image", "registry.au-syd.bluemix.net/cloudzdb/redis_exporter");
				 
				masterPodLabels.put("billingType"		, "hourly");

				List<String> extraFlags = new ArrayList<String>();
				extraFlags.add("--requirepass $(REDIS_PASSWORD)");
				extraFlags.add("--masterauth $(REDIS_PASSWORD)");
				
				boolean cacheModeEnabled = false;
								
				if ("DATA".equals(service.getPurpose())) {
					cacheModeEnabled = false;
				} else if ("SESSION".equals(service.getPurpose())) {
					cacheModeEnabled = true;
				}

//				RedisConfig[] redisConfig = service.getRedisConfig();
//				
//		        for (RedisConfig redis: redisConfig) {
//		        	if (redis.getPodType().equals("master")) {
//						StringBuffer customRedisConfig = new StringBuffer();
//						
//						customRedisConfig.append("bind 0.0.0.0").append("\n");
//						customRedisConfig.append("logfile /opt/bitnami/redis/logs/redis-server.log").append("\n");
//						customRedisConfig.append("pidfile /opt/bitnami/redis/tmp/redis.pid").append("\n");
//						customRedisConfig.append("dir /opt/bitnami/redis/data").append("\n");
//						customRedisConfig.append("rename-command FLUSHDB FDB").append("\n");
//						customRedisConfig.append("rename-command FLUSHALL FALL").append("\n");
//
//						if (cacheModeEnabled) {
//							customRedisConfig.append("appendonly no").append("\n");		
//							customRedisConfig.append("save ").append("\"\"").append("\n");
//						} else {
//							customRedisConfig.append("appendonly yes").append("\n");
//							customRedisConfig.append("save ").append(redis.getConfig().get("save") == null ? "900 1 300 10 60 10000" : redis.getConfig().get("save")).append("\n");
//						}
//						
//						customRedisConfig.append("timeout ").append(redis.getConfig().get("timeout") == null                               	   ? "0" 		  : redis.getConfig().get("timeout")).append("\n");
//						customRedisConfig.append("tcp-keepalive ").append(redis.getConfig().get("tcp-keepalive") == null 			 		   ? "300" 		  : redis.getConfig().get("tcp-keepalive")).append("\n");
//						customRedisConfig.append("slowlog-log-slower-than ").append(redis.getConfig().get("slowlog-log-slower-than") == null   ? "10000" 	  : redis.getConfig().get("slowlog-log-slower-than")).append("\n");
//						customRedisConfig.append("slowlog-max-len ").append(redis.getConfig().get("slowlog-max-len") == null 				   ? "128" 		  : redis.getConfig().get("slowlog-max-len")).append("\n");
//						customRedisConfig.append("hash-max-ziplist-entries ").append(redis.getConfig().get("hash-max-ziplist-entries") == null ? "512" 		  : redis.getConfig().get("hash-max-ziplist-entries")).append("\n");
//						customRedisConfig.append("hash-max-ziplist-value ").append(redis.getConfig().get("hash-max-ziplist-value") == null 	   ? "64" 		  : redis.getConfig().get("hash-max-ziplist-value")).append("\n");
//						customRedisConfig.append("list-max-ziplist-size ").append(redis.getConfig().get("list-max-ziplist-size") == null 	   ? "-2" 		  : redis.getConfig().get("list-max-ziplist-size")).append("\n");
//						customRedisConfig.append("zset-max-ziplist-entries ").append(redis.getConfig().get("zset-max-ziplist-entries") == null ? "128" 		  : redis.getConfig().get("zset-max-ziplist-entries")).append("\n");
//						customRedisConfig.append("zset-max-ziplist-value ").append(redis.getConfig().get("zset-max-ziplist-value") == null 	   ? "64" 		  : redis.getConfig().get("zset-max-ziplist-value")).append("\n");
//						customRedisConfig.append("maxmemory-policy ").append(redis.getConfig().get("maxmemory-policy") == null 		  		   ? "noeviction" : redis.getConfig().get("maxmemory-policy")).append("\n");
//						customRedisConfig.append("maxmemory-samples ").append(redis.getConfig().get("maxmemory-samples") == null 			   ? "5" 		  : redis.getConfig().get("maxmemory-samples")).append("\n");
//						customRedisConfig.append("notify-keyspace-events ").append(redis.getConfig().get("notify-keyspace-events") == null 	   ? "\"\""			  : redis.getConfig().get("notify-keyspace-events")).append("\n");
//						
//						customRedisConfig.append("maxmemory ").append(assignedMemory * 75 / 100).append("mb").append("\n");		// 할당메모리의 75%
//
//						master.put("config"		, customRedisConfig.toString());
//		        	}
//		        } 				
	
				StringBuffer customRedisConfig = new StringBuffer();
				
				customRedisConfig.append("bind 0.0.0.0").append("\n");
				customRedisConfig.append("logfile /opt/bitnami/redis/logs/redis-server.log").append("\n");
				customRedisConfig.append("pidfile /opt/bitnami/redis/tmp/redis.pid").append("\n");
				customRedisConfig.append("dir /opt/bitnami/redis/data").append("\n");
				customRedisConfig.append("rename-command FLUSHDB FDB").append("\n");
				customRedisConfig.append("rename-command FLUSHALL FALL").append("\n");

				if (cacheModeEnabled) {
					customRedisConfig.append("appendonly no").append("\n");		
					customRedisConfig.append("save ").append("\"\"").append("\n");
				} else {
					customRedisConfig.append("appendonly yes").append("\n");
					customRedisConfig.append("save 900 1 300 10 60 10000").append("\n");
				}

				customRedisConfig.append("maxmemory ").append(assignedMemory * 75 / 100).append("mb").append("\n");		// 할당메모리의 75%

				master.put("config"		, customRedisConfig.toString());
		        master.put("extraFlags"	, extraFlags);        

		        if (cacheModeEnabled) {
		        	masterPersistence.put("enabled"			, false);
		        } else {
		        	String pvSize = null;
		        	
		        	if ( assignedMemory <= 6000) {
		        		pvSize = "20Gi";
		        	} else if ( assignedMemory > 6000) {
		        		pvSize = "40Gi";
		        	}
		        	
		        	masterPersistence.put("enabled"			, true);
		        	
		        	masterPersistence.put("path"			, "/bitnami/redis/data");
					masterPersistence.put("subPath"			, "redis/data");
					masterPersistence.put("storageClass"	, storageClass);
					masterPersistence.put("size"			, pvSize);
		        }
		        
				master.put("persistence", masterPersistence);
				master.put("resources"	, masterResource);        
		        master.put("podLabels"  , masterPodLabels);
		        master.put("service" 	, masterService);
		        
		        slave.put("service"  	, slaveService);

				values.put("image" , imageMap);
				values.put("cluster" , clusterMap);
				values.put("master", master);				
				values.put("slave" , slave);				
				values.put("metrics" , metrics);				
				

				String deployType = service.getDeployType() == null ? "NEW" : service.getDeployType().toUpperCase();
				
				if ("NEW".equals(deployType)) {
					 
				} else if ("RECOVERY".equals(deployType)) {
					Map<String, Object> persistence = new HashMap<String, Object>();

					persistence.put("existingClaim" , service.getPersistenceExistingClaim());
					values.put("persistence" , persistence);	
				}
				

				DumperOptions options = new DumperOptions();
				options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
				options.setPrettyFlow(true);				
				
				Yaml yaml = new Yaml(options);
				String valueYaml = yaml.dump(values);

				valuesBuilder.setRaw(valueYaml);
				
				log.debug("###############################################################");
				log.debug("# Input Valeus(YAML) : " + valueYaml);				
				log.debug("###############################################################");

				final Future<InstallReleaseResponse> releaseFuture = releaseManager.install(requestBuilder, chart);
				final Release release = releaseFuture.get().getRelease();
				 
				if (release != null) {
					ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
					if(releaseMeta == null) {
						releaseMeta = new ReleaseMetaData();
					}
					releaseMeta.setAction("CREATE");
					releaseMeta.setApp(release.getChart().getMetadata().getName());
					releaseMeta.setAppVersion(service.getVersion());
					releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
					releaseMeta.setChartName(release.getChart().getMetadata().getName());
					releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()*1000L));
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
					releaseMeta.setManifest(release.getManifest());
					releaseMeta.setPublicEnabled(isPublicEnabled);
					releaseMeta.setPurpose(service.getPurpose());   // SESSION or DATA
					releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));
					releaseMeta.setUserId(service.getRequestUserId());
					
					log.info(new Gson().toJson(releaseMeta));
					
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					
					log.debug("###############################################################");
					log.debug("# Rendered Manifest : " + release.getManifest());
					log.debug("# " + gson.toJson(releaseMeta.getNotes()));
					log.debug("###############################################################");
					
					releaseRepository.save(releaseMeta);
					
					Tag typeTag = new Tag();
					typeTag.setNamespace(service.getNamespace());
					typeTag.setReleaseName(service.getServiceName());
					typeTag.setTagName(service.getServiceType());		
					
					tagRepository.save(typeTag);
					
					if (service.getPurpose() != null) {
						Tag purposeTag = new Tag();
						purposeTag.setNamespace(service.getNamespace());
						purposeTag.setReleaseName(service.getServiceName());
						purposeTag.setTagName(service.getPurpose().toLowerCase());
						
						tagRepository.save(purposeTag);
					}					
					
					exchange.setProperty(KubernetesConstants.KUBERNETES_SERVICE_NAME, service.getServiceName());
					exchange.setProperty(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, service.getNamespace());
					
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
										boolean isReady = Readiness.isReady(pod);
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
//										messageSender.sendToClient("redis installer");
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
							}

						}
					}).start();
					
					lacth.await(600, TimeUnit.SECONDS);
					
					if (lacth.getCount() == 0) {
						if (service.isBackupEnabled()) {
							// 스케줄 등록...
							ScheduleEntity schedule = new ScheduleEntity();
							schedule.setNamespace(service.getNamespace());
							schedule.setServiceType(service.getServiceType());
							schedule.setServiceName(service.getServiceName());
							schedule.setStartTime("01:00");
							schedule.setStorePeriod(2);
							schedule.setUseYn("Y");
							schedule.setDeleteYn("N");
							backupProvider.saveSchedule(exchange.getProperty(Exchange.TXID, String.class), schedule);
						}
						event.setStatus(IResult.OK);
						event.setResultMessage("서비스 생성 완료");
						event.setEndTime(new Date(System.currentTimeMillis()));
					} else {
						event.setStatus(IResult.ERROR);
						event.setResultMessage("서비스 생성 실패. - 10분 타임아웃");
						event.setEndTime(new Date(System.currentTimeMillis()));
						
						releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
						if(releaseMeta != null) {
							if("CREATING".equals(releaseMeta.getStatus())) {
								releaseMeta.setStatus("ERROR");
								releaseMeta.setDescription("서비스 생성 실패.(타임아웃)");
								
								releaseRepository.save(releaseMeta);
							}
						}
					}
					

//					messageSender.sendToClient("redis installer");
				} else {
					event.setStatus(IResult.ERROR);
					event.setResultMessage("Installation failed.");
					event.setEndTime(new Date(System.currentTimeMillis()));
					
//					messageSender.sendToClient("redis installer");
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
		
	public RequestEvent getRequestEvent(Exchange exchange) {
		ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
		String txId = exchange.getProperty(Exchange.TXID, String.class);

		RequestEvent requestEvent = new RequestEvent();
		requestEvent.setTxId(txId);
		requestEvent.setNamespace(service.getNamespace());
		requestEvent.setServiceName(service.getServiceName());
		requestEvent.setServiceType(service.getServiceType());
		requestEvent.setStartTime(new Date(System.currentTimeMillis()));
		requestEvent.setOperation(RequestEvent.CREATE);

		return requestEvent;
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBInstaller#doUnInstall(com.zdb.core.domain.Exchange)
	 */
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
		event.setServiceType(ZDBType.Redis.getName());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setOperation(RequestEvent.DELETE);

		ReleaseManager releaseManager = null;
		try {
			DefaultKubernetesClient client = (DefaultKubernetesClient) K8SUtil.kubernetesClient().inNamespace(namespace);

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

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

			uninstallRequestBuilder.setName(serviceName); // set releaseName
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
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds() * 1000L));
				releaseMeta.setNamespace(namespace);
				releaseMeta.setReleaseName(serviceName);
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				releaseMeta.setDescription(release.getInfo().getDescription());
				releaseMeta.setManifest(release.getManifest());
				releaseMeta.setStatus("DELETED");
				releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));

				log.info(new Gson().toJson(releaseMeta));

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

					// disk usage 정보 삭제처리 
					if (persistentVolumeClaims.size() > 0) {
						diskUsageRepository.deleteByNamespaceAndReleaseName(namespace, serviceName);
					}
				}

				// tag 정보 삭제 
				tagRepository.deleteByNamespaceAndReleaseName(namespace, serviceName);
				
				// backup resource 삭제 요청
				backupProvider.removeServiceResource(txId, namespace, ZDBType.Redis.getName(), serviceName);
				
				event.setStatus(IResult.OK);
				event.setResultMessage("Successfully deleted.");
				event.setEndTime(new Date(System.currentTimeMillis()));
			} else {
				String msg = "설치된 서비스가 존재하지 않습니다.";
				event.setResultMessage(msg);
				event.setStatus(IResult.ERROR);
				event.setEndTime(new Date(System.currentTimeMillis()));
				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
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
			
			if(releaseManager != null){
				try {
					releaseManager.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
			
//			messageSender.sendToClient("redis installer");
			messageSender.sendToClientRefresh(serviceName);
		}
	
	}
}
