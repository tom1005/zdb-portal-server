package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesConstants;
import com.zdb.core.domain.KubernetesOperations;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.RedisConfig;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.ServiceSpec;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.event.ZDBEventWatcher;
import com.zdb.core.exception.DuplicateException;
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
import hapi.services.tiller.Tiller.ListReleasesRequest;
import hapi.services.tiller.Tiller.ListReleasesResponse;
import hapi.services.tiller.Tiller.UninstallReleaseRequest;
import hapi.services.tiller.Tiller.UninstallReleaseResponse;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RedisInstaller implements ZDBInstaller {
	@Autowired
	private ZDBReleaseRepository releaseRepository;
	
	@Autowired
	private ZDBRepository metaRepository;
	
	/**
	 * @param exchange
	 */
	@Override
	public void doInstall(Exchange exchange) {
		String txId = exchange.getProperty(Exchange.TXID, String.class);
		String chartUrl = exchange.getProperty(Exchange.CHART_URL, String.class);
		
		ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
		ZDBRepository metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);

		System.out.println("ZDBEntiry ..................");
		System.out.println(service.toString());
		
		ReleaseManager releaseManager = null; 
		
		try{ 
			if(!K8SUtil.isNamespaceExist(service.getNamespace())) {
				// namespace 생성
				CountDownLatch closeLatch = new CountDownLatch(1);
				ZDBEventWatcher<Namespace> watcher = new ZDBEventWatcher<Namespace>(
						KubernetesOperations.CREATE_NAMESPACE_OPERATION, 
						closeLatch, 
						exchange);
				
				Watch namespaceWatch = K8SUtil.kubernetesClient().namespaces().watch(watcher);
				
				try {
					K8SUtil.doCreateNamespace(service.getNamespace());
					closeLatch.await(30, TimeUnit.SECONDS);
				} catch (DuplicateException e) {
					log.info(service.getNamespace()+ " 이미 사용중 입니다.");
				} finally {
					namespaceWatch.close();
				}
			}
			
			// chart 정보 로딩
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

			// 서비스 명 중복 체크
			if (K8SUtil.isServiceExist(service.getNamespace(), service.getServiceName())) {
				String msg = "사용중인 서비스 명입니다.[" + service.getServiceName() + "]";
				log.error(msg);

				event.setStatus(IResult.ERROR);
				event.setResultMessage(msg);
				event.setStatusMessage("서비스명 중복 오류");
				event.setUpdateTime(new Date(System.currentTimeMillis()));
				event.setEndTIme(new Date(System.currentTimeMillis()));

				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

				return;
			}
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

			final InstallReleaseRequest.Builder requestBuilder = InstallReleaseRequest.newBuilder();
			
			if (requestBuilder != null) {
				requestBuilder.setTimeout(300L);
				requestBuilder.setName(service.getServiceName());
				requestBuilder.setNamespace(service.getNamespace());
				requestBuilder.setWait(false);

				hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();

				Map<String, Object> values = new HashMap<String, Object>();

				// Set Redis Version
				Map<String, Object> imageMap = new HashMap<String, Object>();
				imageMap.put("tag", service.getVersion());
				
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
//						masterServiceAnnotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", serviceInfo.getLoadBalancerType());
						masterServiceAnnotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", "private");						
						masterService.put("type"		, "LoadBalancer");
						masterService.put("annotations"	, masterServiceAnnotations);
						
						if ("public".equals(serviceInfo.getLoadBalancerType())) {
							isPublicEnabled = true;
						}
						
//						slaveServiceAnnotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", serviceInfo.getLoadBalancerType());
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
					customRedisConfig.append("save 900 1 300 10 60 10000");
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
					masterPersistence.put("storageClass"	, "ibmc-block-silver");
					masterPersistence.put("size"			, pvSize);
		        }
		        
				master.put("persistence", masterPersistence);
				master.put("resources"	, masterResource);        
		        master.put("podLabels"  , masterPodLabels);
		        master.put("service" 	, masterService);
		        
		        slave.put("service"  	, slaveService);

				values.put("image" , imageMap);
				values.put("master", master);				
				values.put("slave" , slave);				
				
				DumperOptions options = new DumperOptions();
				options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
				options.setPrettyFlow(true);				
				
				Yaml yaml = new Yaml(options);
				String valueYaml = yaml.dump(values);

				valuesBuilder.setRaw(valueYaml);
				
				log.info("###############################################################");
				log.info("# Input Valeus(YAML) : " + valueYaml);				
				log.info("###############################################################");

				final Future<InstallReleaseResponse> releaseFuture = releaseManager.install(requestBuilder, chart);
				final Release release = releaseFuture.get().getRelease();
				 
				if (release != null) {
					ReleaseMetaData releaseMeta = new ReleaseMetaData();
					releaseMeta.setAction("CREATE");
					releaseMeta.setApp(release.getChart().getMetadata().getName());
					releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
					releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
					releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()*1000L));
					releaseMeta.setNamespace(service.getNamespace());
					releaseMeta.setReleaseName(service.getServiceName());
					releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
					releaseMeta.setDescription(release.getInfo().getDescription());
					releaseMeta.setInputValues(valuesBuilder.getRaw());
					releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
					releaseMeta.setManifest(release.getManifest());
					releaseMeta.setPublicEnabled(isPublicEnabled);
					releaseMeta.setPurpose(service.getPurpose());   // SESSION or DATA

					log.info(new Gson().toJson(releaseMeta));
					
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					
					log.info("###############################################################");
					log.info("# Rendered Manifest : " + release.getManifest());
					log.info("# " + gson.toJson(releaseMeta.getNotes()));
					log.info("###############################################################");
					
					releaseRepository.save(releaseMeta);
					
					exchange.setProperty(KubernetesConstants.KUBERNETES_SERVICE_NAME, service.getServiceName());
					exchange.setProperty(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, service.getNamespace());
				} else {
					event.setStatus(IResult.ERROR);
					event.setResultMessage("Redis Instance 생성 오류");
					
					ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
				}
			}
			
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			RequestEvent event = getRequestEvent(exchange);
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));

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
			event.setEndTIme(new Date(System.currentTimeMillis()));
			
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
		requestEvent.setEventType(EventType.Deployment.name());
		
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
		event.setEventType(EventType.Delete.name());
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setOpertaion(KubernetesOperations.DELETE_SERVICE_INSTANCE);

		ReleaseManager releaseManager = null;
		try {
			DefaultKubernetesClient client = (DefaultKubernetesClient) K8SUtil.kubernetesClient()
					.inNamespace(namespace);

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
				event.setEndTIme(new Date(System.currentTimeMillis()));
				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

//				return new Result(txId, IResult.ERROR, msg);
			    return;
			}

			final UninstallReleaseRequest.Builder uninstallRequestBuilder = UninstallReleaseRequest.newBuilder();

			uninstallRequestBuilder.setName(serviceName); // set releaseName
			uninstallRequestBuilder.setPurge(true); // --purge

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
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds()));
				releaseMeta.setNamespace(namespace);
				releaseMeta.setReleaseName(serviceName);
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				releaseMeta.setDescription(release.getInfo().getDescription());
				releaseMeta.setManifest(release.getManifest());
				releaseMeta.setStatus("DELETED");

				log.info(new Gson().toJson(releaseMeta));

				ReleaseMetaData findByReleaseName = releaseRepository.findByReleaseName(serviceName);
				if (findByReleaseName != null) {
					findByReleaseName.setStatus(release.getInfo().getStatus().getCode().name());
					findByReleaseName.setUpdateTime(new Date(System.currentTimeMillis()));
					releaseRepository.save(findByReleaseName);
				} else {
					releaseRepository.save(releaseMeta);
				}

				{ // pvc 삭제
					List<PersistentVolumeClaim> persistentVolumeClaims = K8SUtil.getPersistentVolumeClaims(namespace, serviceName);

					for (PersistentVolumeClaim pvc : persistentVolumeClaims) {
						client.inNamespace(namespace).persistentVolumeClaims().withName(pvc.getMetadata().getName()).delete();
					}
				}

			} else {
				String msg = "설치된 서비스가 존재하지 않습니다.";
				event.setResultMessage(msg);
				event.setStatusMessage("서비스 삭제 실패");
				event.setStatus(IResult.ERROR);
				event.setEndTIme(new Date(System.currentTimeMillis()));
				ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);

//				return new Result(txId, IResult.ERROR, "Service 삭제 오류!");
			    return;
			}

		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			event = getRequestEvent(exchange);
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				event.setResultMessage("Unauthorized");
			} else {
				event.setResultMessage(e.getMessage());
			}
			
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			getRequestEvent(exchange);
			
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			
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