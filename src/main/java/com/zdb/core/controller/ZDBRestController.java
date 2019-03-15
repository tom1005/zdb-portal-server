package com.zdb.core.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.zdb.core.domain.EventMetaData;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.UserInfo;
import com.zdb.core.domain.UserNamespaces;
import com.zdb.core.domain.ZDBConfig;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.domain.ZDBPersistenceEntity;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.UserNamespaceRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.service.K8SService;
import com.zdb.core.service.MariaDBServiceImpl;
import com.zdb.core.service.RedisServiceImpl;
import com.zdb.core.service.ZDBRestService;
import com.zdb.core.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * RestServiceController class
 * 
 * @author 06919
 *
 */
@RefreshScope
@RestController
@Slf4j
@RequestMapping(value = "/api/v1")
public class ZDBRestController {

	@Autowired
	private HttpServletRequest request;
	
	@Autowired
	@Qualifier("mariadbService")
	private ZDBRestService mariadbService;

	@Autowired
	@Qualifier("redisService")
	private ZDBRestService redisService;
	
	@Autowired
	@Qualifier("commonService")
	private ZDBRestService commonService;
	
	@Autowired
	protected ZDBRepository zdbRepository;

	@Autowired
	protected K8SService k8sService;
	
	@Autowired
	protected ZDBReleaseRepository releaseRepository;
	
	@Autowired
	private UserNamespaceRepository userNamespaceRepo;
	
	@Autowired
	private EventRepository eventRepo;

	// @Autowired
	// @Qualifier("postgresqlService")
	// private ZDBRestService postgresqlService;
	//
	// @Autowired
	// @Qualifier("rabbitmqService")
	// private ZDBRestService rabbitmqService;
	//
	// @Autowired
	// @Qualifier("mongodbService")
	// private ZDBRestService mongodbService;

