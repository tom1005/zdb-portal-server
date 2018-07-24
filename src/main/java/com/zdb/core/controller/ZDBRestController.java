package com.zdb.core.controller;

import java.io.FileNotFoundException;
import java.util.ArrayList;
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

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.UserInfo;
import com.zdb.core.domain.UserNamespaces;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.UserNamespaceRepository;
import com.zdb.core.service.MariaDBServiceImpl;
import com.zdb.core.service.RedisServiceImpl;
import com.zdb.core.service.ZDBRestService;

import io.fabric8.kubernetes.client.KubernetesClientException;
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
	private UserNamespaceRepository userNamespaceRepo;

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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new ResponseEntity<String>("Unauthorized", HttpStatus.UNAUTHORIZED);
			} else {
				return new ResponseEntity<String>(e.getMessage(), HttpStatus.UNAUTHORIZED);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			
			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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
		try {
			UserInfo userInfo = getUserInfo();
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
			ZDBType dbType = ZDBType.getType(serviceType);
			log.info("{}, {}, {}", userInfo.getUserId(), userInfo.getUserName(), userInfo.getAccessRole());

			com.zdb.core.domain.Result result = null;
			entity.setRequestUserId(userInfo.getUserId());
			
			switch (dbType) {
			case MariaDB:
				result = mariadbService.createDeployment(txId, entity, userInfo);
				break;
			case Redis:
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

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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
		try {
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;
			UserInfo userInfo = getUserInfo();
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

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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
		try {
			ZDBType dbType = ZDBType.getType(serviceType);

			com.zdb.core.domain.Result result = null;

			UserInfo userInfo = getUserInfo();
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

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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

		try {
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

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/restart", method = RequestMethod.GET)
	public ResponseEntity<String> restartService(@PathVariable("serviceType") final String serviceType, @PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName) {

		String txId = txId();
		ZDBType dbType = ZDBType.getType(serviceType);

		try {
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
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			com.zdb.core.domain.Result result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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
			result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

		try {
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

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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
			result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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
		try {
			result = ((MariaDBServiceImpl) mariadbService).createDBUser(txId, namespace, serviceName, account);
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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
		try {
			result = ((MariaDBServiceImpl) mariadbService).updateDBUser(txId(), namespace, serviceName, account);
			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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
		try {
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

			return new ResponseEntity<String>(result.toJson(), result.status());

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
//	@RequestMapping(value = "/{serviceType}/service/{namespace}/pvc/create", method = RequestMethod.POST)
//	public ResponseEntity<String> createPersistentVolumeClaim(@PathVariable("serviceType") String serviceType, @PathVariable("namespace") final String namespace, @RequestBody final PersistenceSpec pvcSpec,
//			final UriComponentsBuilder ucBuilder) {
//		
//		String txId = txId();
//		
//		// mariadb , redis, postgresql, rabbitmq, mongodb
//		ZDBType dbType = ZDBType.getType(serviceType);
//
//		Result result = null;
//
//		try {
//			switch (dbType) {
//			case MariaDB:
//				result = mariadbService.createPersistentVolumeClaim(txId, namespace, null, pvcSpec);
//				break;
//			case Redis:
//				result = redisService.createPersistentVolumeClaim(txId, namespace, null, pvcSpec);
//				break;
//			case PostgreSQL:
//				// TODO
//				break;
//			case RabbitMQ:
//				// TODO
//				break;
//			case MongoDB:
//				// TODO
//				break;
//			default:
//				log.error("Not support.");
//				result.setMessage("Not support service type.");
//				break;
//			}
//			return new ResponseEntity<String>(result.toJson(), result.status());
//			
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//			result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
//			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
//		}
//	}
	
	/**
	 * getting a Pod/Container Logs
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
	@RequestMapping(value = "/{namespace}/log/{podname}", method = RequestMethod.GET)
	public ResponseEntity<String> getPodLog(@PathVariable("namespace") final String namespace, @PathVariable("podname") final String pvcName) {
		try {
			Result result = mariadbService.getPodLog(namespace, pvcName);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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
			
			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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
			if(namespaces != null) {
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
			Result result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/service/services", method = RequestMethod.GET)
	public ResponseEntity<String> getServices() throws Exception {
		try {
			long s = System.currentTimeMillis();
			Result result = mariadbService.getAllServices();
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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
		
		try {
			//@RequestParam("newPassword") String newPassword
			String newPassword = param.get("newPassword");
			String secretType = param.get("secretType");
			
			if(newPassword == null || newPassword.isEmpty()) {
				Result result = new Result(null, IResult.ERROR, "새로운 password 입력하세요.");
				return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
			}
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
		    ZDBType dbType = ZDBType.getType(serviceType);
			
		    Result result = null;
		    
		    switch (dbType) {
		    case MariaDB: 
		    	result = mariadbService.setNewPassword(txId, namespace, serviceType, serviceName, newPassword);
		    	if(result.isOK()) {
		    		((MariaDBServiceImpl) mariadbService).updateAdminPassword(txId, namespace, serviceName, newPassword);
		    	}
		    	break;
		    case Redis:
		    	result = redisService.setNewPassword(txId, namespace, serviceType, serviceName, newPassword);
		    	break;
		    default:
		    	log.error("Not support.");
		    	result.setMessage("Not support service type.");
		    	break;
		    }
		    
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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
	public ResponseEntity<String> reStartPod(@PathVariable("namespace") final String namespace, 
										     @PathVariable("serviceType") final String serviceType, 
										     @PathVariable("serviceName") final String serviceName,
											 @PathVariable("podName") final String podName) {
		String txId = txId();

		try {
			com.zdb.core.domain.Result result = null;
			
			// mariadb , redis, postgresql, rabbitmq, mongodb
		    ZDBType dbType = ZDBType.getType(serviceType);
			
		    
		    switch (dbType) {
		    case MariaDB: 
		    	result = mariadbService.reStartPod(txId, namespace, serviceName, podName);
		    	break;
		    case Redis:
		    	result = redisService.reStartPod(txId, namespace, serviceName, podName);
		    	break;
		    default:
		    	log.error("Not support.");
		    	result.setMessage("Not support service type.");
		    	break;
		    }

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}

	}
	
	/**
	 * Getting result of Service alive check
	 * 
	 * @return ResponseEntity<List<Service>>
	 */
//	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/checkAlive", method = RequestMethod.GET)
//	public ResponseEntity<String> getServiceCheckAlive(@PathVariable("namespace") final String namespace, 
//			        				     @PathVariable("serviceType") final String serviceType, 
//			        				     @PathVariable("serviceName") final String serviceName ) {
//		try {
//		    ZDBType dbType = ZDBType.getType(serviceType);
//			
//		    Result result = null;
//		    
//		    switch (dbType) {
//		    case MariaDB: 
//		    	//TO-DO
//		    	break;
//		    case Redis:
//		    	result = redisService.getServiceCheckAlive(namespace, serviceType, serviceName);
//		    	break;
//		    default:
//		    	log.error("Not support.");
//		    	result.setMessage("Not support service type.");
//		    	break;
//		    }
//			
//		    return new ResponseEntity<String>(result.toJson(), result.status());
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//
//			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
//			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
//		}
//	}	
	
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
		
		try {
			
			Result result = ((MariaDBServiceImpl)mariadbService).updateConfig(txId, namespace, serviceName, config);
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/tag/{serviceName}", method = RequestMethod.POST)
	public ResponseEntity<String> createTag(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final Tag tag) {
		try {
			Result result = mariadbService.createTag(tag);
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
	
	@RequestMapping(value = "/{namespace}/tag/{serviceName}", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteTag(@PathVariable("namespace") final String namespace, 
			        				     @PathVariable("serviceName") final String serviceName, 
			        				     @RequestBody final Tag tag) {
		String txId = txId();
		
		try {
			Result result = mariadbService.deleteTag(tag);
			
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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

			Result result = new Result(null, IResult.ERROR, e.getMessage()).putValue(IResult.EXCEPTION, e);
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
	
}
