package com.zdb.core.controller;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.CredentialConfirm;
import com.zdb.core.domain.DBUser;
import com.zdb.core.domain.Database;
import com.zdb.core.domain.EventMetaData;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.MariaDBVariable;
import com.zdb.core.domain.MariadbUserPrivileges;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.RequestEventCode;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.UserInfo;
import com.zdb.core.domain.UserNamespaces;
import com.zdb.core.domain.ZDBConfig;
import com.zdb.core.domain.ZDBEntity;
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
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("서비스 조회 - Not support.");
				result = new Result(null, IResult.ERROR, "서비스 조회 - Not support.");
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
			event.setOperation(RequestEventCode.SERVICE_CREATE.getDesc());
			event.setType(RequestEventCode.SERVICE_CREATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
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
				if("Performance".equals(entity.getKindOfStorage())) {
					history.append("Storage Iops").append(":").append(entity.getPersistenceSpec()[0].getStorageIops()).append("\n");
				}
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
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("서비스 생성 - Not support.");
				result = new Result(null, IResult.ERROR, "서비스 생성 - Not support.");
				break;
			}
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			long s = System.currentTimeMillis();
			
			while((System.currentTimeMillis() - s) < 1000 * 10) {
				Thread.sleep(1000);
				ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(entity.getServiceName());
				if(releaseMeta == null) {
					continue;
				} else {
					break;
				}
			}
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
			event.setOperation(RequestEventCode.NETWORK_PUBLIC_CREATE.getDesc());
			event.setType(RequestEventCode.NETWORK_PUBLIC_CREATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			log.info("{}, {}, {}", userInfo.getUserId(), userInfo.getUserName(), userInfo.getAccessRole());

			com.zdb.core.domain.Result result = null;
			
			result = commonService.createPublicService(txId, namespace, serviceType, serviceName);

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.NETWORK_PUBLIC_CREATE.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.NETWORK_PUBLIC_DELETE.getDesc());
			event.setType(RequestEventCode.NETWORK_PUBLIC_DELETE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			log.info("{}, {}, {}", userInfo.getUserId(), userInfo.getUserName(), userInfo.getAccessRole());

			com.zdb.core.domain.Result result = null;
			
			result = commonService.deletePublicService(txId, namespace, serviceType, serviceName);

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.NETWORK_PUBLIC_DELETE.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.SCALE_UP.getDesc());
			event.setType(RequestEventCode.SCALE_UP.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
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
			case RabbitMQ:
			case MongoDB:
			default:
				log.error(RequestEventCode.SCALE_UP.getDesc() + " - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.SCALE_UP.getDesc() + " - Not support.");
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
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.SCALE_UP.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.SCALE_STORAGE_UP.getDesc());
			event.setType(RequestEventCode.SCALE_STORAGE_UP.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
//			service.setRequestUserId(userInfo.getUserId());

			switch (dbType) {
			case MariaDB:
				result = mariadbService.updateStorageScale(txId, namespace, serviceType, serviceName, size);
				break;
			case Redis:
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error(RequestEventCode.SCALE_STORAGE_UP.getDesc() + " - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.SCALE_STORAGE_UP.getDesc() + " - Not support.");
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
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.SCALE_STORAGE_UP.getDesc() + " 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.SCALE_OUT.getDesc());
			event.setType(RequestEventCode.SCALE_OUT.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			service.setRequestUserId(userInfo.getUserId());
			switch (dbType) {
			case Redis:
				result = redisService.updateScaleOut(txId, service);
				break;
			default:
				log.error(RequestEventCode.SCALE_OUT.getDesc() + " - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.SCALE_OUT.getDesc() + " - Not support.");
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.SCALE_OUT.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.SERVICE_DELETE.getDesc());
			event.setType(RequestEventCode.SERVICE_DELETE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.deleteServiceInstance(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				result = redisService.deleteServiceInstance(txId, namespace, serviceType, serviceName);
				break;
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error(RequestEventCode.SERVICE_DELETE.getDesc() + " - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.SERVICE_DELETE.getDesc() + " - Not support.");
				break;
			}
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.SERVICE_DELETE.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.SERVICE_RESTART.getDesc());
			event.setType(RequestEventCode.SERVICE_RESTART.getType());
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.restartService(txId, dbType, namespace, serviceName);
				break;
			case Redis:
				result = redisService.restartService(txId, dbType, namespace, serviceName);
				break;
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error(RequestEventCode.SERVICE_RESTART.getDesc() + " - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.SERVICE_RESTART.getDesc() + " - Not support.");
				break;
			}
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			com.zdb.core.domain.Result result = new Result(txId, IResult.ERROR, RequestEventCode.SERVICE_RESTART.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("환경변수 조회 - Not support.");
				result = new Result(txId, IResult.ERROR, "환경변수 조회 - Not support.");
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
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("환경변수 조회 - Not support.");
				result = new Result(txId, IResult.ERROR, "환경변수 조회 - Not support.");
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
			event.setOperation(RequestEventCode.CONFIG_UPDATE.getDesc());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).updateConfig(txId, namespace, serviceName, config);
				break;
			case Redis:
				result = ((RedisServiceImpl) redisService).updateDBVariables(txId, namespace, serviceName, config);
				break;
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error(RequestEventCode.CONFIG_UPDATE.getDesc() +"Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.CONFIG_UPDATE.getDesc() +" - Not support.");
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
			result = new Result(txId, IResult.ERROR, RequestEventCode.CONFIG_UPDATE.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			@RequestBody final DBUser account
			) {
		String txId = txId();
		
		Result result = null;
		log.debug("accountId: {}", accountId);
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.USER_CREATE.getDesc());
			event.setType(RequestEventCode.USER_CREATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
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
			@RequestBody final DBUser account
			) {
		String txId = txId();
		
		Result result = null;
		log.debug("accountId: {}, accountPassword: {}", accountId, account.getPassword());
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.USER_UPDATE.getDesc());
			event.setType(RequestEventCode.USER_UPDATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
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
			@PathVariable("accountId") final String accountId,
			@RequestBody final DBUser account
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
			event.setOperation(RequestEventCode.USER_DELETE.getDesc());
			event.setType(RequestEventCode.USER_DELETE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);

			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).deleteDBUser(txId, namespace, serviceName, account);
				break;
			case Redis:
//				result = redisService.deleteDBInstanceAccount(txId, namespace, serviceName, accountId);
				break;
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("사용자 권한 정보 삭제 - Not support.");
				result = new Result(txId, IResult.ERROR, "사용자 권한 정보 삭제 - Not support.");
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
		Result result = null;
		try {
			ZDBType dbType = ZDBType.getType("mariadb");

			switch (dbType) {
			case MariaDB:
				result = mariadbService.getSlowLog(namespace, podName);
				break;
			case Redis:
				break;
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("로그 조회 - Not support.");
				result = new Result(null, IResult.ERROR, "로그 조회 - Not support.");
				break;
			}
			
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			result = new Result(null, IResult.ERROR, "로그 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/slowlog/{podname}/download", method = RequestMethod.GET)
	public ResponseEntity<String> getSlowLogDownload(@PathVariable("namespace") final String namespace, @PathVariable("podname") final String podName) {
		Result result = null;
		try {
			ZDBType dbType = ZDBType.getType("maraidb");

			switch (dbType) {
			case MariaDB:
				result = mariadbService.getSlowLogDownload(namespace, podName);
				break;
			case Redis:
				break;
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("로그 다운로드 - Not support.");
				result = new Result(null, IResult.ERROR, "로그 다운로드 - Not support.");
				break;
			}
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			result = new Result(null, IResult.ERROR, "로그 다운로드 오류!").putValue(IResult.EXCEPTION, e);
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
			@RequestParam("keyword") final String keyword,
			@RequestParam("type") final String type,
			@RequestParam("backupEventExceptYn") final String backupEventExceptYn
			) {
		try {
			Result result = commonService.getOperationEvents(namespace, serviceName, startTime, endTime, keyword, type, backupEventExceptYn);
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

			if (userInfo == null || userInfo.getUserId() == null) {
				new Result(null, IResult.ERROR, "네임스페이스 조회 오류");
				return new ResponseEntity<String>("네임스페이스 조회 오류.[사용자 정보를 알 수 없습니다.]", HttpStatus.EXPECTATION_FAILED);
			}

			// Result result = mariadbService.getNamespaces(userNamespaces);
			Result result = mariadbService.getUserNamespaces(userInfo.getUserId());
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "네임스페이스 조회 오류!").putValue(IResult.EXCEPTION, e);
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
			Result result = new Result(txId, IResult.ERROR, "사용자 네임스페이스 등록 오류!").putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, "전체 서비스 조회 오류!").putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, "서비스 조회 오류!").putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, "서비스 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/failover/services", method = RequestMethod.GET)
	public ResponseEntity<String> getFailoverServicesWithNamespaces(@PathVariable("namespace") String namespaces) throws Exception {
		try {
			Result result = mariadbService.getFailoverServicesWithNamespaces(namespaces, false);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "서비스 조회 오류!").putValue(IResult.EXCEPTION, e);
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
		
		Result result = null;
		
		try {
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);

			switch (dbType) {
			case MariaDB:
				result = mariadbService.getUserGrants(namespace, serviceType, serviceName);
				break;
			case Redis:
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("Not support.");
				result = new Result(null, IResult.ERROR, "사용자 권한 목록 조회 오류!");
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			result = new Result(null, IResult.ERROR, "사용자 권한 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
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
		    	log.error("DB 연결 정보 조회 - Not support.");
		    	result = new Result(null, IResult.ERROR, "DB 연결 정보 조회 - Not support.");
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
			//Result result = mariadbService.getPodMetrics(namespace, podName);
			
			// modified 20190510 heapster, metric-server 모두 지원 
			Result result = mariadbService.getPodMetricsV2(namespace, podName);
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
			event.setOperation(RequestEventCode.CREADENTIAL_UPDATE.getDesc());
			event.setType(RequestEventCode.CREADENTIAL_UPDATE.getType());
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
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
		    	log.error("비밀번호 변경 - Not support.");
		    	result = new Result(txId, IResult.ERROR, "비밀번호 변경 - Not support.");
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
			event.setOperation(RequestEventCode.SERVICE_POD_RESTART.getDesc());
			event.setType(RequestEventCode.SERVICE_POD_RESTART.getType());
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
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
		    	log.error("재시작 - Not support.");
		    	result = new Result(txId, IResult.ERROR, "재시작 - Not support.");
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
			event.setOperation(RequestEventCode.CONFIG_UPDATE.getDesc());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			Result result = ((MariaDBServiceImpl)mariadbService).updateConfig(txId, namespace, serviceName, config);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
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
			event.setOperation(RequestEventCode.TAG_CREATE.getDesc());
			event.setType(RequestEventCode.TAG_CREATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
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
			event.setOperation(RequestEventCode.TAG_DELETE.getDesc());
			event.setType(RequestEventCode.TAG_DELETE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
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
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
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
		
//	Request Header 조회 (2019-08-08)		
//		Enumeration<String> params = request.getHeaderNames();
//		System.out.println("----------------------------");
//		while (params.hasMoreElements()){
//		    String name = (String)params.nextElement();
//		    System.out.println(name + " : " +request.getHeader(name));
//		}
//		System.out.println("----------------------------");

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
			event.setOperation(RequestEventCode.PVC_CREATE.getDesc());
			event.setType(RequestEventCode.PVC_CREATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
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
			event.setOperation(RequestEventCode.SERVICE_OFF.getDesc());
			event.setType(RequestEventCode.SERVICE_OFF.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
//			service.setRequestUserId(userInfo.getUserId());

			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceOff(txId, namespace, serviceType, serviceName, stsName);
				break;
			case Redis:
				result = redisService.serviceOff(txId, namespace, serviceType, serviceName, stsName);
				break;
			case PostgreSQL:
				// TODO
			case RabbitMQ:
				// TODO
			case MongoDB:
				// TODO
			default:
				log.error("Not support.");
				result = new Result(txId, IResult.ERROR, "환경변수 조회 - Not support.");
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
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.SERVICE_OFF.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.SERVICE_ON.getDesc());
			event.setType(RequestEventCode.SERVICE_ON.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
//			service.setRequestUserId(userInfo.getUserId());

			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceOn(txId, namespace, serviceType, serviceName, stsName);
				break;
			case Redis:
				result = redisService.serviceOn(txId, namespace, serviceType, serviceName, stsName);
				break;
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error(RequestEventCode.SERVICE_ON.getDesc() + " - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.SERVICE_ON.getDesc() + " - Not support.");
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
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.SERVICE_ON.getDesc()+" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.CHANGEOVER_MASTER_TO_SLAVE.getDesc());
			event.setType(RequestEventCode.CHANGEOVER_MASTER_TO_SLAVE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceChangeMasterToSlave(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				result = redisService.serviceChangeMasterToSlave(txId, namespace, serviceType, serviceName);
				break;
			default:
				log.error(RequestEventCode.CHANGEOVER_MASTER_TO_SLAVE.getDesc() +" - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.CHANGEOVER_MASTER_TO_SLAVE.getDesc() +" - Not support.");
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
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.CHANGEOVER_MASTER_TO_SLAVE.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.CHANGEOVER_SLAVE_TO_MASTER.getDesc());
			event.setType(RequestEventCode.CHANGEOVER_SLAVE_TO_MASTER.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);
			
			com.zdb.core.domain.Result result = null;
			
			switch (dbType) {
			case MariaDB:
				result = mariadbService.serviceChangeSlaveToMaster(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				result = redisService.serviceChangeSlaveToMaster(txId, namespace, serviceType, serviceName);
				break;
			default:
				log.error(RequestEventCode.CHANGEOVER_SLAVE_TO_MASTER.getDesc() +" - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.CHANGEOVER_SLAVE_TO_MASTER.getDesc() +" - Not support.");
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
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.CHANGEOVER_SLAVE_TO_MASTER.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
				log.error(RequestEventCode.CHANGEOVER_STATUS.getDesc() +" - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.CHANGEOVER_STATUS.getDesc() +" - Not support.");
				break;
			}

			log.debug(result.toJson() +"|"+result.status());
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.CHANGEOVER_STATUS.getDesc() +" 조회 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.LOG_SLOWLOG_ROTATION.getDesc());
			event.setType(RequestEventCode.LOG_SLOWLOG_ROTATION.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbService.slowlogRotation(txId, namespace, serviceType, serviceName);
				break;
			case Redis:
				break;
			default:
				log.error(RequestEventCode.LOG_SLOWLOG_ROTATION.getDesc() + " - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.LOG_SLOWLOG_ROTATION.getDesc() + " - Not support.");
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
			Result result = new Result(txId, IResult.ERROR, RequestEventCode.LOG_SLOWLOG_ROTATION.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.AUTOFAILOVER_SET.getDesc());
			event.setType(RequestEventCode.AUTOFAILOVER_SET.getType());
			
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
			Result result = new Result("", IResult.ERROR, RequestEventCode.AUTOFAILOVER_SET.getDesc() + " 오류!").putValue(IResult.EXCEPTION, e);
			
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
			event.setOperation(RequestEventCode.AUTOFAILOVER_ADD.getDesc());
			event.setType(RequestEventCode.AUTOFAILOVER_ADD.getType());
			
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
			Result result = new Result("", IResult.ERROR, RequestEventCode.AUTOFAILOVER_ADD.getDesc() + " 오류!").putValue(IResult.EXCEPTION, e);
			
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
	 * failover 된 서비스틑 제외.
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
	 * failover 된 서비스틑 제외.
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
	
	/**
	 * Statefulset 의 label : zdb-failover-enable=true 가 등록된 서비스 목록.(master)
	 * 
	 * @param txId
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/failover/{namespace}/enabled-services", method = RequestMethod.GET)
	public ResponseEntity<String> getAutoFailoverEnabledServices(
			@PathVariable("namespace") final String namespace,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			Result result = mariadbService.getAutoFailoverEnabledServices(txId, namespace);
			
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
	
	@RequestMapping(value = "/{namespace}/{serviceType}/{serviceName}/changePort", method = RequestMethod.PUT)
	public ResponseEntity<String> changePort(@PathVariable("namespace") final String namespace, 
										 @PathVariable("serviceType") final String serviceType, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final String reqBody) {
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.NETWORK_CHANGE_PORT.getDesc());
			event.setType(RequestEventCode.NETWORK_CHANGE_PORT.getType());
			
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				if(reqBody!= null) {
					Gson gson = new Gson();
					Map serviceInfoMap = gson.fromJson(reqBody, Map.class);
					if(serviceInfoMap.containsKey("port")) {
						String port = (String) serviceInfoMap.get("port");
						result = ((MariaDBServiceImpl) mariadbService).changePort(txId, namespace, serviceName, port);
					} else {
						result = new Result(txId, Result.ERROR, "변경할 포트를 입력하세요.", null);
					}
				} else {
					result = new Result(txId, Result.ERROR, "변경할 포트를 입력하세요. 입력 포맷 오류.", null);
				}
				
				break;
			case Redis:
			default:
				log.error("포트 변경 - Not support.");
				result = new Result(txId, IResult.ERROR, "포트 변경 - Not support.");
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
			Result result = new Result(null, IResult.ERROR, RequestEventCode.WORKERPOOL_READ.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/nodes/{node}/workerpool", method = RequestMethod.GET)
	public ResponseEntity<String> getWorkerPool(@PathVariable("node") final String node) throws Exception {
		try {
			Result result = commonService.getWorkerPool(node);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, RequestEventCode.WORKERPOOL_READ.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value="/nodes/{node}/workerpool/{workerpool}",method = RequestMethod.PUT)
	public ResponseEntity<String> putWorkerPoolNode(@PathVariable String node,@PathVariable String workerpool) throws Exception {
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceName(node);
			event.setOperation(RequestEventCode.WORKERPOOL_UPDATE.getDesc());
			event.setType(RequestEventCode.WORKERPOOL_UPDATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			Result result = commonService.putWorkerPoolOfNode(txId, node, workerpool);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "worker-pool 변경 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}

	@RequestMapping(value="/{namespace}/{serviceType}/service/{serviceName}/workerpool/{workerpool}",method = RequestMethod.PUT)
	public ResponseEntity<String> putWorkerPoolOfService(@PathVariable String namespace, @PathVariable String serviceType, @PathVariable String serviceName, @PathVariable String workerpool) throws Exception {
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.WORKERPOOL_ZDBUPDATE.getDesc());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			Result result = mariadbService.putWorkerPoolOfService(txId, namespace, serviceType, serviceName, workerpool);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "worker-pool 변경 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}	
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/databases", method = RequestMethod.GET)
	public ResponseEntity<String> getDatabases(
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceType") final String serviceType,
			@PathVariable("serviceName") final String serviceName ) {
		
		Result result = null;
		
		try {
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);

			switch (dbType) {
			case MariaDB:
				result = mariadbService.getDatabases(namespace, serviceType, serviceName);
				break;
			case Redis:
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("Database 목록 조회 - Not support.");
				result = new Result(null, IResult.ERROR, "Database 목록 조회 오류!");
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			result = new Result(null, IResult.ERROR, "Database 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/database/{databaseName}", method = RequestMethod.POST)
	public ResponseEntity<String> createDatabase(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("databaseName") final String databaseName,
			@RequestBody final Database database
			) {
		String txId = txId();
		
		Result result = null;
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.DATABASE_CREATE.getDesc());
			event.setType(RequestEventCode.DATABASE_CREATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			result = ((MariaDBServiceImpl) mariadbService).createDatabase(txId, namespace, serviceName, database);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_CREATE + " 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/database/{databaseName}", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteDatabase(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("databaseName") final String databaseName,
			@RequestBody final Database database
	) {
		String txId = txId();

		Result result = null;
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.DATABASE_DELETE.getDesc());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);

			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).deleteDatabase(txId, namespace, serviceName, database);
				break;
			case Redis:
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error(RequestEventCode.DATABASE_DELETE.getDesc() +" - Not support.");
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_DELETE.getDesc());
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_DELETE.getDesc() + " 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	/**
	 * getting a File log
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/fileLogs/{logType}/{startDate}/{endDate}", method = RequestMethod.GET)
	public ResponseEntity<String> getFileLogs(@PathVariable final String namespace, @PathVariable final String serviceType,
											   @PathVariable final String serviceName,@PathVariable final String logType,
											   @PathVariable final String startDate,
											   @PathVariable final String endDate ) {
		try {
			Result result = mariadbService.getFileLog(namespace, serviceName,logType,startDate,endDate);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "PersistentVolumeClaim 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}	
	
	@RequestMapping(value="/allServices",method = RequestMethod.GET)
	public ResponseEntity<String> getAllServices() throws Exception {
		try {
			Result result = commonService.getAllServices2();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, RequestEventCode.WORKERPOOL_READ.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}	
	
	@RequestMapping(value="/{namespace}/alert/rules",method = RequestMethod.GET)
	public ResponseEntity<String> getAlertRules(@PathVariable String namespace) throws Exception {
		try {
			String txId = txId();
			Result result = commonService.getAlertRules(txId,namespace);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, "alert rules 조회 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	} 
	@RequestMapping(value="/{namespace}/{serviceName}/alert/rules",method = RequestMethod.GET)
	public ResponseEntity<String> getAlertRulesInService(@PathVariable String namespace,@PathVariable String serviceName) throws Exception {
		try {
			String txId = txId();
			Result result = commonService.getAlertRulesInService(txId,serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, "alert rules 조회 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	} 

	@RequestMapping(value="/{namespace}/alert/rule/{alert}",method = RequestMethod.GET)
	public ResponseEntity<String> getAlertRule(@PathVariable("namespace") String namespace,@PathVariable String alert) throws Exception {
		try {
			String txId = txId();
			Result result = commonService.getAlertRule(txId,namespace,alert);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, "alert rule 조회 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	} 

	@RequestMapping(value="/{namespace}/{serviceType}/{serviceName}/alert/defaultRule",method = RequestMethod.PUT)
	public ResponseEntity<String> updateDefaultAlertRule(@PathVariable String namespace,@PathVariable String serviceType,@PathVariable String serviceName) throws Exception {
		try {
			String txId = txId();
			Result result = commonService.updateDefaultAlertRule(txId,namespace,serviceType,serviceName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, RequestEventCode.ALERTRULE_CREATE.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value="/{namespace}/{serviceType}/{serviceName}/alert/rule",method = RequestMethod.PUT)
	public ResponseEntity<String> updateAlertRule(@PathVariable String namespace,@PathVariable String serviceType,@PathVariable String serviceName
												  ,@RequestBody List<AlertingRuleEntity> alertRules) throws Exception {
		try {
			String txId = txId();
			Result result = commonService.updateAlertRule(txId,namespace,serviceType,serviceName,alertRules);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(null, IResult.ERROR, RequestEventCode.ALERTRULE_UPDATE.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	} 
	//@RequestMapping(value="/{namespace}/alert/rule/{alert}",method = RequestMethod.DELETE)
	//public ResponseEntity<String> deleteAlertRule(@PathVariable("namespace") String namespace,@PathVariable String alert,@RequestBody final AlertingRuleEntity alertingRuleEntity) throws Exception {
	//	try {
	//		String txId = txId();
	//		Result result = commonService.deleteAlertRule(txId,alertingRuleEntity);
	//		return new ResponseEntity<String>(result.toJson(), result.status());
	//	} catch (Exception e) {
	//		log.error(e.getMessage(), e);
	//		Result result = new Result(null, IResult.ERROR, RequestEvent.DELETE_ALERT_RULE + " 오류").putValue(IResult.EXCEPTION, e);
	//		return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
	//	}
	//}
	@RequestMapping(value="/{namespace}/{serviceType}/process/{podName}/{pid}",method = RequestMethod.DELETE)
	public ResponseEntity<String> killProcess(@PathVariable String namespace, @PathVariable String serviceType 
											 , @PathVariable String podName, @PathVariable String pid) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).killProcess(txId, namespace, podName,pid);
				break;
			case Redis:
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.PROCESS_KILL.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.PROCESS_KILL.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value="/{namespace}/{serviceType}/process/{podName}",method = RequestMethod.GET)
	public ResponseEntity<String> getProcesses(@PathVariable String namespace, @PathVariable String serviceType , @PathVariable String podName) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result.putValue(IResult.PROCESSES,((MariaDBServiceImpl) mariadbService).getProcesses(txId, namespace, podName));
				break;
			case Redis:
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.PROCESS_SELECT.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.PROCESS_SELECT.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value="/storages",method = RequestMethod.GET)
	public ResponseEntity<String> getStorages(
		@RequestParam(required=false) final String namespace,
		@RequestParam(required=false) final String keyword,
		@RequestParam(required=false) final String app,
		@RequestParam(required=false) final String storageClassName,
		@RequestParam(required=false) final String billingType,
		@RequestParam(required=false) final String phase,
		@RequestParam(required=false) final String stDate,
		@RequestParam(required=false) final String edDate
		) {
		try {
			Result result = mariadbService.getStorages(namespace, keyword,app,storageClassName, billingType, phase,stDate,edDate);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "스토리지 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/storages/data",method = RequestMethod.GET)
	public ResponseEntity<String> getStorageData() {
		try {
			Result result = mariadbService.getStoragesData();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "스토리지 데이터 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/nodesInfo",method = RequestMethod.GET)
	public ResponseEntity<String> getNodeInfo() {
		try {
			Result result = commonService.getNodesInfo();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, "스토리지 데이터 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/{namespace}/{serviceType}/database/status/{podName}",method = RequestMethod.GET)
	public ResponseEntity<String> getDatabaseStatus(@PathVariable String namespace, @PathVariable String serviceType , @PathVariable String podName) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result.putValue(IResult.DATABASE_STATUS,((MariaDBServiceImpl) mariadbService).getDatabaseStatus(txId, namespace, podName));
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_STATUS.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.DATABASE_STATUS.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/{namespace}/{serviceType}/database/connection/{podName}",method = RequestMethod.GET)
	public ResponseEntity<String> getDatabaseConnection(@PathVariable String namespace, @PathVariable String serviceType , @PathVariable String podName) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result.putValue(IResult.DATABASE_CONNECTION,((MariaDBServiceImpl) mariadbService).getDatabaseConnection(txId, namespace, podName));
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_CONNECTION.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.DATABASE_CONNECTION.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/{namespace}/{serviceType}/database/statusVariables/{podName}",method = RequestMethod.GET)
	public ResponseEntity<String> getDatabaseStatusVariables(@PathVariable String namespace, @PathVariable String serviceType , @PathVariable String podName) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result.putValue(IResult.DATABASE_STATUS_VARIABLES,((MariaDBServiceImpl) mariadbService).getDatabaseStatusVariables(txId, namespace, podName));
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_STATUS_VARIABLES.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.DATABASE_STATUS_VARIABLES + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/{namespace}/{serviceType}/database/systemVariables/{podName}",method = RequestMethod.GET)
	public ResponseEntity<String> getDatabaseSystemVariables(@PathVariable String namespace, @PathVariable String serviceType , @PathVariable String podName) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result.putValue(IResult.DATABASE_SYSTEM_VARIABLES,((MariaDBServiceImpl) mariadbService).getDatabaseSystemVariables(txId, namespace, podName));
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_STATUS_VARIABLES.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.DATABASE_STATUS_VARIABLES.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/{serviceType}/database/variables",method = RequestMethod.GET)
	public ResponseEntity<String> getDatabaseVariables(@PathVariable String serviceType) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result.putValue(IResult.DATABASE_VARIABLES,((MariaDBServiceImpl) mariadbService).getDatabaseVariables(txId));
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_VARIABLES.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.DATABASE_VARIABLES.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value="/{namespace}/{serviceType}/{serviceName}/database/useVariables",method=RequestMethod.GET)
	public ResponseEntity<String> getUseDatabaseVariables(@PathVariable String namespace,@PathVariable String serviceType,@PathVariable String serviceName ) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result.putValue(IResult.DATABASE_VARIABLES,((MariaDBServiceImpl) mariadbService).getUseDatabaseVariables(txId,namespace,serviceName));
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_VARIABLES.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.DATABASE_VARIABLES.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	@RequestMapping(value="/{namespace}/{serviceType}/{serviceName}/database/useVariables",method=RequestMethod.PUT)
	public ResponseEntity<String> updateUseDatabaseVariables(@PathVariable String namespace,@PathVariable String serviceType,@PathVariable String serviceName 
														     ,@RequestBody String data) throws Exception {
		Result result = null;
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType("mariadb");
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.CONFIG_UPDATE.getDesc());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());
			
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				Type resultType = new TypeToken<List<MariaDBVariable>>(){}.getType();
				List<MariaDBVariable> variables = new Gson().fromJson(data, resultType); 
				result = ((MariaDBServiceImpl) mariadbService).updateUseDatabaseVariables(txId,namespace,serviceName,variables);
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.DATABASE_UPDATE_VARIABLES.getDesc());
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
			result = new Result(txId, IResult.ERROR, RequestEventCode.CONFIG_UPDATE.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
	

	@RequestMapping(value="/{namespace}/{serviceType}/{serviceName}/database/userPrivileges",method=RequestMethod.GET)
	public ResponseEntity<String> getUserPrivileges(@PathVariable String namespace,@PathVariable String serviceType,@PathVariable String serviceName ) throws Exception {
		String txId = txId();
		Result result = new Result(txId,IResult.OK);
		
		try {
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).getUserPrivileges(txId,namespace,serviceName);
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.USER_PRIVILEGES.getDesc());
				break;
			}
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(null, IResult.ERROR, RequestEventCode.USER_PRIVILEGES.getDesc() + " 오류").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/{serviceName}/database/userPrivileges", method = RequestMethod.POST)
	public ResponseEntity<String> createUserPrivileges(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@RequestBody String data
			) {
		String txId = txId();
		Result result = null;
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.USER_CREATE.getDesc());
			event.setType(RequestEventCode.USER_CREATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());			
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				Type resultType = new TypeToken<MariadbUserPrivileges>(){}.getType();
				MariadbUserPrivileges userPrivileges = new Gson().fromJson(data, resultType);
				result = ((MariaDBServiceImpl) mariadbService).createUserPrivileges(txId,namespace,serviceName,userPrivileges);
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.USER_CREATE.getDesc());
				break;
			}
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());		
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, RequestEventCode.USER_CREATE.getDesc() + " 오류!").putValue(IResult.EXCEPTION, e);
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	@RequestMapping(value = "/{namespace}/{serviceType}/{serviceName}/database/userPrivileges", method = RequestMethod.PUT)
	public ResponseEntity<String> updateUserPrivileges(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@RequestBody String data
			) {
		String txId = txId();
		
		Result result = null;
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.USER_UPDATE.getDesc());
			event.setType(RequestEventCode.USER_UPDATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());			
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				Type resultType = new TypeToken<MariadbUserPrivileges>(){}.getType();
				MariadbUserPrivileges userPrivileges = new Gson().fromJson(data, resultType);
				log.debug("accountId: {}, accountPassword: {}", userPrivileges.getGrantee(), userPrivileges.getPassword());
				result = ((MariaDBServiceImpl) mariadbService).updateUserPrivileges(txId,namespace,serviceName,userPrivileges);
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.USER_UPDATE.getDesc());
				break;
			}

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());				
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, RequestEventCode.USER_UPDATE + " 오류!").putValue(IResult.EXCEPTION, e);
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/{serviceName}/database/userPrivileges", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteUserPrivileges(
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceName") final String serviceName,
			@RequestBody String data ) {
		String txId = txId();
		Result result = null;
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType(serviceType);
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.USER_DELETE_PRIVILEGES.getDesc());
			event.setType(RequestEventCode.USER_DELETE_PRIVILEGES.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());			
			ZDBType dbType = ZDBType.getType(serviceType);
			switch (dbType) {
			case MariaDB:
				Type resultType = new TypeToken<MariadbUserPrivileges>(){}.getType();
				MariadbUserPrivileges userPrivileges = new Gson().fromJson(data, resultType);
				log.debug("accountId: {}, accountPassword: {}", userPrivileges.getGrantee(), userPrivileges.getPassword());
				result = ((MariaDBServiceImpl) mariadbService).deleteUserPrivileges(txId,namespace,serviceName,userPrivileges);
				break;
			default:
				result = new Result(txId, IResult.ERROR, RequestEventCode.USER_DELETE_PRIVILEGES.getDesc());
				break;
			}
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());		
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, RequestEventCode.USER_DELETE_PRIVILEGES.getDesc() + " 오류!").putValue(IResult.EXCEPTION, e);
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
		}
	}
	
	/**
	 * Confirm Credential 
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/credentialConfirm", method = RequestMethod.POST)
	public ResponseEntity<String> getCredentialConfirm(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceType") final String serviceType, 
			        				     @PathVariable("serviceName") final String serviceName,
			        					 @RequestBody final String data) {

		try {
		    // mariadb , redis, postgresql, rabbitmq, mongodb		    
			ZDBType dbType = ZDBType.getType(serviceType);
			Result result = null;
			
		    switch (dbType) {
		    case MariaDB: 

				Type resultType = new TypeToken<CredentialConfirm>(){}.getType();
				CredentialConfirm credentialConfirm = new Gson().fromJson(data, resultType);
		    	result = mariadbService.getCredentialConfirm(namespace, serviceType, serviceName ,credentialConfirm.getCredential());
		    	break;
		    case Redis:
		    	//result = redisService.getConnectionInfo(namespace, serviceType, serviceName);
		    	break;
		    default:
		    	log.error("Credential 확인 - Not support.");
		    	result = new Result(null, IResult.ERROR, "Credential 확인- Not support.");
		    	break;
		    }
			
		    return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, "Credential 확인 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	

	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/userPrivileges/{user}/{host}/{schema}", method = RequestMethod.GET)
	public ResponseEntity<String> getUserPrivilegesForSchema(@PathVariable("namespace") final String namespace, 
			@PathVariable("serviceType") final String serviceType,
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("user") final String user,
			@PathVariable("host") final String host,
			@PathVariable("schema") final String schema ) {
		
		Result result = null;
		
		try {
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);

			switch (dbType) {
			case MariaDB:
				result = ((MariaDBServiceImpl) mariadbService).getUserPrivilegesForSchema(namespace, serviceType, serviceName ,user , host , schema);
				break;
			case Redis:
			case PostgreSQL:
			case RabbitMQ:
			case MongoDB:
			default:
				log.error("Not support.");
				result = new Result(null, IResult.ERROR, "사용자 권한 목록 조회 오류!");
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			result = new Result(null, IResult.ERROR, "사용자 권한 목록 조회 오류!").putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}	
	
	@RequestMapping(value = "/failover/{namespace}/{serviceName}/info", method = RequestMethod.GET)
	public ResponseEntity<String> getLastFailoverInfo(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			String lastFailoverTime = k8sService.getLastFailoverTime(namespace, serviceName);
			Result result = null;
			if(lastFailoverTime != null) {
				result = new Result(txId, IResult.OK).putValue(IResult.LAST_FAILOVER, lastFailoverTime);
			} else {
				result = new Result(txId, IResult.OK).putValue(IResult.LAST_FAILOVER, "-");
			}
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
	
	@RequestMapping(value = "/namespaces/{namespace}/services/{serviceName}/config", method = RequestMethod.PUT)
	public ResponseEntity<String> putMycnf(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			@RequestBody final String mycnf) {
//		RequestEvent event = new RequestEvent();
//		String txId = txId();
//		try {
//			UserInfo userInfo = getUserInfo();
//			event.setTxId(txId);
//			event.setStartTime(new Date(System.currentTimeMillis()));
//			event.setServiceType("mariadb");
//			event.setNamespace(namespace);
//			event.setServiceName(serviceName);
//			event.setOperation(RequestEventCode.CONFIG_UPDATE.getDesc());
//			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());		
//			
//			
//			String cnf = k8sService.putConfig(namespace, serviceName, mycnf);
//			Result result = Result.RESULT_OK.putValue(IResult.PUT_MY_CNF, cnf);
//			result.setMessage("환경설정이 변경.");
//			
//			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//			Result result = new Result(txId, IResult.ERROR, "조회 오류!").putValue(IResult.EXCEPTION, e);
//			
//			event.setStatus(result.getCode());
//			event.setResultMessage(result.getMessage());
//			
//			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
//		} finally {
//			event.setEndTime(new Date(System.currentTimeMillis()));
//			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
//		}	
		

		Result result = null;
		String txId = txId();
		
		RequestEvent event = new RequestEvent();
		try {
			UserInfo userInfo = getUserInfo();
			event.setTxId(txId);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setServiceType("mariadb");
			event.setNamespace(namespace);
			event.setServiceName(serviceName);
			event.setOperation(RequestEventCode.CONFIG_UPDATE.getDesc());
			event.setType(RequestEventCode.CONFIG_UPDATE.getType());
			event.setUserId(userInfo.getUserName() == null ? "SYSTEM" : userInfo.getUserName());	
			
			result = ((MariaDBServiceImpl) mariadbService).updateConfigV2(txId, namespace, serviceName, mycnf);

			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			Object history = result.getResult().get(Result.HISTORY);
			if (history != null) {
				event.setHistory("" + history);
			}
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, RequestEventCode.CONFIG_UPDATE.getDesc() +" 오류!").putValue(IResult.EXCEPTION, e);
			
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
}