	/**
	 * Retrieve All Persistent Volume Claims
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/pvcs", method = RequestMethod.GET)
	public ResponseEntity<String> getPersistentVolumeClaims(@PathVariable("namespace") final String namespace) {
		try {
			Result result = mariadbService.getPersistentVolumeClaims(namespace);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "PersistentVolumeClaim 목록 조회 에러 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * getting a Persistent Volume Claim
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/pvcs/{pvcname}", method = RequestMethod.GET)
	public ResponseEntity<String> getPersistentVolumeClaim(@PathVariable("namespace") final String namespace, @PathVariable("pvcname") final String pvcName) {
		try {
			Result result = mariadbService.getPersistentVolumeClaim(namespace, pvcName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "PersistentVolumeClaim 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Retrieve All services
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/deployments", method = RequestMethod.GET)
	public ResponseEntity<String> getDeployments(@PathVariable("namespace") final String namespace, @PathVariable("serviceType") String serviceType) {
		try {
			Result result = mariadbService.getDeployments(namespace, serviceType);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "서비스 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	/**
	 * Retrieve a single service
	 * 
	 * @param id
	 *            service ID
	 * @return ResponseEntity<Service>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/deployments/{serviceName}", method = RequestMethod.GET)
	public ResponseEntity<String> getDeployment(@PathVariable("serviceType") String serviceType, @PathVariable("namespace") final String namespace, @PathVariable("serviceName") final String serviceName) {

		try {
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);

			Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.getDeployment(namespace, serviceName);
				break;
			case Redis:
				result = redisService.getDeployment(namespace, serviceName);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "서비스 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}

	}

	/**
	 * Create a service
	 * 
	 * @param service
	 *            object to be created
	 * @param ucBuilder
	 *            UriComponentBuilder
	 * @return ResponseEntity<Void>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service", method = RequestMethod.POST)
	public ResponseEntity<String> createDeployment(@PathVariable("serviceType") String serviceType, @PathVariable("namespace") final String namespace, @RequestBody final ZDBEntity entity,
			final UriComponentsBuilder ucBuilder) {

		String txId = txId();
		RequestEvent event = new RequestEvent();
		StringBuffer history = new StringBuffer();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(entity.getServiceType());
			event.setNamespace(entity.getNamespace());
			event.setServiceName(entity.getServiceName());
			event.setOperation(RequestEvent.CREATE);
			event.setUserId(userInfo.getUserName());
			
			history.append("Namespace").append(":").append(entity.getNamespace()).append("\n");
			history.append("ServiceName").append(":").append(entity.getServiceName()).append("\n");
			history.append("ServiceType").append(":").append(entity.getServiceType()).append("\n");
			history.append("Version").append(":").append(entity.getVersion()).append("\n");
			history.append("BackupEnabled").append(":").append(entity.isBackupEnabled()).append("\n");
			history.append("ClusterEnabled").append(":").append(entity.isClusterEnabled()).append("\n");
			history.append("CPU").append(":").append(entity.getPodSpec()[0].getResourceSpec()[0].getCpu()+"m").append("\n");
			history.append("Memory").append(":").append(entity.getPodSpec()[0].getResourceSpec()[0].getMemory()+"Mi").append("\n");

			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);
			log.info("{}, {}, {}", userInfo.getUserId(), userInfo.getUserName(), userInfo.getAccessRole());

			com.zdb.core.domain.Result result = null;
			
			if(userInfo.getUserId() != null && !userInfo.getUserId().isEmpty()) {
				entity.setRequestUserId(userInfo.getUserId());
			}
			
			if(entity.getRequestUserId() == null || entity.getRequestUserId().isEmpty()) {
				result = new Result(txId, IResult.ERROR, "서비스 생성 오류!");
				
				event.setStatus(result.getCode());
				event.setResultMessage("사용자 정보가 유효하지 않습니다.");
				
				return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
			}
			
			switch (dbType) {
			case MariaDB:
				history.append("StorageClass").append(":").append(entity.getPersistenceSpec()[0].getStorageClass()).append("\n");
				history.append("Storage Size").append(":").append(entity.getPersistenceSpec()[0].getSize()).append("\n");
				history.append("Database").append(":").append(entity.getMariaDBConfig().getMariadbDatabase()).append("\n");
				history.append("CharacterSet").append(":").append(entity.getCharacterSet()).append("");
				event.setHistory(history.toString());
				result = mariadbService.createDeployment(txId, entity, userInfo);
				break;
			case Redis:
				history.append("Purpose").append(":").append(entity.getPurpose()).append("");
				event.setHistory(history.toString());
				result = redisService.createDeployment(txId, entity, userInfo);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "서비스 생성 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(e.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}

	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/{serviceName}/public-service", method = RequestMethod.POST)
	public ResponseEntity<String> createPublicService(
			  @PathVariable("serviceType") final String serviceType
			, @PathVariable("namespace") final String namespace
			, @PathVariable("serviceName") final String serviceName) {

		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.CREATE_PUBLIC_SVC);
			event.setUserId(userInfo.getUserName());
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			log.info("{}, {}, {}", userInfo.getUserId(), userInfo.getUserName(), userInfo.getAccessRole());

			com.zdb.core.domain.Result result = null;
			
			result = commonService.createPublicService(txId, namespace, serviceType, serviceName);

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.CREATE_PUBLIC_SVC +" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(e.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/{serviceName}/public-service", method = RequestMethod.DELETE)
	public ResponseEntity<String> deletePublicService(
			  @PathVariable("serviceType") final String serviceType
			, @PathVariable("namespace") final String namespace
			, @PathVariable("serviceName") final String serviceName) {

		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.DELETE_PUBLIC_SVC);
			event.setUserId(userInfo.getUserName());
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			log.info("{}, {}, {}", userInfo.getUserId(), userInfo.getUserName(), userInfo.getAccessRole());

			com.zdb.core.domain.Result result = null;
			
			result = commonService.deletePublicService(txId, namespace, serviceType, serviceName);

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.DELETE_PUBLIC_SVC +" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(e.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}

	/**
	 * Update a service
	 * 
	 * @param id
	 *            service ID to be updated
	 * @param service
	 *            source Service object to be updated
	 * @return ResponseEntity<Service>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/scaleUp", method = RequestMethod.PUT)
	public ResponseEntity<String> updateScaleUp(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			@RequestBody final ZDBEntity service, final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.SCALE_UP);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
			service.setRequestUserId(userInfo.getUserId());

			switch (dbType) {
			case MariaDB:
				result = mariadbService.updateScale(txId, service);
				break;
			case Redis:
				result = redisService.updateScale(txId, service);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			// 2018-10-05 수정 
			// history 저장 
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SCALE_UP +" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/storageScaleUp", method = RequestMethod.POST)
	public ResponseEntity<String> updateStorageScaleUp(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			@RequestBody final String size, final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.STORAGE_SCALE_UP);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
//			service.setRequestUserId(userInfo.getUserId());

			switch (dbType) {
			case MariaDB:
				result = mariadbService.updateStorageScale(txId, namespace, serviceType, serviceName, size);
				break;
			case Redis:
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			// 2018-10-05 수정 
			// history 저장 
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			log.info(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.STORAGE_SCALE_UP + " 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}

	/**
	 * Update a service
	 * 
	 * @param id
	 *            service ID to be updated
	 * @param service
	 *            source Service object to be updated
	 * @return ResponseEntity<Service>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/scaleOut", method = RequestMethod.PUT)
	public ResponseEntity<String> updateScaleOut(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			@RequestBody final ZDBEntity service, final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.SCALE_OUT);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			service.setRequestUserId(userInfo.getUserId());
			switch (dbType) {
			case Redis:
				result = redisService.updateScaleOut(txId, service);
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SCALE_OUT +" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}	
	
	/**
	 * Delete a service
	 * 
	 * @param serviceName
	 *            service ID to be deleted
	 * @return ResponseEntity<Void>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteServiceInstance(@PathVariable("serviceType") final String serviceType, @PathVariable("namespace") final String namespace, @PathVariable("serviceName") final String serviceName) {
		String txId = txId();

		ZDBType dbType = ZDBType.getType(serviceType);

		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setServiceName(serviceName);
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setOperation(RequestEvent.DELETE);
			event.setUserId(userInfo.getUserName());
			
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.deleteServiceInstance(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				result = redisService.deleteServiceInstance(txId, namespace, serviceType, serviceName);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.DELETE +" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/restart", method = RequestMethod.GET)
	public ResponseEntity<String> restartService(@PathVariable("serviceType") final String serviceType, @PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName) {

		String txId = txId();
		ZDBType dbType = ZDBType.getType(serviceType);

		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setServiceType(serviceType);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.RESTART);
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setUserId(userInfo.getUserName());
			
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.restartService(txId, dbType, namespace, serviceName);
				break;
			case Redis:
				result = redisService.restartService(txId, dbType, namespace, serviceName);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			com.zdb.core.domain.Result result = new Result(txId, IResult.ERROR, RequestEvent.RESTART +" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}

	private String txId() {
		return UUID.randomUUID().toString();
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/variables", method = RequestMethod.GET)
	public ResponseEntity<String> getDBVariables(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName
			) {
		Result result = null;
		String txId = txId();
		ZDBType dbType = ZDBType.getType(serviceType);
		try {
			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).getDBVariables(txId, namespace, serviceName);
				break;
			case Redis:
				result = ((RedisServiceImpl) redisService).getDBVariables(txId, namespace, serviceName);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "환경변수 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/allVariables", method = RequestMethod.GET)
	public ResponseEntity<String> getAllDBVariables(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName
			) {
		Result result = null;
		String txId = txId();
		ZDBType dbType = ZDBType.getType(serviceType);
		try {
			switch (dbType) {
			case MariaDB:
				// TODO
				break;
			case Redis:
				result = ((RedisServiceImpl) redisService).getAllDBVariables(txId, namespace, serviceName);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "환경변수 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/variables", method = RequestMethod.PUT)
	public ResponseEntity<String> updateDBVariables(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@RequestBody final Map<String, String> config
	) {
		Result result = null;
		String txId = txId();
		
		ZDBType dbType = ZDBType.getType(serviceType);

		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.UPDATE_CONFIG);
			event.setUserId(userInfo.getUserName());	
			
			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).updateConfig(txId, namespace, serviceName, config);
				break;
			case Redis:
				result = ((RedisServiceImpl) redisService).updateDBVariables(txId, namespace, serviceName, config);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, RequestEvent.UPDATE_CONFIG +" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/accounts", method = RequestMethod.GET)
	public ResponseEntity<String> getDBUsers(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName
			) {
		Result result = null;
		String txId = txId();
		
		try {
			result = ((MariaDBServiceImpl) mariadbService).getDBInstanceAccounts(txId, namespace, serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "사용자 권한 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/accounts/{accountId}", method = RequestMethod.POST)
	public ResponseEntity<String> createDBUser(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("accountId") final String accountId,
			@RequestBody final ZDBMariaDBAccount account
			) {
		String txId = txId();
		
		Result result = null;
		log.debug("accountId: {}, accountPassword: {}", accountId, account.getUserPassword());
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.CREATE_DB_USER);
			event.setUserId(userInfo.getUserName());
			
			result = ((MariaDBServiceImpl) mariadbService).createDBUser(txId, namespace, serviceName, account);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "사용자 권한 등록 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/accounts/{accountId}", method = RequestMethod.PUT)
	public ResponseEntity<String> updateDBUser(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("accountId") final String accountId,
			@RequestBody final ZDBMariaDBAccount account
			) {
		String txId = txId();
		
		Result result = null;
		log.debug("accountId: {}, accountPassword: {}", accountId, account.getUserPassword());
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.UPDATE_DB_USER);
			event.setUserId(userInfo.getUserName());
			
			result = ((MariaDBServiceImpl) mariadbService).updateDBUser(txId(), namespace, serviceName, account);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "사용자 권한 변경 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/accounts/{accountId}", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteDBUser(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("accountId") final String accountId
	) {
		String txId = txId();

		Result result = null;
		log.debug("deleting an account. accountId: {}", accountId);
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.DELETE_DB_USER);
			event.setUserId(userInfo.getUserName());
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);

			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).deleteDBInstanceAccount(txId, namespace, serviceName, accountId);
				break;
			case Redis:
//				result = redisService.deleteDBInstanceAccount(txId, namespace, serviceName, accountId);
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "사용자 권한 정보 삭제 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	/**
	 * getting a Pod/Container Logs
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/log/{podname}", method = RequestMethod.GET)
	public ResponseEntity<String> getPodLog(@PathVariable("namespace") final String namespace, @PathVariable("podname") final String podName) {
		try {
			Result result = mariadbService.getPodLog(namespace, podName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "로그 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/slowlog/{podname}", method = RequestMethod.GET)
	public ResponseEntity<String> getSlowLog(@PathVariable("namespace") final String namespace, @PathVariable("podname") final String podName) {
		try {
			Result result = mariadbService.getSlowLog(namespace, podName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "로그 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/slowlog/{podname}/download", method = RequestMethod.GET)
	public ResponseEntity<String> getSlowLogDownload(@PathVariable("namespace") final String namespace, @PathVariable("podname") final String podName) {
		try {
			Result result = mariadbService.getSlowLogDownload(namespace, podName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "로그 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceName}/mycnf", method = RequestMethod.GET)
	public ResponseEntity<String> getMycnf(@PathVariable("namespace") final String namespace, @PathVariable("serviceName") final String serviceName) {
		try {
			Result result = mariadbService.getMycnf(namespace, serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "환경 설정 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/operationEvents", method = RequestMethod.GET)
	public ResponseEntity<String> getOperationEvents(
			@RequestParam("namespace") final String namespace, 
			@RequestParam("serviceName") final String serviceName,
			@RequestParam("startTime") final String startTime,
			@RequestParam("endTime") final String endTime,
			@RequestParam("keyword") final String keyword) {
		try {
			Result result = commonService.getOperationEvents(namespace, serviceName, startTime, endTime, keyword);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "이벤트 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/events", method = RequestMethod.GET)
	public ResponseEntity<String> getEvents(
			@RequestParam("namespace") final String namespace, 
			@RequestParam("kind") final String kind,
			@RequestParam("serviceName") final String serviceName,
			@RequestParam("startTime") final String startTime,
			@RequestParam("endTime") final String endTime,
			@RequestParam("keyword") final String keyword) {
		try {
			Result result = mariadbService.getEvents(namespace, serviceName, kind, startTime, endTime, keyword);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "이벤트 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Retrieve All services
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/namespaces", method = RequestMethod.GET)
	public ResponseEntity<String> getNamespaces() {
		try {
			UserInfo userInfo = getUserInfo();
			String namespaces = userInfo.getNamespaces();
			List<String> userNamespaces = new ArrayList<>();
			if(namespaces != null) {
				String[] split = namespaces.split(",");
				for (String ns : split) {
					userNamespaces.add(ns.trim());
				}
			}
			
			Result result = mariadbService.getNamespaces(userNamespaces);
			return new ResponseEntity<String>(result.toJson(), result.status());
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Namespace 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/updateUserNamespaces", method = RequestMethod.POST)
	public ResponseEntity<String> updateUserNamespace(final UriComponentsBuilder ucBuilder) {

		String txId = txId();
		try {
			UserInfo userInfo = getUserInfo();
			String namespaces = userInfo.getNamespaces();
			List<String> userNamespaces = new ArrayList<>();
			if(namespaces != null && !namespaces.isEmpty()) {
				String[] split = namespaces.split(",");
				for (String ns : split) {
					userNamespaces.add(ns.trim());
				}
			}
			
			if(userInfo != null && userInfo.getUserId() != null) {
				List<UserNamespaces> userNamespaceList = userNamespaceRepo.findByUserId(userInfo.getUserId());
				List<String> savedUserNamespaceList = new ArrayList<>();
				for (UserNamespaces us : userNamespaceList) {
					savedUserNamespaceList.add(us.getNamespace());
					if(!userNamespaces.contains(us.getNamespace())) {
						userNamespaceRepo.delete(us);
					}
				}
				
				for (String ns : userNamespaces) {
					if(!savedUserNamespaceList.contains(ns)) {
						UserNamespaces un = new UserNamespaces();
						un.setUserId(userInfo.getUserId());
						un.setNamespace(ns);
						
						userNamespaceRepo.save(un);
					}
				}
			}

			return new ResponseEntity<String>("", HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "사용자 Namespace 등록 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/service/services", method = RequestMethod.GET)
	public ResponseEntity<String> getServices() throws Exception {
		try {
			Result result = mariadbService.getAllServices();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "전체 서비스 LB 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/services/{serviceName}", method = RequestMethod.GET)
	public ResponseEntity<String> getService(@PathVariable("namespace") String namespace, @PathVariable("serviceType") String serviceType, @PathVariable("serviceName") String serviceName) throws Exception {
		try {
			Result result = mariadbService.getService(namespace, serviceType, serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "서비스 LB 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}


	@RequestMapping(value = "/{namespace}/service/services", method = RequestMethod.GET)
	public ResponseEntity<String> getServicesWithNamespaces(@PathVariable("namespace") String namespaces) throws Exception {
		try {
			Result result = mariadbService.getServicesWithNamespaces(namespaces, false);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "서비스 LB 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/services", method = RequestMethod.GET)
	public ResponseEntity<String> getServices(@PathVariable("namespace") String namespace, @PathVariable("serviceType") String serviceType) throws Exception {
		try {
			Result result = mariadbService.getServices(namespace, serviceType);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "서비스 LB 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Retrieve All Pods
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/pods", method = RequestMethod.GET)
	public ResponseEntity<String> getPods(@PathVariable("namespace") final String namespace, 
										  @PathVariable("serviceType") final String serviceType,
										  @PathVariable("serviceName") final String serviceName ) {
		try {
			Result result = mariadbService.getPods(namespace, serviceType, serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Pod 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/userGrants", method = RequestMethod.GET)
	public ResponseEntity<String> getUserGrants(@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceType") final String serviceType,
			@PathVariable("serviceName") final String serviceName ) {
		try {
			Result result = mariadbService.getUserGrants(namespace, serviceType, serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "사용자 권한 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	/**
	 * Getting a Pod info.
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/pods/{podName}", method = RequestMethod.GET)
	public ResponseEntity<String> getPod(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceType") final String serviceType, 
			        				     @PathVariable("serviceName") final String serviceName,
			        				     @PathVariable("podName") final String podName ) {
		try {
			Result result = mariadbService.getPodWithName(namespace, serviceType, serviceName, podName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Pod 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Getting a Pod Resource info.
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/pods/resource", method = RequestMethod.GET)
	public ResponseEntity<String> getPodResource(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceType") final String serviceType, 
			        				     @PathVariable("serviceName") final String serviceName ) {
		try {
			Result result = mariadbService.getPodResources(namespace, serviceType, serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Pod 리소스 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Getting a Connection Info.
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/connection", method = RequestMethod.GET)
	public ResponseEntity<String> getConnectionInfo(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceType") final String serviceType, 
			        				     @PathVariable("serviceName") final String serviceName ) {
		try {
		    // mariadb , redis, postgresql, rabbitmq, mongodb
		    ZDBType dbType = ZDBType.getType(serviceType);
			
		    Result result = null;
		    
		    switch (dbType) {
		    case MariaDB: 
		    	result = mariadbService.getConnectionInfo(namespace, serviceType, serviceName);
		    	break;
		    case Redis:
		    	result = redisService.getConnectionInfo(namespace, serviceType, serviceName);
		    	break;
		    default:
		    	log.error("Not support.");
		    	result.setMessage("Not support service type.");
		    	break;
		    }
			
		    return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "DB 연결 정보 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Getting a Connection Info.
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/service/{podName}/metrics", method = RequestMethod.GET)
	public ResponseEntity<String> getPodMetrics(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("podName") final String podName ) {
		try {
			//http://169.56.80.189/api/v1/model/namespaces/zdb-maria/pod-list/maria-test777-mariadb-0/metrics/cpu-usage
			Result result = mariadbService.getPodMetrics(namespace, podName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Pod 리소스 사용량 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * resource check
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/avaliable", method = RequestMethod.GET)
	public ResponseEntity<String> isAvailableResource(@PathVariable("namespace") final String namespace, 
			@RequestParam("memory") final String memory, @RequestParam("cpu") final String cpu,  @RequestParam("clusterEnabled") final boolean clusterEnabled) {
		try {
			UserInfo userInfo = getUserInfo();
			String userId = userInfo.getUserId();
			
			Result result = commonService.isAvailableResource(namespace, userId, cpu, memory, clusterEnabled);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/resource", method = RequestMethod.GET)
	public ResponseEntity<String> getNamespaceResource(@PathVariable("namespace") final String namespace) {
		try {
			UserInfo userInfo = getUserInfo();
			String userId = userInfo.getUserId();
			
			Result result = commonService.getNamespaceResource(namespace, userId);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "가용 리소스 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Getting a Connection Info.
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/passwd", method = RequestMethod.PUT)
	public ResponseEntity<String> setNewPassword(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceType") final String serviceType, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final Map<String, String> param) {
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setServiceName(serviceName);
			event.setServiceType(serviceType);
			event.setOperation(RequestEvent.MODIFY_PASSWORD);
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setUserId(userInfo.getUserName());
			
			String newPassword = param.get("newPassword");
			String secretType = param.get("secretType");
			String clusterEnabled = param.get("clusterEnabled");
			
			if(newPassword == null || newPassword.isEmpty()) {
				Result result = new Result(null, IResult.ERROR, "새로운 password 입력하세요.");
				
				event.setStatus(result.getCode());
				event.setResultMessage(result.getMessage());
				
				return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
			}
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
		    ZDBType dbType = ZDBType.getType(serviceType);
			
		    Result result = null;
		    
		    switch (dbType) {
		    case MariaDB: 
		    	result = mariadbService.setNewPassword(txId, namespace, serviceType, serviceName, newPassword, clusterEnabled);
		    	if(result.isOK()) {
		    		((MariaDBServiceImpl) mariadbService).updateAdminPassword(txId, namespace, serviceName, newPassword);
		    	}
		    	break;
		    case Redis:
		    	result = redisService.setNewPassword(txId, namespace, serviceType, serviceName, newPassword, clusterEnabled);
		    	break;
		    default:
		    	log.error("Not support.");
		    	result.setMessage("Not support service type.");
		    	break;
		    }
		    
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "비밀번호 변경 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	
	/**
	 * Retrieve All Abnormal Persistent Volume Claims
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/pvcs/abnormal/{abnormalType}", method = RequestMethod.GET)
	public ResponseEntity<String> getAbnormalPersistentVolumeClaims(@PathVariable("namespace") final String namespace,
																	@PathVariable("abnormalType") final String abnormalType) {
		try {
			Result result = mariadbService.getAbnormalPersistentVolumeClaims(namespace, abnormalType);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	/**
	 * Retrieve All Abnormal Persistent Volume
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/pvs/abnormal", method = RequestMethod.GET)
	public ResponseEntity<String> getAbnormalPersistentVolumeClaims() {
		try {
			Result result = mariadbService.getAbnormalPersistentVolumes();
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/pod/{podName}/restart", method = RequestMethod.GET)
	public ResponseEntity<String> restartPod(@PathVariable("namespace") final String namespace, 
										     @PathVariable("serviceType") final String serviceType, 
										     @PathVariable("serviceName") final String serviceName,
											 @PathVariable("podName") final String podName) {
		String txId = txId();

		RequestEvent event = new RequestEvent();
		try {

			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setServiceName(serviceName);
			event.setServiceType(serviceType);
			event.setOperation(RequestEvent.POD_RESTART);
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setUserId(userInfo.getUserName());
			
			com.zdb.core.domain.Result result = null;
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
		    ZDBType dbType = ZDBType.getType(serviceType);
			
		    
		    switch (dbType) {
		    case MariaDB: 
		    	result = mariadbService.restartPod(txId, namespace, serviceName, podName);
		    	break;
		    case Redis:
		    	result = redisService.restartPod(txId, namespace, serviceName, podName);
		    	break;
		    default:
		    	log.error("Not support.");
		    	result.setMessage("Not support service type.");
		    	break;
		    }

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "재시작 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	

	}
	
	/**
	 * Update MariaDB My.cnf
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/mariadb/service/{serviceName}/config", method = RequestMethod.PUT)
	public ResponseEntity<String> updateConfig(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final Map<String, String> config) {
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType("mariadb");
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.CREATE);
			event.setUserId(userInfo.getUserName());
			
			Result result = ((MariaDBServiceImpl)mariadbService).updateConfig(txId, namespace, serviceName, config);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "환경 설정 변경 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	@RequestMapping(value = "/{namespace}/tag/{serviceName}", method = RequestMethod.POST)
	public ResponseEntity<String> createTag(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final Tag tag) {
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			
			ReleaseMetaData releaseName = releaseRepository.findByReleaseName(serviceName);
			if(releaseName != null) {
				event.setServiceType(releaseName.getApp());
			}
			
			event.setTxId(txId());
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.CREATE_TAG);
			event.setUserId(userInfo.getUserName());
			
			Result result = mariadbService.createTag(tag);

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "태그 생성 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	@RequestMapping(value = "/{namespace}/tag/{serviceName}", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteTag(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final Tag tag) {
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			
			ReleaseMetaData releaseName = releaseRepository.findByReleaseName(serviceName);
			if(releaseName != null) {
				event.setServiceType(releaseName.getApp());
			}
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.DELETE_TAG);
			event.setUserId(userInfo.getUserName());	
			Result result = mariadbService.deleteTag(tag);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "태그 삭제 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	@RequestMapping(value = "/{namespace}/tag/{serviceName}", method = RequestMethod.GET)
	public ResponseEntity<String> getTagsWithService(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceName") final String serviceName) {
		try {
			Result result = mariadbService.getTagsWithService(namespace, serviceName);
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "태그 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/tags", method = RequestMethod.GET)
	public ResponseEntity<String> getTagsWithNamespace(@PathVariable("namespace") final String namespace) {
		String txId = txId();
		
		try {
			Result result = mariadbService.getTagsWithNamespace(namespace);
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "태그 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/tags", method = RequestMethod.GET)
	public ResponseEntity<String> getTags() {
		String txId = txId();
		
		UserInfo userInfo = getUserInfo();
		
		try {
			String namespaces = userInfo.getNamespaces();
			List<String> userNamespaces = new ArrayList<>();
			if(namespaces != null) {
				String[] split = namespaces.split(",");
				for (String ns : split) {
					userNamespaces.add(ns.trim());
				}
			}
			
			Result result = mariadbService.getTags(userNamespaces);
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "태그 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/nodes", method = RequestMethod.GET)
	public ResponseEntity<String> getNodes() throws Exception {
		try {
			Result result = redisService.getNodes();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Node 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}	
	
	@RequestMapping(value = "/nodeCount", method = RequestMethod.GET)
	public ResponseEntity<String> getNodeCount() throws Exception {
		try {
			Result result = redisService.getNodeCount();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Node 수 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}	
	
	/**
	 * Retrieve All Abnormal Persistent Volume Claims
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/pvcs/unused", method = RequestMethod.GET)
	public ResponseEntity<String> getUnusedPersistentVolumeClaims(@PathVariable("namespace") final String namespace) {
		try {
			Result result = redisService.getUnusedPersistentVolumeClaims(namespace);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * @return
	 */
	private UserInfo getUserInfo() {
		UserInfo userInfo = new UserInfo();
		userInfo.setUserId(request.getHeader("userId"));
		userInfo.setUserName(request.getHeader("userName"));
		userInfo.setEmail(request.getHeader("email"));
		userInfo.setAccessRole(request.getHeader("accessRole"));
		userInfo.setNamespaces(request.getHeader("namespaces"));
		userInfo.setDefaultNamespace(request.getHeader("defaultNamespace"));

		return userInfo;
	}
	
