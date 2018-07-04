package com.zdb.core.service;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.DefaultExchange;
import com.zdb.core.domain.EventMetaData;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesOperations;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBStatus;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.repository.TagRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.redis.RedisConfiguration;
import com.zdb.redis.RedisConnection;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentList;
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
	BeanFactory beanFactory;
	
	@Autowired
	protected K8SService k8sService;
	
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
	public Result createDeployment(String txId, ZDBEntity service) throws Exception {
//		# id, 
//		#uid, 
//		#first_timestamp, 
//		#last_timestamp, 
//		#kind, 
//		#name, 
//		#namespace, 
//		#reason, 
//		#message, 
//		#release_name
//		#metadata, 
//
//		#30b17e5d7-8322-41cf-9970-f59c25d43caf', 
//		#'15d38e03-754e-11e8-9249-62f12bbc403a', 
//		#'2018-06-21 12:24:46', 
//		#'2018-06-21 12:24:46', 
//		#'Deployment', 
//		#'zdb-redis-demo-session-slave', 
//		#'zdb-redis', 
//		#'ScalingReplicaSet', 
//		#'Scaled up replica set zdb-redis-demo-session-slave-786c494c5b to 1', 
//		#NULL
//		#'{\"apiVersion\":\"v1\",\"count\":1,\"firstTimestamp\":\"2018-06-21T12:24:46Z\",\"involvedObject\":{\"apiVersion\":\"extensions\",\"kind\":\"Deployment\",\"name\":\"zdb-redis-demo-session-slave\",\"namespace\"
		
		RequestEvent event = new RequestEvent();

		event.setTxId(txId);
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setServiceType(service.getServiceType());
		event.setNamespace(service.getNamespace());
		event.setServiceName(service.getServiceName());
		event.setEventType(EventType.Deployment.name());
		
		Result requestCheck = isDeploymentAvaliable();
		if(!requestCheck.isOK()) {
			event.setStatusMessage("서비스 생성 요청 한도 초과");
			event.setResultMessage(requestCheck.getMessage());
			event.setEndTIme(new Date(System.currentTimeMillis()));
			
			log.warn(toPrettyJson(event));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
			
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
			exchange.setProperty(Exchange.OPERTAION, EventType.Deployment);
			
			long s = System.currentTimeMillis();
			// 서비스 명 중복 체크
			ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
			if (releaseMeta != null/*K8SUtil.isServiceExist(service.getNamespace(), service.getServiceName())*/) {
				String msg = "사용중인 서비스 명입니다.[" + service.getServiceName() + "]";
				log.error(msg);

				return new Result(txId, IResult.ERROR, msg);
			}
			log.error("서비스 명 중복 체크 : " + (System.currentTimeMillis() - s));
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
			releaseMeta.setPublicEnabled(service.isClusterEnabled());
			releaseMeta.setPurpose(service.getPurpose());

			log.info(">>> install request : "+new Gson().toJson(releaseMeta));

			releaseRepository.save(releaseMeta);

			// install request
			deploymentRequest(exchange);
//			long s = System.currentTimeMillis();
//			try {
//				while (true) {
//					Thread.sleep(500);
//					if((System.currentTimeMillis() - s) > 3 * 1000) {
//						break;
//					}
//					try {
//						List<Pod> pods = k8sService.getPods(service.getNamespace(), service.getServiceName());
//						if (pods != null && !pods.isEmpty()) {
//							break;
//						}
//					} catch (Exception e) {
//					}
//					
//				}
//
//			} catch (Exception e) {
//			} finally {
//				event.setResultMessage("[" + service.getServiceName() + "] 설치 요청 성공.");
//				event.setEndTIme(new Date(System.currentTimeMillis()));
//
//				log.info(toPrettyJson(event));
//				ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
//			}

			return new Result(txId, IResult.OK, "[" + service.getServiceName() + "] 설치 요청 성공.");
		}
		
	}
	
	@Override
	public synchronized Result deleteServiceInstance(String txId, String namespace, String serviceType, String serviceName) throws Exception {

		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();

		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setNamespace(namespace);
		event.setEventType(EventType.Delete.name());
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setOpertaion(KubernetesOperations.DELETE_SERVICE_INSTANCE);
		
		
		Result requestCheck = isDeploymentAvaliable();
		if(!requestCheck.isOK()) {
			event.setStatusMessage("서비스 생성 요청 한도 초과");
			event.setResultMessage(requestCheck.getMessage());
			event.setEndTIme(new Date(System.currentTimeMillis()));
			
			log.warn(toPrettyJson(event));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
			
			return requestCheck;
		} else {
			
			Exchange exchange = new DefaultExchange();
			exchange.setProperty(Exchange.TXID, txId);
			exchange.setProperty(Exchange.NAMESPACE, namespace);
			exchange.setProperty(Exchange.SERVICE_NAME, serviceName);
			exchange.setProperty(Exchange.SERVICE_TYPE, serviceType);
			exchange.setProperty(Exchange.CHART_URL, chartUrl);
			exchange.setProperty(Exchange.META_REPOSITORY, zdbRepository);
			exchange.setProperty(Exchange.OPERTAION, EventType.Delete);
			
		    unInstallRequest(exchange);

			event.setResultMessage("[" + serviceName + "] 삭제 요청 성공.");
			event.setEndTIme(new Date(System.currentTimeMillis()));
			
			log.info(toPrettyJson(event));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
			
			return new Result(txId, IResult.OK, "[" + serviceName + "] 삭제 요청 성공.");
		}
		
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue("deployments", "");
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#createPersistentVolumeClaim(java.lang.String, java.lang.String, java.lang.String, com.zdb.core.domain.PersistenceSpec)
	 */
//	public Result createPersistentVolumeClaim(String txId, String namespace, String serviceName, String pvcName) throws Exception {
//
//		String serviceType = "";
//		if(this instanceof MariaDBServiceImpl) {
//			serviceType = ZDBType.MariaDB.getName();
//		} else if(this instanceof RedisServiceImpl) {
//			serviceType = ZDBType.Redis.getName();
//		} 
//
//		RequestEvent event = new RequestEvent();
//
//		event.setTxId(txId);
//		event.setNamespace(namespace);
//		event.setServiceName(serviceName == null ? "" : serviceName);
//		event.setServiceType(serviceType);
//		event.setEventType(EventType.CreatePersistentVolumeClaim.name());
//		event.setOpertaion(KubernetesOperations.CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION);
//		event.setStartTime(new Date(System.currentTimeMillis()));
//		
////		final CountDownLatch closeLatch = new CountDownLatch(1);
//
//		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
//
//		Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim> withName = client.inNamespace(namespace).persistentVolumeClaims().withName(pvcName);
//
//		if (withName == null || withName.get() == null) {
//			// metaRepository, closeLatch, KubernetesOperations.CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION, txId, namespace, serviceName, pvcName
//			Exchange exchange = new DefaultExchange();
//			exchange.setProperty(Exchange.TXID, txId);
//			exchange.setProperty(Exchange.NAMESPACE, namespace);
//			exchange.setProperty(Exchange.SERVICE_NAME, serviceName == null ? "" : serviceName);
//			exchange.setProperty(Exchange.CHART_URL, chartUrl);
//			exchange.setProperty(Exchange.META_REPOSITORY, metaRepository);
//			
//			//////////////////////////////////////////////////////////////////////////////
//			
//			
//			createPVCRequest(exchange);
//
//			event.setResultMessage("[" + pvcName + "] 설치 요청 성공.");
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//			
//			log.info(toPrettyJson(event));
//			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//			
//			return new Result(txId, IResult.OK, "[" + pvcName + "] 설치 요청 성공.");
//			
//			/////////////////////////////////////////////////////////////////////////////
//		} else {
//			event.setStatus(IResult.ERROR);
//			event.setEndTIme(new Date(System.currentTimeMillis()));
//			log.info("!!!"+new Gson().toJson(event));
//			
//			return new Result(txId, IResult.ERROR, "exist PVC.").putValue(IResult.PERSISTENTVOLUMECLAIM, withName.get());
//		}
//
//	}
	
	/**
	 * @param txId
	 * @param namespace
	 * @param pvcName
	 * @param billingType
	 * @param accessMode
	 * @param limitStorage
	 * @param requestStorage
	 * @return
	 * @throws Exception
	 */
//	protected synchronized Result doCreatePersistentVolumeClaimsService(String txId, String namespace, String pvcName, String billingType, String accessMode, int size) throws Exception {
//
//		try {
//			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
//			PersistentVolumeClaim pvc = null;
//
//			Map<String, String> labels = new HashMap<>();
//			// labels.put("billingType", "hourly");
//			labels.put("billingType", billingType);
//
//			PersistentVolumeClaimSpec pvcSpec = new PersistentVolumeClaimSpec();
//
//			ResourceRequirements rr = new ResourceRequirements();
//
//			// Map<String, Quantity> mp = new HashMap<String, Quantity>();
//			// mp.put("storage", new Quantity(limitStorage + "Gi"));
//			// rr.setLimits(mp);
//
//			Map<String, Quantity> req = new HashMap<String, Quantity>();
//			req.put("storage", new Quantity(size + "Gi"));
//			rr.setRequests(req);
//
//			pvcSpec.setResources(rr);
//			// pvcSpec.setVolumeName("maraiadb0003");
//			List<String> access = new ArrayList<String>();
//			// access.add("ReadWriteMany");
//			access.add(accessMode);
//
//			pvcSpec.setAccessModes(access);
//
//			Map<String, String> annotations = new HashMap<>();
//			annotations.put("volume.beta.kubernetes.io/storage-class", "ibmc-file-silver");
//			PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder().withNewMetadata().withName(pvcName).withAnnotations(annotations).withLabels(labels).endMetadata().withSpec(pvcSpec).build();
//
//			pvc = client.inNamespace(namespace).persistentVolumeClaims().create(pvcCreating);
//			log.info("---------------------pvc create finish.............");
//			return new Result(txId, IResult.OK, "Create PVC").putValue(IResult.PERSISTENTVOLUMECLAIM, pvc);
//		} catch (Exception e) {
//			return new Result(txId, IResult.ERROR, "Create PVC", e);
//		}
//	}
	/**
	 * @param eventType
	 * @param serviceName
	 * @return
	 */
	public RequestEvent getRequestEvent(EventType eventType, String serviceName) {
		Iterable<RequestEvent> findAll = zdbRepository.findAll();

		for (RequestEvent event : findAll) {
			if (event.getEventType() == eventType.name() && event.getServiceName().equals(serviceName)) {
				return event;
			}
		}

		return null;
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
					
//					exchange.setProperty(Exchange.TXID, txId);
//					exchange.setProperty(Exchange.NAMESPACE, namespace);
//					exchange.setProperty(Exchange.SERVICE_NAME, serviceName);
//					exchange.setProperty(Exchange.CHART_URL, chartUrl);
//					exchange.setProperty(Exchange.META_REPOSITORY, metaRepository);
//					exchange.setProperty(Exchange.OPERTAION, EventType.Delete.name());
					
					if(operation != null) {
						if (ZDBType.MariaDB.name().equalsIgnoreCase(serviceType)) {
							MariaDBInstaller installer = beanFactory.getBean(MariaDBInstaller.class);

							switch (operation) {
							case Deployment:
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
							case Deployment:
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
							
							String txId = exchange.getProperty(Exchange.TXID, String.class);
							ZDBRepository metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);
							
							String msg = "지원하지 않는 서비스 타입 입니다. [" + property.getServiceType() + "]";
							
							RequestEvent event = metaRepository.findByTxId(txId);
							if(event != null) {
								event.setStatus(IResult.ERROR);
								event.setResultMessage(msg);
								event.setStatusMessage("생성오류");
							} else {
								
							}
							ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
							
							log.warn(msg);
						}
					} else {
						log.error("비정상 요청...");
					}

				} catch (InterruptedException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}

	@Override
	public Result getPodLog(String namespace, String podName) throws Exception {
		try {
			String[] podLog = K8SUtil.getPodLog(namespace, podName);
			
			if (!StringUtils.isEmpty(podLog)) {
				return new Result("", Result.OK).putValue(IResult.POD_LOG, podLog);
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
				predicates.add(builder.or(message, reason));
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
				return new Result("", Result.OK).putValue(IResult.NAMESPACES, namespaces);
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

		return new Result("", Result.OK).putValue(IResult.NAMESPACES, "");
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
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
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if(releaseMetaData == null) {
				log.warn("{} 는 release_meta_data 테이블에 등록되지 않은 서비스 입니다.", serviceName);
				return;
			}
			long createTime = releaseMetaData.getCreateTime().getTime();
			
			// 15분이내 생성된 서버 1529453558000
			if((System.currentTimeMillis() - createTime) < 150 * 60 * 1000) {
				// kind, name, reason
//				'PersistentVolumeClaim', 'data-zdb-116-mariadb-master-0', '2018-06-01T09:56:59Z', '2018-06-01T09:56:59Z', 'Successfully provisioned volume pvc-f941d085-6581-11e8-bddb-ea6741069087', 'ProvisioningSucceeded'
//				'Pod', 'zdb-116-mariadb-master-0', '2018-06-01T09:58:53Z', '2018-06-01T09:58:53Z', 'MountVolume.SetUp succeeded for volume \"pvc-f941d085-6581-11e8-bddb-ea6741069087\" ', 'SuccessfulMountVolume'
//				'Pod', 'zdb-116-mariadb-master-0', '2018-06-01T09:58:59Z', '2018-06-01T09:58:59Z', 'Started container', 'Startedq
				List<EventMetaData> pvcStatus = eventRepository.findByKindAndNameAndReason("PersistentVolumeClaim", "%"+serviceName+"%", "ProvisioningSucceeded");
				
				// Storage status : 생성중, 생성완료, mount
				// Container status: start, ready 
				
//				String statusMessage = String.format("Storage[master:%s, slave:%s], Container[master:%s, slave:%s]", "", "", "", "");
//				if(!overview.isClusterEnabled()) {
//					statusMessage = String.format("Storage[master:%s], Container[master:%s]", "", "");
//				}
				String statusMessage = "Storage[master:%s, slave:%s], Container[master:%s, slave:%s]";
				if(!overview.isClusterEnabled()) {
					statusMessage = "Storage[master:%s], Container[master:%s]";
				}
				
		        String storageMaster = "-";
		        String storageSlave = "-";
		        String containerMasger = "-";
		        String containerSlave = "-";
		        String isReadyMaster = "-";
		        String isReadySlave = "-";
				
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
									storageMaster = " 마운트OK";
								} else {
									storageSlave = " 마운트OK";
								}
							}
						}
					}
				}
				
				List<EventMetaData> podContainerStatus = eventRepository.findByKindAndNameAndReason("Pod", "%"+serviceName+"%",  "Started");
				if (!podContainerStatus.isEmpty()) {
					for (EventMetaData eventMetaData : podContainerStatus) {
						try {
							Pod pod = K8SUtil.getPodWithName(eventMetaData.getNamespace(), serviceName, eventMetaData.getName());
							String role = "";
							if ("mariadb".equals(overview.getServiceType())) {
								role = pod.getMetadata().getLabels().get("component");
							} else if ("redis".equals(overview.getServiceType())) {
								role = pod.getMetadata().getLabels().get("role");
							}
							
							log.info(eventMetaData.getMetadata());
							if (eventMetaData.getMessage().indexOf("Started container") > -1) {
								if ("master".equals(role)) {
									containerMasger = " 동작중";
								} else {
									containerSlave = " 동작중";
								}
							}
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						}
					}
				}
				List<Pod> pods = overview.getPods();
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
				}
				
				String storageMsg = "";
				if (pvcList.size() == 2) {
					storageMsg = String.format("‣스토리지[M: %s, S: %s]</br>", storageMaster, storageSlave);
				} else if (pvcList.size() == 1) {
					storageMsg = String.format("‣스토리지[M: %s]</br>", storageMaster);
				}
				
				if (overview.isClusterEnabled()) {
					statusMessage = String.format("%s‣컨테이너[M: %s, S: %s]</br>‣상태[M: %s, S: %s]", storageMsg, containerMasger, containerSlave,
							isReadyMaster, isReadySlave);
				} else {
					statusMessage = String.format("%s‣컨테이너[M: %s]</br>‣상태[M: %s]", storageMsg, containerMasger, isReadyMaster);
				}
				overview.setStatusMessage(statusMessage);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPods(java.lang.String, java.lang.String)
	 */
	public Result getPods(String namespace, String serviceType, String serviceName) throws Exception {

		try {
			List<Pod> pods = K8SUtil.getPodList(namespace, serviceName);
			
			if (pods != null) {
				return new Result("", Result.OK).putValue(IResult.PODS, pods);
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue(IResult.POD, "");
	}	
	
	public Result getPodMetrics(String namespace, String podName) throws Exception {
		List<Service> services = K8SUtil.getServicesWithNamespace("kube-system");

		for (Service service : services) {
			if (service.getMetadata().getName().equals("heapster")) {

				String portStr = null;
				String ipStr = null;

				if ("loadbalancer".equals(service.getSpec().getType().toLowerCase())) {
					List<ServicePort> ports = service.getSpec().getPorts();
					for (ServicePort port : ports) {
						portStr = Integer.toString(port.getPort());
						break;
					}

					if (portStr == null) {
						throw new Exception("unknown Service Port");
					}

					List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
					if (ingress != null && ingress.size() > 0) {
						ipStr = ingress.get(0).getIp();
					} else {
						throw new Exception("unknown Service IP Address");
					}
				} else if ("clusterip".equals(service.getSpec().getType().toLowerCase())) {
					List<ServicePort> ports = service.getSpec().getPorts();
					for (ServicePort port : ports) {
						portStr = Integer.toString(port.getPort());
						break;
					}
					if (portStr == null) {
						throw new Exception("unknown Service Port");
					}
					ipStr = service.getSpec().getClusterIP();
				} else {
					log.warn("no cluster ip.");
				}

				if (ipStr == null || portStr == null) {
					throw new Exception("unknown Service IP or Port");
				}

				// http://169.56.71.110/api/v1/model/namespaces/zdb-maria/pod-list/maria-test777-mariadb-0/metrics/cpu-usage

				String metricUrl = String.format("http://%s:%s/api/v1/model/namespaces/%s/pod-list/%s/metrics", ipStr, portStr, namespace, podName);

				Result result = new Result("", Result.OK);

				RestTemplate restTemplate = new RestTemplate();
				{
					URI uri = URI.create(metricUrl + "/cpu-usage");
					Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);

					result.putValue(IResult.METRICS_CPU_USAGE, ((Map)((List)responseMap.get("items")).get(0)).get("metrics"));
				}
				{
					URI uri = URI.create(metricUrl + "/memory-usage");
					Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);

					result.putValue(IResult.METRICS_MEM_USAGE, ((Map)((List)responseMap.get("items")).get(0)).get("metrics"));
				}

				return result;
			}
		}

		return new Result("", Result.ERROR);
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
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

		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setEventType(EventType.Restart.name());
		event.setOpertaion("Restart");
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));

		try {
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

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
				ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);

				return new Result(txId, IResult.ERROR, msg);
			}
			
			List<Pod> pods = K8SUtil.getPods(namespace, serviceName);
			
			for (Pod pod : pods) {
				PodResource<Pod, DoneablePod> podResource = client.inNamespace(namespace).pods().withName(pod.getMetadata().getName());
				if (podResource != null) {
					podResource.delete();
				}
			}
			
			return new Result(txId, IResult.OK, "Pod 재시작 요청.");


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
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#restartService(java.lang.String, java.lang.String)
	 */
	@Override
	public Result reStartPod(String txId, String namespace, String serviceName, String podName) throws Exception {

		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setEventType(EventType.RestartPod.name());
		event.setOpertaion("RestartPod");
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));

		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			PodResource<Pod, DoneablePod> podResource = client.inNamespace(namespace).pods().withName(podName);
			
			if (podResource != null) {
				podResource.delete();
			} else {
				String msg = "삭제 대상 POD이 존재하지 않습니다.";
				event.setResultMessage(msg);
				event.setStatusMessage("POD 삭제 실패");
				event.setStatus(IResult.ERROR);
				event.setEndTIme(new Date(System.currentTimeMillis()));
				ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);

				return new Result(txId, IResult.ERROR, msg);				
			}
			
			return new Result(txId, IResult.OK, "Pod 재시작 요청.");
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
		}
	}		
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#restartService(java.lang.String, java.lang.String)
	 */
	@Override
	public Result setNewPassword(String txId, String namespace, String serviceType, String serviceName, String newPassword) throws Exception {

		// 서비스 요청 정보 기록
		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setEventType(EventType.UpdatePassword.name());
		event.setOpertaion("UpdatePassword");
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));

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
				
				redisConnection = RedisConnection.getRedisConnection(namespace, serviceName, "slave");
				RedisConfiguration.setConfig(redisConnection, "requirepass", newPassword);

				secretName = serviceName;
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
			
			return new Result("", Result.OK).putValue(IResult.CHANGE_PASSWORD, changeResult);
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
		}
	}			

	@Override
	public Result createTag(Tag tag) throws Exception {
		if( tag != null) {
			Tag findTag = tagRepository.findByNamespaceAndReleaseNameAndTag(tag.getNamespace(), tag.getReleaseName(), tag.getTagName());
			if(findTag == null) {
				tagRepository.save(tag);
			} else {
				return new Result("", Result.ERROR, "Duplicate tag name.");
			}
			
			return new Result("", Result.OK).putValue(IResult.CREATE_TAG, tag);
		}
		
		return new Result("", Result.ERROR, "tag is null.");
	}
	
	@Override
	public Result deleteTag(Tag tag) throws Exception {
		if( tag != null) {
			Tag findTag = tagRepository.findByNamespaceAndReleaseNameAndTag(tag.getNamespace(), tag.getReleaseName(), tag.getTagName());
			if(findTag != null) {
				tagRepository.delete(findTag);
				return new Result("", Result.OK).putValue(IResult.DELETE_TAG, tag);
			} else {
				return new Result("", Result.ERROR, "not exist tag. [" +tag.getTagName()+ "]");
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
		
		for (Iterator iterator = tagList.iterator(); iterator.hasNext();) {
			String ns = (String) iterator.next();
			if(!namespaceList.contains(ns)) {
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
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
				return new Result("", Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue(IResult.UNUSED_PERSISTENTVOLUMECLAIMS, "");
	}	
	
}
