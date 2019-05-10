package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.DefaultExchange;
import com.zdb.core.domain.EventMetaData;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.NamespaceResource;
import com.zdb.core.domain.PersistentVolumeClaimEntity;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.UserInfo;
import com.zdb.core.domain.ZDBConfig;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBPersistenceEntity;
import com.zdb.core.domain.ZDBStatus;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.repository.PersistentVolumeClaimRepository;
import com.zdb.core.repository.TagRepository;
import com.zdb.core.repository.ZDBConfigRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.HeapsterMetricUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.NamespaceResourceChecker;
import com.zdb.core.util.NumberUtils;
import com.zdb.core.util.ZDBLogViewer;
import com.zdb.core.vo.PodMetrics;
import com.zdb.redis.RedisConfiguration;
import com.zdb.redis.RedisConnection;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentList;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

/**
 * ZDBRestService Implementation
 * 
 * @author 06919
 *
 */
@Slf4j
@Configuration
public abstract class AbstractServiceImpl implements ZDBRestService {

	@Autowired
	protected ZDBRepository zdbRepository;
	
	@Autowired
	protected EventRepository eventRepository;
	
	@Autowired
	protected MetadataRepository metaDataRepository;
	
	@Autowired
	protected ZDBReleaseRepository releaseRepository;
	
	@Autowired
	protected DiskUsageRepository diskRepository;
	
	@Autowired
	protected TagRepository tagRepository;
	
	@Autowired
	protected ZDBConfigRepository zdbConfigRepository;
	
	@Autowired
	BeanFactory beanFactory;
	
	@Autowired
	protected K8SService k8sService;
	
	@Autowired
	protected AlertService alertService;
	
	@Autowired PersistentVolumeClaimRepository persistentVolumeClaimRepository;
	
	protected String chartUrl;
	
	static final int WORKER_COUNT = 5;
	
	static BlockingQueue<Exchange> deploymentQueue = null;
	
	protected AbstractServiceImpl() {

		if (deploymentQueue == null) {
			deploymentQueue = new ArrayBlockingQueue<Exchange>(MAX_QUEUE_SIZE);
			
			for(int i = 0; i < WORKER_COUNT; i++) {
				new Thread(new DeploymentConsumer("worker-"+i, deploymentQueue)).start();
			}
		}
		
	}

	/**
	 * 서비스 생성 가능 여부 체크
	 * @return
	 */
	protected Result isDeploymentAvaliable() {
		if(deploymentQueue.size() >= MAX_QUEUE_SIZE) {
			return new Result("", IResult.ERROR, "서비스 생성 요청이 너무 많습니다. 잠시 후 다시 이용하세요. | "+ deploymentQueue.size()+"/"+MAX_QUEUE_SIZE);
		} else {
			return Result.RESULT_OK;
		}
	}
	