	@RequestMapping(value = "/pvc", method = RequestMethod.POST)
	public ResponseEntity<String> createPersistentVolumeClaim(@RequestBody final ZDBPersistenceEntity entity,
			final UriComponentsBuilder ucBuilder) {

//		{
//		  "namespace": "ns-zdb-02",
//		  "persistenceSpec": {
//		    "accessMode": "ReadWriteOnce",
//		    "billingType": "hourly",
//		    "size": "20Gi",
//		    "storageClass": "ibmc-block-silver",
//		    "subPath": "string"
//		  },
//		  "podName": "ns-zdb-02-hhh-mariadb-slave-0",
//		  "serviceName": "ns-zdb-02-hhh",
//		  "serviceType": "mariadb"
//		}
		
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(entity.getServiceType());
			event.setNamespace(entity.getNamespace());
			event.setServiceName(entity.getServiceName());
			event.setOperation(RequestEvent.CREATE_PVC);
			event.setUserId(userInfo.getUserName());
			
			log.info("{}, {}, {}", userInfo.getUserId(), userInfo.getUserName(), userInfo.getAccessRole());

			//
			Result result = commonService.createPersistentVolumeClaim(txId, entity);

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "PVC 생성 오류").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(e.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}

	}

	/**
	 * Create ZDB Global Configurations
	 * @return
	 */
	@RequestMapping(value = "/zdbconfig", method = RequestMethod.POST)
	public ResponseEntity<String> createZDBConfig(@RequestBody final ZDBConfig[] zdbConfigs) {
		try {
			String configs = "";
			for (int i=0; i < zdbConfigs.length; i++) {
				ZDBConfig zdbConfig = zdbConfigs[i];
				commonService.createZDBConfig(zdbConfig);
				if (i == 0) {
					configs = configs.concat(zdbConfigs[i].getConfigName());
				} else {
					configs = configs.concat(", " + zdbConfigs[i].getConfigName());
				}
			}
			EventMetaData m = new EventMetaData();
			String namespace = zdbConfigs[0].getNamespace();
			m.setFirstTimestamp(DateUtil.formatDate(DateUtil.currentDate()));
			m.setKind("ZDB Global Configuration"); // TODO: UI 추가
			m.setLastTimestamp(DateUtil.formatDate(DateUtil.currentDate()));
			m.setMessage("ZDB Global 환경설정 생성 완료 (Namespace: "
							+ zdbConfigs[0].getNamespace()
							+ ", Configurations: "
							+ configs);
			m.setMetadata("");
			m.setReason("ZDB Global Configuration Created");
			m.setUid("");
			m.setName("ZDB Global Configuration");
			m.setNamespace(namespace);
			EventMetaData findByNameAndMessageAndLastTimestamp = eventRepo.findByNameAndMessageAndLastTimestamp(m.getName(), m.getMessage(), m.getLastTimestamp());
			if(findByNameAndMessageAndLastTimestamp == null) {
				eventRepo.save(m);
			}
			return new ResponseEntity<String>(new Result("", Result.OK, "Global 설정값이 생성되었습니다.").toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, "ZDB Global 설정 등록 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Retrieve ZDB Global Configurations
	 * @return
	 */
	@RequestMapping(value = "/{namespace}/zdbconfigs", method = RequestMethod.GET)
	public ResponseEntity<String> getZDBConfig(@PathVariable("namespace") final String namespace) {
		try {			
			Result result = commonService.getZDBConfig(namespace);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, "Global 설정 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Update ZDB Global Configurations
	 * @return
	 */
	@RequestMapping(value = "/zdbconfig", method = RequestMethod.PUT)
	public ResponseEntity<String> updateZDBConfigs(@RequestBody final ZDBConfig[] zdbConfigs) {
		try {
			String configs = "";
			for (int i=0; i < zdbConfigs.length; i++) {
				ZDBConfig zdbConfig = zdbConfigs[i];
				commonService.updateZDBConfig(zdbConfig);
				if (i == 0) {
					configs = configs.concat(zdbConfigs[i].getConfigName());
				} else {
					configs = configs.concat(", " + zdbConfigs[i].getConfigName());
				}
			}	
			EventMetaData m = new EventMetaData();
			String namespace = zdbConfigs[0].getNamespace();
			m.setFirstTimestamp(DateUtil.formatDate(DateUtil.currentDate()));
			m.setKind("ZDB Global Configuration"); // TODO: UI 추가
			m.setLastTimestamp(DateUtil.formatDate(DateUtil.currentDate()));
			m.setMessage("ZDB Global 환경설정 변경 완료 (Namespace: "
					+ zdbConfigs[0].getNamespace()
					+ ", Configurations: "
					+ configs);
			m.setMetadata("");
			m.setReason("ZDB Global Configuration Updated");
			m.setUid("");
			m.setName("ZDB Global Configuration");
			m.setNamespace(namespace);
			EventMetaData findByNameAndMessageAndLastTimestamp = eventRepo.findByNameAndMessageAndLastTimestamp(m.getName(), m.getMessage(), m.getLastTimestamp());
			if(findByNameAndMessageAndLastTimestamp == null) {
				eventRepo.save(m);
			}
			return new ResponseEntity<String>(new Result("", Result.OK, "ZDB Global 설정값이 변경되었습니다.").toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, "ZDB Global 설정값이 변경 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	/**
	 * Delete ZDB Global Configurations
	 * @return
	 */
	@RequestMapping(value = "/zdbconfig", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteZDBConfig(@RequestBody final ZDBConfig[] zdbConfigs) {
		try {
			String configs = "";
			for (int i=0; i < zdbConfigs.length; i++) {
				ZDBConfig zdbConfig = zdbConfigs[i];
				commonService.deleteZDBConfig(zdbConfig);
				if (i == 0) {
					configs = configs.concat(zdbConfigs[i].getConfigName());
				} else {
					configs = configs.concat(", " + zdbConfigs[i].getConfigName());
				}
			}
			EventMetaData m = new EventMetaData();
			String namespace = zdbConfigs[0].getNamespace();
			m.setFirstTimestamp(DateUtil.formatDate(DateUtil.currentDate()));
			m.setKind("ZDB Global Configuration"); // TODO: UI 추가
			m.setLastTimestamp(DateUtil.formatDate(DateUtil.currentDate()));
			m.setMessage("ZDB Global 환경설정 삭제 완료 (Namespace: "
					+ zdbConfigs[0].getNamespace()
					+ ", Configurations: "
					+ configs);
			m.setMetadata("");
			m.setReason("ZDB Global Configuration Deleted");
			m.setUid("");
			m.setName("ZDB Global Configuration");
			m.setNamespace(namespace);
			EventMetaData findByNameAndMessageAndLastTimestamp = eventRepo.findByNameAndMessageAndLastTimestamp(m.getName(), m.getMessage(), m.getLastTimestamp());
			if(findByNameAndMessageAndLastTimestamp == null) {
				eventRepo.save(m);
			}
			return new ResponseEntity<String>(new Result("", Result.OK, "ZDB Global 설정값이 삭제되었습니다.").toJson(), HttpStatus.OK);
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, "ZDB Global 설정값이 삭제 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/{stsName}/off", method = RequestMethod.PUT)
	public ResponseEntity<String> serviceOff(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("stsName") final String stsName,
			final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.SERVICE_OFF);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
//			service.setRequestUserId(userInfo.getUserId());

			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceOff(txId, namespace, serviceType, serviceName, stsName);
				break;
			case Redis:
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			// 2018-10-05 수정 
			// history 저장 
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			log.info(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SERVICE_OFF+" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/{stsName}/on", method = RequestMethod.PUT)
	public ResponseEntity<String> serviceOn(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("stsName") final String stsName,
			final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.SERVICE_ON);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
//			service.setRequestUserId(userInfo.getUserId());

			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceOn(txId, namespace, serviceType, serviceName, stsName);
				break;
			case Redis:
				break;
			case PostgreSQL:
				// TODO
				break;
			case RabbitMQ:
				// TODO
				break;
			case MongoDB:
				// TODO
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			// 2018-10-05 수정 
			// history 저장 
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			log.info(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SERVICE_ON+" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/svc/masterToslave", method = RequestMethod.PUT)
	public ResponseEntity<String> serviceChangeMasterToSlave(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.SERVICE_MASTER_TO_SLAVE);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceChaneMasterToSlave(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			// 2018-10-05 수정 
			// history 저장 
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			log.debug(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SERVICE_MASTER_TO_SLAVE+" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/svc/slaveTomaster", method = RequestMethod.PUT)
	public ResponseEntity<String> serviceChangeSlaveToMaster(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.SERVICE_SLAVE_TO_MASTER);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);
			
			com.zdb.core.domain.Result result = null;
			
			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceChaneSlaveToMaster(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			// 2018-10-05 수정 
			// history 저장 
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			log.debug(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SERVICE_SLAVE_TO_MASTER+" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	
	/**
	 * Master 장애로 서비스LB 를 Master -> Slave 로 전환 여부를 반환한다.
	 * 
	 * Result.message 로 상태값 반환
	 *  - MasterToSlave (전환된 상태)
	 *  - MasterToMaster (정상 서비스 상태)
	 *  - unknown (파라메터 오류 또는 알수 없음)
	 *  
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @param ucBuilder
	 * @return
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/svc/status", method = RequestMethod.GET)
	public ResponseEntity<String> serviceFailOverStatus (
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		try {
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
			case Redis:
				result = mariadbService.serviceFailOverStatus(txId, namespace, serviceType, serviceName);
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			log.debug(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SERVICE_FAIL_OVER_STATUS+" 조회 오류!").putValue(IResult.EXCEPTION, e);
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
		}	
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/slowlogRotation", method = RequestMethod.PUT)
	public ResponseEntity<String> slowlogRotation(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		String txId = txId();
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.SLOWLOG_ROTATION);
			event.setUserId(userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.slowlogRotation(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			// 2018-10-05 수정 
			// history 저장 
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			log.info(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEvent.SERVICE_MASTER_TO_SLAVE+" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	/**
	 * Auto Failover 
	 *  - On : add label : zdb-failover-enable=true
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=true" --overwrite
	 *  - Off : update label : zdb-failover-enable=false
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=false" --overwrite
	 *        
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @param enable
	 * @param ucBuilder
	 * @return
	 */
	@RequestMapping(value = "/failover/{namespace}/{serviceType}/{serviceName}/{enable}", method = RequestMethod.PUT)
	public ResponseEntity<String> updateAutoFailoverEnable(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("enable") final Boolean enable,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			event.setTxId(txId);
			event.setServiceName(serviceName);
			event.setServiceType(ZDBType.MariaDB.getName());
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setOperation(RequestEvent.SET_AUTO_FAILOVER_USABLE);
			
			Result result = mariadbService.updateAutoFailoverEnable(txId, namespace, serviceType, serviceName, enable);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result("", IResult.ERROR, RequestEvent.SET_AUTO_FAILOVER_USABLE + " 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	/**
	 * failover 기능 활성화를 위한 설정으로 변경.
	 * 
	 * 1. report_status shell script configmap 생성
	 * 2. Satefulset 에 command 추가 
	 * 3. configmap volumnMount 추가
	 * 4. volumes 추가 
	 * 
	 * Auto Failover 
	 *  - On : add label : zdb-failover-enable=true
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=true" --overwrite
	 *  - Off : update label : zdb-failover-enable=false
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=false" --overwrite
	 *        
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @param ucBuilder
	 * @return
	 */
	@RequestMapping(value = "/failover/{namespace}/{serviceType}/{serviceName}", method = RequestMethod.PUT)
	public ResponseEntity<String> addAutoFailover(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			event.setTxId(txId);
			event.setServiceName(serviceName);
			event.setServiceType(ZDBType.MariaDB.getName());
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setOperation(RequestEvent.ADD_AUTO_FAILOVER);
			
			Result result = mariadbService.updateAutoFailoverEnable(txId, namespace, serviceType, serviceName, true);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result("", IResult.ERROR, RequestEvent.ADD_AUTO_FAILOVER + " 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	/**
	 * Statefulset 의 label : zdb-failover-enable=true 가 등록된 서비스 목록.(master)
	 * 
	 * @param txId
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/failover/{namespace}/services", method = RequestMethod.GET)
	public ResponseEntity<String> getAutoFailoverServices(
			@PathVariable("namespace") final String namespace,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			Result result = mariadbService.getAutoFailoverServices(txId, namespace);
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result("", IResult.ERROR, "조회 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
		}	
	}	
	
	/**
	 * Statefulset 의 label : zdb-failover-enable=true 가 등록된 서비스 목록.(master)
	 * 
	 * @param txId
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/failover/{namespace}/{serviceName}/service", method = RequestMethod.GET)
	public ResponseEntity<String> getAutoFailoverService(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			Result result = mariadbService.getAutoFailoverService(txId, namespace, serviceName);
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "조회 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
		}	
	}	
	
	@RequestMapping(value = "/{namespace}/{serviceType}/{serviceName}/changePort", method = RequestMethod.PUT)
	public ResponseEntity<String> changePort(@PathVariable("namespace") final String namespace, 
										 @PathVariable("serviceType") final String serviceType, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final String port) {
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEvent.CHANGE_PORT);
			event.setUserId(userInfo.getUserName());
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl)mariadbService).changePort(txId, namespace, serviceName, port);
				break;
			case Redis:
				result.setMessage("Not support service type.");
				break;
			default:
				log.error("Not support.");
				result.setMessage("Not support service type.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "포트 변경 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}

	@RequestMapping(value = "/workerpools", method = RequestMethod.GET)
	public ResponseEntity<String> getWorkerPools() throws Exception {
		try {
			Result result = commonService.getWorkerPools();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, RequestEvent.WORKER_POOLS_READ + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}	
}