	/**
	 * 서비스 생성 요청.
	 * @param req
	 */
	protected void deploymentRequest(Exchange req) {
		try {
			deploymentQueue.put(req);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	protected void unInstallRequest(Exchange req) {
		try {
			deploymentQueue.put(req);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public Result getNamespaceResource(String namespace, String userId) throws Exception {
		try {
			NamespaceResource namespaceResource = NamespaceResourceChecker.getNamespaceResource(namespace, userId);
			if(namespaceResource != null) {
				return new Result("", IResult.OK, "").putValue(IResult.NAMESPACE_RESOURCE, namespaceResource);
			} else {
				return new Result("", IResult.ERROR, "가용 리소스 조회 오류.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", IResult.ERROR, e.getMessage());
		}
	}
	
	@Override
	public Result isAvailableResource(String namespace, String userId, String cpu, String memory, boolean clusterEnabled) throws Exception {
		int requestCpu = 0;
		int requestMem = 0;
		
		String masterCpu = cpu;
		String masterMemory = memory;
		
		if(clusterEnabled) {
			String slaveCpu = cpu;
			String slaveMemory = memory;
			
			requestCpu += Integer.parseInt(slaveCpu);
			requestMem += Integer.parseInt(slaveMemory);
		}
		
		requestCpu += Integer.parseInt(masterCpu);
		requestMem += Integer.parseInt(masterMemory);
		
		log.warn("cpu : {}, memory : {}, requestCpu : {}, requestMem : {}, clusterEnabled : {}", cpu, memory, requestCpu, requestMem, clusterEnabled);
		
		try {
			boolean availableResource = NamespaceResourceChecker.isAvailableResource(namespace, userId, requestMem, requestCpu);
			if(availableResource) {
				return new Result("", IResult.OK, "");
			} else {
				return new Result("", IResult.ERROR, "가용 리소스가 부족가 부족합니다.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", IResult.ERROR, e.getMessage());
		}
	}
	
	@Override
	public Result createDeployment(String txId, ZDBEntity service, UserInfo userInfo) throws Exception {
		Result requestCheck = isDeploymentAvaliable();
		if(!requestCheck.isOK()) {
			return requestCheck;
		} else {
			
			service.setNamespace(service.getNamespace().trim().toLowerCase());
			service.setServiceName(service.getServiceName().trim().toLowerCase());

			Exchange exchange = new DefaultExchange();
			exchange.setProperty(Exchange.TXID, txId);
			exchange.setProperty(Exchange.ZDBENTITY, service);
			exchange.setProperty(Exchange.NAMESPACE, service.getNamespace());
			exchange.setProperty(Exchange.SERVICE_NAME, service.getServiceName());
			exchange.setProperty(Exchange.SERVICE_TYPE, service.getServiceType());
			exchange.setProperty(Exchange.CHART_URL, chartUrl);
			exchange.setProperty(Exchange.META_REPOSITORY, zdbRepository);
			exchange.setProperty(Exchange.OPERTAION, EventType.Install);
			
			// private docker repository 사용을 위해 namespace 마다 secret 생성 필요
			try(DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
				String namespace = service.getNamespace();
				String secretName = "zdb-system-secret";
				
				Secret s = client.inNamespace(namespace).secrets().withName(secretName).get();
				if(s == null) {
					// namespace : "zdb-system" 에 환경 구성시 생성한 secret 을 복제한다.  
					Secret secret = client.inNamespace("zdb-system").secrets().withName(secretName).get();
					
					ObjectMeta metadata = new ObjectMeta();
					metadata.setNamespace(namespace);
					metadata.setName(secretName);
					secret.setMetadata(metadata);
					
					client.inNamespace(namespace).secrets().create(secret);
					
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			
			long s = System.currentTimeMillis();
			// 서비스 명 중복 체크
			ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
			if (releaseMeta != null) {
				String msg = "사용중인 서비스 명입니다.[" + service.getServiceName() + "]";
				log.error(msg);

				return new Result(txId, IResult.ERROR, msg);
			}
			
			// 가용 리소스 체크
			boolean clusterEnabled = service.isClusterEnabled();
			PodSpec[] podSpec = service.getPodSpec();
			
			ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
			String masterCpu = masterSpec.getCpu();
			String masterMemory = masterSpec.getMemory();
			
			Result availableResource = isAvailableResource(service.getNamespace(), service.getRequestUserId(), masterCpu, masterMemory, clusterEnabled);
			if(!availableResource.isOK()) {
				return new Result(txId, IResult.ERROR, availableResource.getMessage());
			}
			
			// 설치 요청 정보 저장
			if(releaseMeta == null) {
				releaseMeta = new ReleaseMetaData();
			}
			releaseMeta.setAction("CREATE");
			releaseMeta.setApp(service.getServiceType());
			releaseMeta.setAppVersion(service.getVersion());
			releaseMeta.setChartVersion("");
			releaseMeta.setChartName("");
			releaseMeta.setCreateTime(new Date(System.currentTimeMillis()));
			releaseMeta.setNamespace(service.getNamespace());
			releaseMeta.setReleaseName(service.getServiceName());
			releaseMeta.setStatus("REQUEST");
			releaseMeta.setDescription("install request.");
			releaseMeta.setInputValues("");
			releaseMeta.setNotes("");
			if("mariadb".equals(service.getServiceType())) {
				releaseMeta.setDbname(service.getMariaDBConfig().getMariadbDatabase());
			}
			releaseMeta.setManifest("");
			releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));
			releaseMeta.setPublicEnabled(clusterEnabled);
			releaseMeta.setPurpose(service.getPurpose());
			releaseMeta.setClusterEnabled(service.isClusterEnabled());
			releaseMeta.setUserId(userInfo.getUserId());

			log.info(">>> install request : "+new Gson().toJson(releaseMeta));

			releaseRepository.save(releaseMeta);

			// install request
			deploymentRequest(exchange);

			return new Result(txId, IResult.OK, "서비스 생성 요청됨");
		}
		
	}
	
	@Override
	public synchronized Result deleteServiceInstance(String txId, String namespace, String serviceType, String serviceName) throws Exception {

		Result requestCheck = isDeploymentAvaliable();
		if (!requestCheck.isOK()) {
			return requestCheck;
		}

		Exchange exchange = new DefaultExchange();
		exchange.setProperty(Exchange.TXID, txId);
		exchange.setProperty(Exchange.NAMESPACE, namespace);
		exchange.setProperty(Exchange.SERVICE_NAME, serviceName);
		exchange.setProperty(Exchange.SERVICE_TYPE, serviceType);
		exchange.setProperty(Exchange.CHART_URL, chartUrl);
		exchange.setProperty(Exchange.OPERTAION, EventType.Delete);
		exchange.setProperty(Exchange.META_REPOSITORY, zdbRepository);

		unInstallRequest(exchange);

		return new Result(txId, IResult.OK, "서비스 삭제 요청됨");
	}

	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getDeployments(java.lang.String, java.lang.String)
	 */
	public Result getDeployments(String namespace, String serviceType) throws Exception {

		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			ZDBType dbType = ZDBType.getType(serviceType);
			
			if (client != null) {
				DeploymentList deployments = client.inNamespace(namespace).extensions().deployments().list();

				List<Deployment> deploymentList = deployments.getItems();
				List<Deployment> result = new ArrayList<Deployment>();
				
				for (Deployment deployment : deploymentList) {
					if (dbType.getName().equals(deployment.getMetadata().getLabels().get("app"))) {
						result.add(deployment);
					}
				}
				if (result != null) {
					return new Result("", Result.OK).putValue(IResult.DEPLOYMENTS, result);
				}

			}
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", e);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue("deployments", "");
	}
	
	public String toPrettyJson(Object obj) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(obj);
	}

	public class DeploymentConsumer implements Runnable {
		private BlockingQueue<Exchange> queue;
		String workerId;

		public DeploymentConsumer(String id, BlockingQueue<Exchange> queue) {
			this.queue = queue;
			this.workerId = id;
		}

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(1000);
					Exchange exchange = (Exchange) queue.take();

					ZDBEntity property = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
					
					EventType operation = exchange.getProperty(Exchange.OPERTAION, EventType.class);
					String serviceType = exchange.getProperty(Exchange.SERVICE_TYPE, String.class);
					
					if(operation != null) {
						if (ZDBType.MariaDB.name().equalsIgnoreCase(serviceType)) {
							MariaDBInstaller installer = beanFactory.getBean(MariaDBInstaller.class);

							switch (operation) {
							case Install:
								installer.doInstall(exchange);
								break;
							case Delete:
								installer.doUnInstall(exchange);
								break;
							default:
								log.error("Not support.");
								break;
							}
						} else if (ZDBType.Redis.name().equalsIgnoreCase(serviceType)) {
							RedisInstaller installer = beanFactory.getBean(RedisInstaller.class);
							
							switch (operation) {
							case Install:
								installer.doInstall(exchange);
								break;
							case Delete:
								installer.doUnInstall(exchange);
								break;
							default:
								log.error("Not support.");
								break;
							}
						
						} else {
							String msg = "지원하지 않는 서비스 타입 입니다. [" + property.getServiceType() + "]";
							log.error(msg);
						}
					} else {
						log.error("비정상 요청...");
					}

				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}

	@Override
	public Result getPodLog(String namespace, String podName) throws Exception {
		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			
			String app = client.pods().inNamespace(namespace).withName(podName).get().getMetadata().getLabels().get("app");
			String log = "";
			if ("redis".equals(app)) {
				String[] podLog = K8SUtil.getPodLog(namespace, podName);
				
				if (!StringUtils.isEmpty(podLog)) {
					return new Result("", Result.OK).putValue(IResult.POD_LOG, podLog);
				}
			} else if ("mariadb".equals(app)) {
				String errorlogPath = getLogPath(namespace, podName, "log_error");
				if(errorlogPath == null || errorlogPath.isEmpty()) {
					errorlogPath = "/bitnami/mariadb/logs/mysqld_safe.log";
				}
				
				log = new ZDBLogViewer().getTailLog(namespace, podName, "mariadb", 1000, errorlogPath);
				if(log.trim().isEmpty()) {
					errorlogPath = "/bitnami/mariadb/logs/mysqld.log";
					log = new ZDBLogViewer().getTailLog(namespace, podName, "mariadb", 1000, errorlogPath);
				}
				if (!log.isEmpty()) {
					String[] errorLog = log.split("\n");

					return new Result("", Result.OK).putValue(IResult.POD_LOG, errorLog);
				}
			}
			
		} catch (KubernetesClientException e) {
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
		
		return new Result("", Result.ERROR);
	}
	
	@Override
	public Result getSlowLog(String namespace, String podName) throws Exception {
		// MariaDBServiceImpl 에 구현...
		return null;
	}
	
	@Override
	public Result getSlowLogDownload(String namespace, String podName) throws Exception {
		// MariaDBServiceImpl 에 구현...
		return null;
	}
	
	protected String getLogPath(String namespace, String podName, String logType) {
		Map<String, String> systemConfigMap = new HashMap<String, String>();
		try {
			String releaseName = K8SUtil.kubernetesClient().pods().inNamespace(namespace).withName(podName).get().getMetadata().getLabels().get("release");
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
						//slow_query_log_file
						//log_error
						systemConfigMap.put(key, value);
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return systemConfigMap.get(logType);
	}
	
	@PersistenceContext
	protected EntityManager entityManager;
	
	/* kind : 
	 * //	'StatefulSet'
	 * //	'Service'
	 * //	'ReplicaSet'
	 * //	'PodDisruptionBudget'
	 * //	'Pod'
	 * //	'PersistentVolumeClaim'
	 * //	'PersistentVolume'
	 * //	'Node'
	 * //	'Ingress'
	 * //	'Deployment'
	 * //	'DaemonSet'
	 * //	'ConfigMap'
	 * //	'Cluster'
	 * 
	 * 
	 * @see com.zdb.core.service.ZDBRestService#getEvents(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Result getEvents(String namespace, String servceName, String kind, String start, String end, String keyword) throws Exception {
		try {
			CriteriaBuilder builder = entityManager.getCriteriaBuilder();
			CriteriaQuery<EventMetaData> query = builder.createQuery(EventMetaData.class);
			Root<EventMetaData> root = query.from(EventMetaData.class);
			// where절에 들어갈 옵션 목록.
			List<Predicate> predicates = new ArrayList<>();

			// where절에 like문 추가
			// predicates.add( builder.like ( root.get("titleMsg"), "%test%" ) );
			if (kind == null || kind.isEmpty() || kind.equals("-")) {
			} else {
				predicates.add(builder.equal(root.get("kind"), kind));
			}
			if (servceName != null && !servceName.isEmpty() && !servceName.equals("-")) {
				predicates.add(builder.like(root.get("name"), "%" + servceName + "%"));
			}
			if (namespace != null && !namespace.isEmpty() && !namespace.equals("-")) {
				predicates.add(builder.equal(root.get("namespace"), namespace));
			}

			if (keyword != null && !keyword.isEmpty() && !keyword.equals("-")) {
				Predicate message = builder.like(root.get("message"), "%" + keyword + "%");
				Predicate reason = builder.like(root.get("reason"), "%" + keyword + "%");
				Predicate name = builder.like(root.get("name"), "%" + keyword + "%");
				predicates.add(builder.or(message, reason, name));
			}
			if (start != null && !start.isEmpty() && end != null && !end.isEmpty()) {
				Expression<Date> last_timestamp = root.get("lastTimestamp");

				GregorianCalendar gc1 = new GregorianCalendar();
				gc1.setTime(DateUtil.parseDate(start));
				gc1.add(Calendar.HOUR_OF_DAY, -9);
				Date changeStartDate = gc1.getTime();
				
				Calendar gc2 = Calendar.getInstance();
				gc2.setTime(DateUtil.parseDate(end));
				gc2.add(Calendar.HOUR_OF_DAY, -9);
				Date changeEndDate = gc2.getTime();
				
				predicates.add(builder.between(last_timestamp, changeStartDate, changeEndDate));
			}

			// 옵션 목록을 where절에 추가
			query.where(predicates.toArray(new Predicate[] {}));
			
			query.orderBy(builder.desc(root.get("lastTimestamp")));

			// 쿼리를 select문 추가
			query.select(root);

			// 최종적인 쿼리를 만큼
			TypedQuery<EventMetaData> typedQuery = entityManager.createQuery(query);

			// 쿼리 실행 후 결과 확인
			List<EventMetaData> eventMetaDataList = typedQuery.getResultList();
			
			for (EventMetaData eventMetaData : eventMetaDataList) {
				String lastTimestamp = eventMetaData.getLastTimestamp();
				
				Date d = DateUtil.parseDate(lastTimestamp);
				GregorianCalendar gc = new GregorianCalendar();
				gc.setTime(d);
				gc.add(Calendar.HOUR_OF_DAY, 9);
				
				Date changeDate = gc.getTime();
				
				eventMetaData.setLastTimestamp(DateUtil.formatDate(changeDate));
			}
			
			return new Result("", Result.OK).putValue(IResult.SERVICE_EVENTS, eventMetaDataList);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public Result getOperationEvents(String namespace, String servceName, String start, String end, String keyword) throws Exception {

		try {
			CriteriaBuilder builder = entityManager.getCriteriaBuilder();
			CriteriaQuery<RequestEvent> query = builder.createQuery(RequestEvent.class);
			Root<RequestEvent> root = query.from(RequestEvent.class);
			// where절에 들어갈 옵션 목록.
			List<Predicate> predicates = new ArrayList<>();

			// where절에 like문 추가
			// predicates.add( builder.like ( root.get("titleMsg"), "%test%" ) );
	
			if (servceName != null && !servceName.isEmpty() && !servceName.equals("-")) {
				predicates.add(builder.equal(root.get("serviceName"), servceName));
			}
			
			if (namespace != null && !namespace.isEmpty() && !namespace.equals("-")) {
				predicates.add(builder.equal(root.get("namespace"), namespace));
			}

			if (keyword != null && !keyword.isEmpty() && !keyword.equals("-")) {
				predicates.add(builder.like(root.get("resultMessage"), "%" + keyword + "%"));
			}
			if (start != null && !start.isEmpty() && end != null && !end.isEmpty()) {
				Expression<Date> endTime = root.get("endTime");

				GregorianCalendar gc1 = new GregorianCalendar();
				gc1.setTime(DateUtil.parseDate(start));
				gc1.add(Calendar.HOUR_OF_DAY, -9);
				Date changeStartDate = gc1.getTime();
				
				Calendar gc2 = Calendar.getInstance();
				gc2.setTime(DateUtil.parseDate(end));
				gc2.add(Calendar.HOUR_OF_DAY, -9);
				Date changeEndDate = gc2.getTime();
				
				predicates.add(builder.between(endTime, changeStartDate, changeEndDate));
			}

			// 옵션 목록을 where절에 추가
			query.where(predicates.toArray(new Predicate[] {}));
			
			query.orderBy(builder.desc(root.get("endTime")));

			// 쿼리를 select문 추가
			query.select(root);

			// 최종적인 쿼리를 만큼
			TypedQuery<RequestEvent> typedQuery = entityManager.createQuery(query);

			// 쿼리 실행 후 결과 확인
			List<RequestEvent> eventMetaDataList = typedQuery.getResultList();
			
			for (RequestEvent requestEvent : eventMetaDataList) {
				Date endTime = requestEvent.getEndTime();
				
				GregorianCalendar gc = new GregorianCalendar();
				gc.setTime(endTime);
				gc.add(Calendar.HOUR_OF_DAY, 9);
				
				Date changeDate = gc.getTime();
				
				requestEvent.setEndTime(changeDate);
			}
			
			return new Result("", Result.OK).putValue(IResult.OPERATION_EVENTS, eventMetaDataList);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public Result getNamespaces(List<String> namespaceFilter) throws Exception {
		try {
			List<Namespace> namespaces = k8sService.getNamespaces();
			for (Iterator<Namespace> iterator = namespaces.iterator(); iterator.hasNext();) {
				Namespace namespace = (Namespace) iterator.next();
				if (!namespaceFilter.contains(namespace.getMetadata().getName())) {
					iterator.remove();
				}
			}
			if (namespaces != null) {
				Ascending descending = new Ascending();
				Collections.sort(namespaces, descending);

				return new Result("", Result.OK).putValue(IResult.NAMESPACES, namespaces);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.NAMESPACES, "");
	}
	
	class Ascending implements Comparator<Namespace> {
		 
	    @Override
	    public int compare(Namespace o1, Namespace o2) {
	        return o1.getMetadata().getName().compareTo(o2.getMetadata().getName());
	    }
	 
	}

	@Override
	public Result getAllServices() throws Exception {
		// @getService
		return getServicesWithNamespaces(null, false);
	}

	@Override
	public Result getServicesWithNamespaces(String namespaces, boolean detail) throws Exception {
		// @getService
		try {
			List<ServiceOverview> overviews = k8sService.getServiceInNamespaces(namespaces, detail);
			
			if (overviews != null) {
				for (ServiceOverview overview : overviews) {
					setServiceOverViewStatusMessage(overview.getServiceName(), overview);
				}
				return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEWS, overviews);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEWS, "");
	}

	@Override
	public Result getService(String namespace, String serviceType, String serviceName) throws Exception {
		// @getService
		try {
			ServiceOverview overview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
			
			if (overview != null) {
				setServiceOverViewStatusMessage(serviceName, overview);
				
				return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEW, overview);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEW, "");
	}

	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getServicesOfServiceType(java.lang.String)
	 */
	@Override
	public Result getServicesOfServiceType(String serviceType) throws Exception {
		// @getService
		try {
			List<ServiceOverview> overviews = k8sService.getServiceInServiceType(serviceType);
			
			if (overviews != null) {
				for (ServiceOverview overview : overviews) {
					setServiceOverViewStatusMessage(overview.getServiceName(), overview);
				}
				return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEWS, overviews);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEWS, "");
	}

	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getServices(java.lang.String, java.lang.String)
	 */
	@Override
	public Result getServices(String namespace, String serviceType) throws Exception {
		// @getService
		try {
			List<ServiceOverview> overviews = k8sService.getServiceInNamespaceInServiceType(namespace, serviceType);
			
			if (overviews != null) {
				for (ServiceOverview overview : overviews) {
					setServiceOverViewStatusMessage(overview.getServiceName(), overview);
				}
				return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEWS, overviews);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.SERVICEOVERVIEWS, "");
	}

	/**
	 * @param serviceName
	 * @param overview
	 */
	private void setServiceOverViewStatusMessage(String serviceName, ServiceOverview overview) throws Exception {
		if(overview.getStatus() != ZDBStatus.GREEN ) {
			if(overview.getDeploymentStatus().equals("ERROR")) {
				overview.setStatusMessage("생성중 오류가 발생했습니다. 삭제 후 재생성하거나 관리자에게 문의하세요.");
				return;
			}

			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if(releaseMetaData == null) {
				log.warn("{} 는 ZDB 관리 목록에 등록되지 않은 서비스 입니다.", serviceName);
				overview.setStatusMessage("삭제된 서비스 이거나 서비스 불가 상태입니다. 관리자에게 문의하세요.");
				return;
			}
			
			// 2018-10-04 수정
			// StatefulSet 의 실행 인스턴스 수가 0인 경우 오류 메세지.
			List<StatefulSet> statefulSets = overview.getStatefulSets();
			for (StatefulSet statefulSet : statefulSets) {
				Integer replicas = statefulSet.getSpec().getReplicas();
				
				String role = "";
				if ("mariadb".equals(overview.getServiceType())) {
					role = statefulSet.getMetadata().getLabels().get("component");
				} else if ("redis".equals(overview.getServiceType())) {
					role = "master";
				}
				
				if( replicas == null || replicas.intValue() == 0) {
					if("master".equals(role)) {
						role = "Master";
					}else if("slave".equals(role)) {
						role = "Slave";
					}
					overview.setStatusMessage(role + " 실행 인스턴스 개수가 0 입니다.");
					break;
				}
			}
			
			List<Pod> pods = overview.getPods();
			
			boolean isPodReady = true;
			for (Pod pod : pods) {
				if(!K8SUtil.IsReady(pod)) {
					isPodReady = false;
					break;
				}
			}

			if(!isPodReady) {
				// kind, name, reason
				List<EventMetaData> pvcStatus = eventRepository.findByKindAndNameAndReason("PersistentVolumeClaim", "%"+serviceName+"%", "ProvisioningSucceeded");
				
				String statusMessage = "";
				
		        String storageMaster = "-";
		        String storageSlave = "-";
		        String containerMaster = " 생성요청";
		        String containerSlave = "-";
		        String isReadyMaster = " 생성중";
		        String isReadySlave = "-";
		        Set<String> eventMessageSet = new HashSet<>();
		        
				// pvc 상태 
				List<PersistentVolumeClaim> pvcList = k8sService.getPersistentVolumeClaims(overview.getNamespace(), serviceName);
				for (PersistentVolumeClaim persistentVolumeClaim : pvcList) {
					String pvcName = persistentVolumeClaim.getMetadata().getName();
					String role = "";
					if ("mariadb".equals(overview.getServiceType())) {
						role = persistentVolumeClaim.getMetadata().getLabels().get("component");
					} else if ("redis".equals(overview.getServiceType())) {
						role = persistentVolumeClaim.getMetadata().getLabels().get("role");
					}

					if ("master".equals(role)) {
						storageMaster = " 생성중";
					} else {
						storageSlave = " 생성중";
					}
					
					if (!pvcStatus.isEmpty()) {
						for (EventMetaData eventMetaData : pvcStatus) {
							if (pvcName.equals(eventMetaData.getName())) {
								if ("master".equals(role)) {
									storageMaster = " 생성완료";
								} else {
									storageSlave = " 생성완료";
								}
							}
						}
					}
					
					List<EventMetaData> podVolumeStatus = eventRepository.findByKindAndNameAndReason("Pod", "%"+serviceName+"%",  "SuccessfulMountVolume");
					
					if (!podVolumeStatus.isEmpty()) {
						for (EventMetaData eventMetaData : podVolumeStatus) {
							if (eventMetaData.getMessage().indexOf("MountVolume.SetUp succeeded") > -1) {
								if ("master".equals(role)) {
									storageMaster = " OK";
								} else {
									storageSlave = " OK";
								}
							}
						}
					}
				}
				
				List<EventMetaData> podContainerStatus = eventRepository.findByKindAndNameAndReason("Pod", "%"+serviceName+"%",  "Started");
				if (!podContainerStatus.isEmpty()) {
					for (EventMetaData eventMetaData : podContainerStatus) {
						try {
							Pod pod = null;
							for(Pod p : pods) {
								if(p.getMetadata().getName().equals( eventMetaData.getName())) {
									pod = p;
									break;
								}
							}
							if(pod == null) {
								continue;
							}
							String role = "";
							if ("mariadb".equals(overview.getServiceType())) {
								role = pod.getMetadata().getLabels().get("component");
							} else if ("redis".equals(overview.getServiceType())) {
								role = pod.getMetadata().getLabels().get("role");
							}
							
							if (eventMetaData.getMessage().indexOf("Started container") > -1) {
								if ("master".equals(role)) {
									containerMaster = " 동작중";
								} else {
									containerSlave = " 동작중";
								}
							}
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						}
					}
				}
				
				for (Pod pod : pods) {
					
					String role = "";
					if ("mariadb".equals(overview.getServiceType())) {
						role = pod.getMetadata().getLabels().get("component");
					} else if ("redis".equals(overview.getServiceType())) {
						role = pod.getMetadata().getLabels().get("role");
					}
					
					boolean isReady = K8SUtil.IsReady(pod);
					if ("master".equals(role)) {
						isReadyMaster = isReady ? "OK" : "준비중";
					} else {
						isReadySlave = isReady ? "OK" : "준비중";
					}
					 
					if(!isReady) {
						List<PodCondition> conditions = pod.getStatus().getConditions();
						if(!conditions.isEmpty()) {
							String message = conditions.get(0).getMessage();
							if(message != null) {
								eventMessageSet.add(message);
							} else {
								EventMetaData findByKindAndName = eventRepository.findByKindAndName("Pod", pod.getMetadata().getName());
								if(findByKindAndName != null) {
									eventMessageSet.add(findByKindAndName.getMessage());
								}
							}
						}
					}
				}
				
				String storageMsg = "";
				if (pvcList.size() == 2) {
					storageMsg = String.format("‣ 스토리지[M: %s, S: %s]</br>", storageMaster, storageSlave);
				} else if (pvcList.size() == 1) {
					storageMsg = String.format("‣ 스토리지[M: %s]</br>", storageMaster);
				}
				
				if (overview.isClusterEnabled()) {
					statusMessage = String.format("%s‣ 컨테이너[M: %s, S: %s]</br>‣ 상태[M: %s, S: %s]", storageMsg, containerMaster, containerSlave, isReadyMaster, isReadySlave);
					if("OK".equals(isReadyMaster) && "OK".equals(isReadySlave)) {
						overview.setStatus(ZDBStatus.GREEN);
					}
				} else {
					statusMessage = String.format("%s‣ 컨테이너[M: %s]</br>‣ 상태[M: %s]", storageMsg, containerMaster, isReadyMaster);
					if("OK".equals(isReadyMaster)) {
						overview.setStatus(ZDBStatus.GREEN);
					}
				}
				
				if(!eventMessageSet.isEmpty()) {
					statusMessage = statusMessage + "</br>‣ 메세지:";
					int index = 0;
					String lastMessage = "";
					for (Iterator<String> iterator = eventMessageSet.iterator(); iterator.hasNext();) {
						String m = (String) iterator.next();
						//CREATING
						if(!"DEPLOYED".equals(overview.getDeploymentStatus()) 
							&& !"DELETED".equals(overview.getDeploymentStatus())
							&& !"DELETING".equals(overview.getDeploymentStatus())) {
							m = getEventMessage(m); 
						}
						if(!m.trim().isEmpty()) {
							if(m.equals(lastMessage)) {
								lastMessage = "";
								continue;
							}
							lastMessage = m;
							if(index == 0) {
								statusMessage += " " + m;
							} else {
								statusMessage += "</br>&nbsp; &nbsp; &nbsp;&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;" + m;								
							}
						}
						index++;
						
					}
				}
				overview.setStatusMessage(statusMessage);
			} else {
				if(pods.isEmpty()) {
					List<EventMetaData> statefulSetStatus = eventRepository.findByKindAndNameAndReason("StatefulSet", "%"+serviceName+"%", "FailedCreate");
					if(statefulSetStatus != null && !statefulSetStatus.isEmpty()) {
						EventMetaData eventMetaData = statefulSetStatus.get(0);
						overview.setStatusMessage("서비스 생성에 실패 했습니다. 관리자에게 문의하세요.<br> ‣ 에러메세지 : "+eventMetaData.getMessage());
					}
				}
			}
		} else {
			if("mariadb".equals(overview.getServiceType())) {
				
				// 2018-12-05 추가.
				// GREEN 일때...
				// mariadb 환경 설정 값을 변경 후 재시작을 했는지 여부 체크
				// request_event 테이블 
				//        # Pod 가 재시작 되지 않음을 의미.
				//        RequestEvent.UPDATE_CONFIG (환경설정 변경) 의 등록된 시간 > Pod 의 시작 시간) ? "YELLOW" : "GREEN";
				//        메세지 : 환경설정이 변경되었습니다. Pod 재시작이 필요합니다.
				RequestEvent requestEvent = zdbRepository.findByServiceNameAndOperation(overview.getNamespace(), serviceName, RequestEvent.UPDATE_CONFIG);
				if (requestEvent != null) {
					
					
					List<Pod> pods = overview.getPods();
					
					boolean isPodReady = true;
					for (Pod pod : pods) {
						if (!K8SUtil.IsReady(pod)) {
							isPodReady = false;
							break;
						}
					}
					
					if (isPodReady) {
						// RequestEvent.UPDATE_CONFIG
						
						for (Pod pod : pods) {
							PodStatus status = pod.getStatus();
							
							List<PodCondition> conditions = status.getConditions();
							for (PodCondition condition : conditions) {
								
								if ("Ready".equals(condition.getType())) {
									String lastTransitionTime = condition.getLastTransitionTime();
									lastTransitionTime = lastTransitionTime.replace("T", " ").replace("Z", "");
									
									SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
									
									try {
										Date podUptime = sdf.parse(lastTransitionTime);
										long eventTime = requestEvent.getStartTime().getTime();
										
										//RequestEvent.UPDATE_CONFIG "환경설정 변경" 의 등록된 시간 > Pod 의 시작 시간
										if(eventTime - podUptime.getTime() > 0) {
											overview.setStatus(ZDBStatus.YELLOW);
											overview.setStatusMessage("환경 설정이 변경되었습니다.</br>서비스 재시작이 필요합니다.");
										}
										
									} finally {
										// do nothing
									}
								}
								
							}
						}
					}
				}
			}
			
		}
		
		if(overview.isClusterEnabled() && k8sService.isFailoverEnable(overview.getNamespace(), overview.getServiceName())) {
			boolean failoverService = k8sService.isFailoverService(overview.getNamespace(), overview.getServiceName());
			if ("mariadb".equals(overview.getServiceType()) && failoverService) {
				
				String msg = overview.getStatusMessage();
				if(msg != null && msg.length() > 0) {
					msg = msg + "<br>";
				} else {
					msg = "";
				}
				if(overview.getStatus() == ZDBStatus.GREEN) {
					overview.setStatus(ZDBStatus.YELLOW);
				}
				overview.setStatusMessage(msg + "Master L/B가 Slave DB로 Failover 되었습니다.<br>Master DB 는 Failback 실행 후 서비스가 가능합니다.");
			}
		}
	}
	
	private String getEventMessage(String m) {

		if (m.startsWith("pod has unbound PersistentVolumeClaims")) {
			return "서비스 준비중...";
		} else if (m.startsWith("pod has unbound")) {
			return "서비스 준비중...";
		} else if (m.startsWith("Readiness probe failed:")) {
			return "서비스 상태 점검중...";
		} else if (m.startsWith("Liveness probe failed:")) {
			return "서비스 상태 점검중...";
		} else if (m.startsWith("Successfully pulled image")) {
			return "서비스 준비중...";
		} else if (m.startsWith("Started container")) {
			return "컨테이너 생성중...";
		} else if (m.startsWith("MountVolume.SetUp succeeded")) {
			return "볼륨 마운트 완료";
		} else if (m.startsWith("Created container")) {
			return "컨테이너 생성 완료";
		} else if (m.startsWith("Container image")) {
			return "서비스 준비중...";
		} else if (m.startsWith("pulling image")) {
			return "서비스 준비중...";
		} else if (m.startsWith("Back-off restarting failed container")) {
			return "서비스 준비중...";
		} else if (m.startsWith("PersistentVolumeClaim is not bound")) {
			return "스토리지 생성중...";
		} else if (m.startsWith("containers with incomplete status: [init-volume]")) {
			return "컨테이너 초기화중...";
		} else if (m.startsWith("Successfully assigned")) {
			return "컨테이너 초기화중...";
		} 

		return m;
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPods(java.lang.String, java.lang.String)
	 */
	public Result getPods(String namespace, String serviceType, String serviceName) throws Exception {

		try {
			List<Pod> pods = K8SUtil.getPods(namespace, serviceName);
			
			if (pods != null) {
				return new Result("", Result.OK).putValue(IResult.PODS, pods);
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

		return new Result("", Result.OK).putValue(IResult.PODS, "");
	}	
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPod(java.lang.String, java.lang.String, java.lang.String)
	 */
	public Result getPodWithName(String namespace, String serviceType, String serviceName, String podName) throws Exception {

		try {
			Pod pod = K8SUtil.getPodWithName(namespace, serviceName, podName);
				
			if (pod != null) {
				return new Result("", Result.OK).putValue(IResult.POD, pod);
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

		return new Result("", Result.OK).putValue(IResult.POD, "");
	}	
	

	
	public Map<String, String> getNamespaceResource(String namespace) {
		//https://zcp-iam.cloudzcp.io:443/iam/namespace/ns-zdb-02/resource
		
//		RestTemplate restTemplate = getRestTemplate();
//		URI uri = URI.create(iamBaseUrl + "/iam/namespace/"+namespace+"/resource");
//		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
		
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPodMetricsV2(java.lang.String, java.lang.String)
	 */
	public Result getPodMetricsV2(String namespace, String podName) throws Exception {
		Result result = new Result("", Result.OK);

		Deployment deployment = K8SUtil.kubernetesClient().inNamespace("kube-system").extensions().deployments().withName("heapster").get();
		
		try {
			HeapsterMetricUtil metricUtil = new HeapsterMetricUtil();
			
			// heapster 사용 
			if(deployment != null) {
				PodMetrics metrics = metricUtil.getMetricFromHeapster(namespace, podName);
				List<com.zdb.core.vo.Container> containers = metrics.getContainers();
				
				String timestamp = metrics.getTimestamp();
				
				double cupValue = 0;
				double memValue = 0;
				for (com.zdb.core.vo.Container c : containers) {
					String cpu = c.getUsage().getCpu();
					String memory = c.getUsage().getMemory();
					
					Double cpuByM = NumberUtils.cpuByM(cpu);
					cupValue += cpuByM.doubleValue();
					
					Double memoryByMi = NumberUtils.memoryByMi(memory);
					memValue += (memoryByMi.doubleValue());
				}
			
				String cpuStringValue = String.format("{\"metrics\":[{\"timestamp\":\"%s\",\"value\":%d}]}",timestamp, ((int)cupValue));
				String memStringValue = String.format("{\"metrics\":[{\"timestamp\":\"%s\",\"value\":%s}]}",timestamp, ((int)memValue)+"");
				
				Gson gson = new GsonBuilder().create();
				java.util.Map<String, Object> cpuUsage = gson.fromJson(cpuStringValue, java.util.Map.class);
				java.util.Map<String, Object> memoryUsage = gson.fromJson(memStringValue, java.util.Map.class);
				
				result.putValue(IResult.METRICS_CPU_USAGE, cpuUsage.get("metrics"));
				result.putValue(IResult.METRICS_MEM_USAGE, memoryUsage.get("metrics"));
				
			} else {
				// metricserver 사용 
//				[{"timestamp":"2019-03-19T11:26:00Z","value":2}]
				
				PodMetrics metrics = metricUtil.getMetricFromMetricServer(namespace, podName);
				List<com.zdb.core.vo.Container> containers = metrics.getContainers();
				
				String timestamp = metrics.getTimestamp();
				
				double cupValue = 0;
				double memValue = 0;
				for (com.zdb.core.vo.Container c : containers) {
					String cpu = c.getUsage().getCpu();
					String memory = c.getUsage().getMemory();
					
					Double cpuByM = NumberUtils.cpuByM(cpu);
					cupValue += cpuByM.doubleValue();
					
					Double memoryByMi = NumberUtils.memoryByMi(memory);
					memValue += memoryByMi.doubleValue();
				}
			
				String cpuStringValue = String.format("[{\"timestamp\":\"%s\",\"value\":%d}]",timestamp, ((int)cupValue));
				String memStringValue = String.format("[{\"timestamp\":\"%s\",\"value\":%d}]",timestamp, ((int)memValue));
				
				Gson gson = new GsonBuilder().create();
				java.util.Map<String, Object> cpuUsage = gson.fromJson(cpuStringValue, java.util.Map.class);
				java.util.Map<String, Object> memoryUsage = gson.fromJson(memStringValue, java.util.Map.class);
				
				result.putValue(IResult.METRICS_CPU_USAGE, cpuUsage);
				result.putValue(IResult.METRICS_MEM_USAGE, memoryUsage);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return result;
	}
	
	public Result getPodMetrics(String namespace, String podName) throws Exception {
		Result result = new Result("", Result.OK);
		
		HeapsterMetricUtil metricUtil = new HeapsterMetricUtil();
		try {
			Object cpuUsage = metricUtil.getCPUUsage(namespace, podName);
			result.putValue(IResult.METRICS_CPU_USAGE, cpuUsage);
			Object memoryUsage = metricUtil.getMemoryUsage(namespace, podName);
			result.putValue(IResult.METRICS_MEM_USAGE, memoryUsage);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result.putValue(IResult.METRICS_CPU_USAGE, "");
			result.putValue(IResult.METRICS_MEM_USAGE, "");
		}
		
		return result;
	}
	
	private RestTemplate getRestTemplate() {
		HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(1000);
        httpRequestFactory.setConnectTimeout(1000);
        httpRequestFactory.setReadTimeout(1000);
        
        return new RestTemplate(httpRequestFactory);
	}
	
	public Result updateDBVariables(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
		return null;
	}
	
	public Result getPodResources(String namespace, String serviceType, String serviceName) throws Exception {
		try {
			
			ZDBEntity podResources = K8SUtil.getPodResources(namespace, serviceType, serviceName);
			
			if (podResources != null) {
				return new Result("", Result.OK).putValue(IResult.PODS_RESOURCE, podResources);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.PODS_RESOURCE, "");
	}	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#restartService(java.lang.String, java.lang.String)
	 */
	@Override
	public Result restartService(String txId, ZDBType dbType, String namespace, String serviceName) {
		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			ReleaseMetaData findByReleaseName = releaseRepository.findByReleaseName(serviceName);

			if (findByReleaseName == null) {
				return new Result(txId, IResult.ERROR, "설치된 서비스가 존재하지 않습니다.");
			}

			List<Pod> tempPods = k8sService.getPods(namespace, serviceName);
			
			List<Pod> pods = new ArrayList<>();
			for (Pod pod : tempPods) {
				pods.add(pod);
			}
			
			if(pods != null && pods.size() > 1) {
				Collections.sort(pods, new Comparator<Pod>() {
					@Override
					public int compare(Pod o1, Pod o2) {
						if(dbType == ZDBType.MariaDB) {
							String c1 = o1.getMetadata().getLabels().get("component");
							String c2 = o2.getMetadata().getLabels().get("component");
							
							return c2.compareTo(c1);
							
						} else if(dbType == ZDBType.Redis) {
							String c1 = o1.getMetadata().getLabels().get("role");
							String c2 = o2.getMetadata().getLabels().get("role");
							return c2.compareTo(c1);
						}
						
						return 0;
					}
				});
			}
			
			// auto-failover 를 고려해 slave 먼저 삭제 해야 함.

			for (Pod pod : pods) {
				String c1 = pod.getMetadata().getLabels().get("component");
				log.error(c1);
				PodResource<Pod, DoneablePod> podResource = client.inNamespace(namespace).pods().withName(pod.getMetadata().getName());
				if (podResource != null) {
					podResource.delete();
				}
			}

			return new Result(txId, IResult.OK, "서비스 재시작 요청");

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
	}	
	
	@Override
	public Result getAbnormalPersistentVolumeClaims(String namespace, String abnormalType) throws Exception {
		try {
			List<PersistentVolumeClaim> abnormalPvcs = K8SUtil.getAbnormalPersistentVolumeClaims(namespace, abnormalType);
			
			if (abnormalPvcs != null) {
				return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, abnormalPvcs);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, "");
	}

	@Override
	public Result getAbnormalPersistentVolumes() throws Exception {
		try {
			List<PersistentVolume> abnormalPvs = K8SUtil.getAbnormalPersistentVolumes();
			
			if (abnormalPvs != null) {
				return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, abnormalPvs);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, "");
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 */
	@Override
	public Result restartPod(String txId, String namespace, String serviceName, String podName) throws Exception {
		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			PodResource<Pod, DoneablePod> podResource = client.inNamespace(namespace).pods().withName(podName);
			
			if (podResource != null) {
				podResource.delete();
			} else {
				String msg = "삭제 대상 POD이 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);				
			}
			
			return new Result(txId, IResult.OK, "Pod 재시작 요청");
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
	}		
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#restartService(java.lang.String, java.lang.String)
	 */
	@Override
	public Result setNewPassword(String txId, String namespace, String serviceType, String serviceName, String newPassword, String clusterEnabled) throws Exception {
		try {
			String changedPassword = new String();
			
			ZDBType dbType = ZDBType.getType(serviceType);

			String secretName = new String();
			
			switch (dbType) {
			case MariaDB:
				secretName = serviceName + "-mariadb";
				changedPassword = K8SUtil.updateSecrets(namespace, secretName, "mariadb-password", newPassword);
				break;
			case Redis:
				Jedis redisConnection = null;

				redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "master");
				RedisConfiguration.setConfig(redisConnection, "requirepass", newPassword);
				
				if (clusterEnabled.equals("true")) {
					redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "slave");
					RedisConfiguration.setConfig(redisConnection, "requirepass", newPassword);
				}
				
				secretName = k8sService.getSecrets(namespace, serviceName).get(0).getMetadata().getName();
				changedPassword = K8SUtil.updateSecrets(namespace, secretName, "redis-password", newPassword);
				
				break;
			default:
				log.error("Not support.");
				break;
			}			
			
			if (changedPassword == null) {
				return new Result(txId, IResult.ERROR, "비밀번호 변경에 실패했습니다");
			}
			
			Map<String, String> changeResult = new HashMap<String, String>();
			changeResult.put("password", changedPassword);
			
			return new Result(txId, Result.OK, "비밀번호 변경 성공").putValue(IResult.CHANGE_PASSWORD, changeResult);
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
	}			

	private String txId() {
		return UUID.randomUUID().toString();
	}
	
	@Override
	public Result createTag(Tag tag) throws Exception {
		if( tag != null) {
			Tag findTag = tagRepository.findByNamespaceAndReleaseNameAndTag(tag.getNamespace(), tag.getReleaseName(), tag.getTagName());
			if(findTag == null) {
				tagRepository.save(tag);
			} else {
				return new Result("", Result.ERROR, "태그명 중복 ["+tag.getTagName() +"]");
			}
			
			return new Result("", Result.OK, "태그명 : "+tag.getTagName()).putValue(IResult.CREATE_TAG, tag);
		}
		
		return new Result("", Result.ERROR, "태그 등록 오류 - 입력값이 NULL 입니다.");
	}
	
	@Override
	public Result deleteTag(Tag tag) throws Exception {
		if( tag != null) {
			Tag findTag = tagRepository.findByNamespaceAndReleaseNameAndTag(tag.getNamespace(), tag.getReleaseName(), tag.getTagName());
			if(findTag != null) {
				tagRepository.delete(findTag);
				return new Result("", Result.OK, "태그명 : "+tag.getTagName()).putValue(IResult.DELETE_TAG, tag);
			} else {
				return new Result("", Result.ERROR, "이미 삭제 되었거나 존재하지 않는 태그명입니다. [" +tag.getTagName()+ "]");
			}
		}
		
		return new Result("", Result.ERROR);
	}
	
	@Override
	public Result getTagsWithService(String namespace, String serviceName) throws Exception {
		if( namespace != null && serviceName != null) {
			List<Tag> tagList = tagRepository.findByNamespaceAndReleaseName(namespace, serviceName);
			
			return new Result("", Result.OK).putValue(IResult.TAGS, tagList);
		} else {
			return new Result("", Result.ERROR);
		}
	}
	
	@Override
	public Result getTagsWithNamespace(String namespace) throws Exception {
		if (namespace != null) {
			String[] split = namespace.split(",");
			Map<String, Tag> tagMap = new TreeMap<String, Tag>();
			
			for (String ns : split) {
				List<Tag> tagList = tagRepository.findByNamespace(ns.trim());
				for (Tag tag : tagList) {
					ReleaseMetaData findByReleaseName = releaseRepository.findByReleaseName(tag.getReleaseName());
					if(findByReleaseName != null) {
						tagMap.put(tag.getTagName(), tag);
					}
				}
			}

			return new Result("", Result.OK).putValue(IResult.TAGS, tagMap.values());
		} else {
			return new Result("", Result.ERROR);
		}
	}
	
	public Result getTags(List<String> namespaceList) throws Exception {
		Iterable<Tag> tagList = tagRepository.findAll();
		
		for (Iterator<Tag> iterator = tagList.iterator(); iterator.hasNext();) {
			Tag ns = (Tag) iterator.next();
			String namespace = ns.getNamespace();
			if(!namespaceList.contains(namespace)) {
				iterator.remove();
			}
		}
		
		return new Result("", Result.OK).putValue(IResult.TAGS, tagList);
	}
	
	public Result getNodes() throws Exception {

		try {
			NodeList nodes = K8SUtil.getNodes();
			
			if (nodes != null) {
				return new Result("", Result.OK).putValue(IResult.NODES, nodes);
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

		return new Result("", Result.OK).putValue(IResult.NODES, "");
	}		
	
	public Result getNodeCount() throws Exception {

		try {
			int nodeCount = K8SUtil.getNodeCount();
			
			return new Result("", Result.OK).putValue(IResult.NODES, nodeCount);
			
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
	}		
	
	@Override
	public Result getUnusedPersistentVolumeClaims(String namespace) throws Exception {
		try {
			List<PersistentVolumeClaim> unusedPvcs = K8SUtil.getAbnormalPersistentVolumeClaims(namespace, "unused");
			List<HashMap<String, String>> pvcs = new ArrayList<HashMap<String, String>>();
			HashMap<String, String> pvcInfo = new HashMap<String, String>();
			
			if (unusedPvcs != null) {
				for (PersistentVolumeClaim unusedPvc : unusedPvcs) {
					pvcInfo.put("name"   		, unusedPvc.getMetadata().getName());
					pvcInfo.put("release"		, unusedPvc.getMetadata().getLabels().get("release"));
					pvcInfo.put("amount" 		, unusedPvc.getStatus().getCapacity().get("storage").getAmount());
					pvcInfo.put("storageClass" 	, unusedPvc.getSpec().getStorageClassName());
				
					pvcs.add(pvcInfo);
				}
				
				return new Result("", Result.OK).putValue(IResult.UNUSED_PERSISTENTVOLUMECLAIMS, pvcs);
			}
		} catch (KubernetesClientException e) {
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

		return new Result("", Result.OK).putValue(IResult.UNUSED_PERSISTENTVOLUMECLAIMS, "");
	}	
	
	/**
	 * @param service
	 * @return
	 * @throws Exception
	 */
	protected boolean isAvailableScaleUp(final ZDBEntity service) throws Exception {
		PodSpec[] podSpec = service.getPodSpec();
		
		ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
		String masterCpu = masterSpec.getCpu();
		String masterMemory = masterSpec.getMemory();
		
		ZDBEntity podResources = K8SUtil.getPodResources(service.getNamespace(), service.getServiceType(), service.getServiceName());
		PodSpec[] currentPodSpecs = podResources.getPodSpec();
		
		int gapCpu = 0;
		int gapMem = 0;
		
		for (PodSpec currentPodSpec : currentPodSpecs) {
			ResourceSpec[] resourceSpec = currentPodSpec.getResourceSpec();
			String currentCpu = resourceSpec[0].getCpu();
			String currentMemory = resourceSpec[0].getMemory();
			
			try {
				int cCpu = K8SUtil.convertToCpu(currentCpu);
				int cMem = K8SUtil.convertToMemory(currentMemory);
				
				int rCpu = Integer.parseInt(masterCpu);
				int rMem = Integer.parseInt(masterMemory);
			
				gapCpu = gapCpu + rCpu - cCpu;
				gapMem = gapMem + rMem - cMem;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
		
		if(gapCpu == 0) {
			gapCpu = Integer.parseInt(masterCpu);
		}
		if(gapMem == 0) {
			gapMem = Integer.parseInt(masterMemory);
		}
		
		boolean availableResource = NamespaceResourceChecker.isAvailableResource(service.getNamespace(), service.getRequestUserId(), gapMem, gapCpu);
		return availableResource;
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#createPublicService(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	public Result createPublicService(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		try {
			int createPublicService = k8sService.createPublicService(namespace, serviceType, serviceName);
			
			if(createPublicService > 0) {
				return new Result(txId, Result.OK, "Public 서비스 생성 완료");				
			} else {
				return new Result(txId, Result.ERROR, "Public 서비스 생성 오류");
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
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	
	public Result deletePublicService(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		try {
			int createPublicService = k8sService.deletePublicService(namespace, serviceType, serviceName);
			
			if(createPublicService > 0) {
				return new Result(txId, Result.OK, "Public 서비스 삭제 완료");				
			} else {
				return new Result(txId, Result.ERROR, "Public 서비스 삭제 오류");
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
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	
	protected String compareResources(String namespace, String serviceName, ZDBEntity service) {
		StringBuffer sb = new StringBuffer();
		
		try {
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
			
			ZDBEntity podResources = K8SUtil.getPodResources(service.getNamespace(), service.getServiceType(), service.getServiceName());
			PodSpec[] currentPodSpecs = podResources.getPodSpec();
			
			int masterApplyCpu = Integer.parseInt(masterCpu);
			int masterApplyMem = Integer.parseInt(masterMemory);
			
			int slaveApplyCpu = Integer.parseInt(slaveCpu);
			int slaveApplyMem = Integer.parseInt(slaveMemory);

			int currentCpu = 0;
			int currentMemory = 0;
			
			for (PodSpec currentPodSpec : currentPodSpecs) {
				ResourceSpec[] resourceSpec = currentPodSpec.getResourceSpec();
				String cpu = resourceSpec[0].getCpu();
				String memory = resourceSpec[0].getMemory();
				
				String role = currentPodSpec.getPodType();
				
				try {
					currentCpu = K8SUtil.convertToCpu(cpu);
					currentMemory = K8SUtil.convertToMemory(memory);
					
					if("master".equals(role)) {
						sb.append(String.format("CPU : %sm → %sm | Mem : %sMi → %sMi", currentCpu, masterApplyCpu, currentMemory, masterApplyMem));
					}
					
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return sb.toString();
	}
	
	@Override
	public Result createPersistentVolumeClaim(String txId, ZDBPersistenceEntity entity) throws Exception {
		return null;
	}
	
	public Result createZDBConfig(ZDBConfig zdbConfig) {
		try {
			if( zdbConfig != null) {
				List<ZDBConfig> findByConfig = zdbConfigRepository.findByNamespaceAndConfig(zdbConfig.getNamespace(), zdbConfig.getConfig());
				if(findByConfig != null && !findByConfig.isEmpty()) {
					zdbConfigRepository.updateZDBConfig(zdbConfig.getNamespace(), zdbConfig.getConfig(), zdbConfig.getValue());
				} else {
					zdbConfigRepository.save(zdbConfig);
				}
				return new Result("", Result.OK, "설정값이 저장되었습니다.");
			} else {
				return new Result("", Result.ERROR, "설정값이 없습니다.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	public Result getZDBConfig(String namespace) {
		try {
			Iterable<ZDBConfig> findAll = zdbConfigRepository.findByNamespace("global");
			
			if(findAll == null || !findAll.iterator().hasNext()) {
				ZDBConfigService.initZDBConfig(zdbConfigRepository);
			}
			else {
				int count = 0;
				for (ZDBConfig configList : findAll) {
					 count++;
				}
				if(ZDBConfigService.getConfigCount() != count) {
					ZDBConfigService.initZDBConfig(zdbConfigRepository);
				}
			}
			List<ZDBConfig> configList = zdbConfigRepository.findByNamespace(namespace);
			return new Result("", Result.OK).putValue(IResult.ZDBConfig, configList);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	public Result updateZDBConfig(ZDBConfig zdbConfig) {
		try {
			if( zdbConfig != null) {
				ZDBConfigService.updateZDBConfig(zdbConfigRepository, zdbConfig);
				return new Result("", Result.OK, "설정값이 변경되었습니다.");
			} else {
				return new Result("", Result.ERROR, "설정값이 없습니다.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	public Result deleteZDBConfig(ZDBConfig zdbConfig) {
		try {
			if( zdbConfig != null) {
				ZDBConfigService.deleteZDBConfig(zdbConfigRepository, zdbConfig);
				return new Result("", Result.OK, "설정값이 삭제되었습니다.");
			} else {
				return new Result("", Result.ERROR, "설정값이 없습니다.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	public Result updateStorageScale(String txId, String namespace, String serviceType, String serviceName, String pvcSize) throws Exception {
		return null;
	}
	
	public Result serviceOff(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception {
		return null;
	}

	public Result serviceOn(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception {
		return null;
	}

	public Result serviceChangeMasterToSlave(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		return null;
	}

	public Result serviceChangeSlaveToMaster(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		return null;
	}

	public Result serviceFailOverStatus(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		return null;
	}

	public Result slowlogRotation(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		return null;
	}
	
	@Override
	public Result updateAutoFailoverEnable(String txId, String namespace, String serviceType, String serviceName, boolean enable) throws Exception {
		return null;
	}

	@Override
	public Result getAutoFailoverServices(String txId, String namespace) throws Exception {
		return null;
	}
	
	@Override
	public Result getAutoFailoverEnabledServices(String txId, String namespace) throws Exception {
		return null;
	}

	@Override
	public Result getAutoFailoverService(String txId, String namespace, String releaseName) throws Exception {
		return null;
	}
	
	public Result getWorkerPools() throws Exception {
		try {
			List<String> workerPools = k8sService.getWorkerPools();
			return new Result("", Result.OK).putValue(IResult.WORKER_POOLS, workerPools);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}

	public Result getDatabases(String namespace, String serviceType, String serviceName) {
		return null;
	}

	@Override
	public Result getAllServices2() {
		Result result = Result.RESULT_OK(null);
		try {
			List<ReleaseMetaData> releaseMetaDatalist = releaseRepository.findAll();
			List<ServiceOverview> list = new ArrayList<>();
			for(int i = 0; i < releaseMetaDatalist.size();i++) {
				ReleaseMetaData rm = releaseMetaDatalist.get(i);
				ServiceOverview so = new ServiceOverview();
				so.setNamespace(rm.getNamespace());
				so.setServiceName(rm.getReleaseName());
				so.setServiceType(rm.getApp());
				list.add(so);
			}
			result.putValue(IResult.SERVICEOVERVIEWS, list);
			return result;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(null, Result.ERROR, "서비스조회 실패 - "+ e.getMessage(), e);
		}
	}
	
	@Override
	public Result getAlertRules(String txId,String namespaces){
		Result result = Result.RESULT_OK(txId);
		
		try {
			List<AlertingRuleEntity> list = alertService.getAlertRules(namespaces);
			result.putValue(IResult.ALERT_RULES, list);
			return result;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(null, Result.ERROR, "alert rule 조회 실패 - "+ e.getMessage(), e);
		}		
	}

	@Override
	public Result getAlertRule(String txId,String namespace,String alert) {
		Result result = Result.RESULT_OK(txId);
		try {
			AlertingRuleEntity ar = alertService.getAlertRule(namespace,alert);
			result.putValue(IResult.ALERT_RULE, ar);
			return result;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(null, Result.ERROR, "alert rule 조회 실패 - "+ e.getMessage(), e);
		}		
	}

	@Override
	public Result createAlertRule(String txId, AlertingRuleEntity alertingRuleEntity) {
		try {
			if(alertService.getAlertRule(alertingRuleEntity.getNamespace(), alertingRuleEntity.getAlert())!=null) {
				return new Result("", Result.ERROR, "이미 설정된 Rule입니다.");
			}
			alertService.createAlertRule(alertingRuleEntity);
			return new Result("", Result.OK, "설정값이 저장되었습니다.");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	@Override
	public Result updateDefaultAlertRule(String txId, AlertingRuleEntity alertingRuleEntity) {
		try {
			alertService.updateDefaultAlertRule(alertingRuleEntity);
			return new Result("", Result.OK, "설정값이 저장되었습니다.");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}

	@Override
	public Result updateAlertRule(String txId, AlertingRuleEntity alertingRuleEntity) {
		try {
			alertService.deleteAlertRule(alertingRuleEntity);
			alertService.createAlertRule(alertingRuleEntity);
			return new Result("", Result.OK, "설정값이 저장되었습니다.");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}

	@Override
	public Result deleteAlertRule(String txId, AlertingRuleEntity alertingRuleEntity) {
		try {
			alertService.deleteAlertRule(alertingRuleEntity);
			return new Result("", Result.OK, "설정값이 저장되었습니다.");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	@Override
	public Result getStorages(String namespace, String keyword,String app,String storageClassName, String billingType, String phase,String stDate,String edDate) throws Exception {
		try {
			CriteriaBuilder builder = entityManager.getCriteriaBuilder();
			CriteriaQuery<PersistentVolumeClaimEntity> query = builder.createQuery(PersistentVolumeClaimEntity.class);
			Root<PersistentVolumeClaimEntity> root = query.from(PersistentVolumeClaimEntity.class);
			// where절에 들어갈 옵션 목록.
			List<Predicate> predicates = new ArrayList<>();

			// where절에 like문 추가
			if (StringUtils.hasText(namespace)) {
				predicates.add(builder.equal(root.get("namespace"), namespace));
			}
			if (StringUtils.hasText(keyword)) {
				Predicate releaseName = builder.like(root.get("release"), "%" + keyword + "%");
				Predicate name = builder.like(root.get("name"), "%" + keyword + "%");
				predicates.add(builder.or(releaseName, name));
			}
			if (StringUtils.hasText(app)) {
				if(app.equals("-")) {
					predicates.add(builder.isNull(root.get("app")));
				}else {
					predicates.add(builder.like(root.get("app"), "%" + app + "%"));
				}
			}
			if (StringUtils.hasText(storageClassName)) {
				if(storageClassName.equals("-")) {
					predicates.add(builder.isNull(root.get("storageClassName")));
				}else {
					predicates.add(builder.like(root.get("storageClassName"), "%" + storageClassName + "%"));
				}
			}
			if (StringUtils.hasText(billingType)) {
				if(billingType.equals("-")) {
					predicates.add(builder.isNull(root.get("billingType")));
				}else {
					predicates.add(builder.like(root.get("billingType"), "%" + billingType + "%"));
				}
			}
			if (StringUtils.hasText(phase)) {
				if(phase.equals("-")) {
					predicates.add(builder.isNull(root.get("phase")));
				}else {
					predicates.add(builder.like(root.get("phase"), "%" + phase + "%"));
				}
			}
			if (StringUtils.hasText(stDate)) {
				Expression<Date> creationTimestamp = root.get("creationTimestamp");
				GregorianCalendar gc1 = new GregorianCalendar();
				gc1.setTime(DateUtil.parseDate(stDate,"yyyy-MM-dd"));
				//gc1.add(Calendar.HOUR_OF_DAY, -9);				
				predicates.add(builder.greaterThanOrEqualTo(creationTimestamp, gc1.getTime()));
			}
			if (StringUtils.hasText(edDate)) {
				Expression<Date> creationTimestamp = root.get("creationTimestamp");
				GregorianCalendar gc2 = new GregorianCalendar();
				gc2.setTime(DateUtil.parseDate(edDate,"yyyy-MM-dd"));
				//gc2.add(Calendar.HOUR_OF_DAY, 24 -9);	
				gc2.add(Calendar.HOUR_OF_DAY, 24);				
				predicates.add(builder.lessThan(creationTimestamp, gc2.getTime()));
			}
			//zcp-system 필터
			predicates.add(builder.notEqual(root.get("namespace"), "zcp-system"));
			
			// 옵션 목록을 where절에 추가
			query.where(predicates.toArray(new Predicate[] {}));
			query.orderBy(builder.desc(root.get("creationTimestamp")),builder.desc(root.get("name")));
			
			// 쿼리를 select문 추가
			query.select(root);
			// 최종적인 쿼리를 만큼
			TypedQuery<PersistentVolumeClaimEntity> typedQuery = entityManager.createQuery(query);
			// 쿼리 실행 후 결과 확인
			List<PersistentVolumeClaimEntity> resultList = typedQuery.getResultList();
			
			return new Result("", Result.OK).putValue(IResult.STORAGES, resultList);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	@Override
	public Result getStoragesData() throws Exception {
		HashMap<String, List<String>> storageData = new HashMap<>();
		List<String> cols = Arrays.asList("app","billingType");
		List<Predicate> predicates = new ArrayList<>();
		try {
			CriteriaBuilder builder = entityManager.getCriteriaBuilder();
			CriteriaQuery<String> query = builder.createQuery(String.class);
			Root<PersistentVolumeClaimEntity> root = query.from(PersistentVolumeClaimEntity.class);
			for(String col:cols) {
				query.select(root.get(col)).distinct(true);
				predicates.add(builder.isNotNull(root.get(col)));
				query.where(predicates.toArray(new Predicate[] {}));
				
				TypedQuery<String> q = entityManager.createQuery(query);	
				List<String> li = q.getResultList();
				storageData.put(col, li);
			}
			
			return new Result("", Result.OK).putValue(IResult.STORAGES_DATA, storageData);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	
	
}
