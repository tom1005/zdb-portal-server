package com.zdb.core.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.zdb.core.domain.BackupEntity;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ScheduleEntity;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.service.MariaDBBackupServiceImpl;
import com.zdb.core.service.RedisBackupServiceImpl;

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
public class ZDBBackupController {

	@Autowired
	@Qualifier("mariadbBackupService")
	private MariaDBBackupServiceImpl mariadbBackupService;

	@Autowired
	@Qualifier("redisBackupService")
	private RedisBackupServiceImpl redisBackupService;

	private String txId() {
		return UUID.randomUUID().toString();
	}
	
	/**
	 * Registration of Backup Schedule
	 * @param serviceType
	 * @param namespace
	 * @param serviceName
	 * @param schedule
	 * @param ucBuilder
	 * @return
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/schedule", method = RequestMethod.POST)
	public ResponseEntity<String> saveSchedule(
					@PathVariable("serviceType") String serviceType
					, @PathVariable("namespace") final String namespace
					, @RequestBody final ScheduleEntity scheduleEntity
					, final UriComponentsBuilder ucBuilder) {

		String txId = txId();

		ZDBType dbType = ZDBType.getType(serviceType);

		try {
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbBackupService.saveSchedule(txId, scheduleEntity);
				break;
			case Redis:
				result = redisBackupService.saveSchedule(txId, scheduleEntity);
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
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	/**
	 * Querying for Backup Schedule
	 * @param serviceType
	 * @param namespace
	 * @param serviceName
	 * @param schedule
	 * @param ucBuilder
	 * @return
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/schedule", method = RequestMethod.GET)
	public ResponseEntity<String> getSchedule(
					@PathVariable("serviceType") String serviceType
					, @PathVariable("namespace") final String namespace
					, @PathVariable("serviceName") final String serviceName) {

		String txId = txId();

		ZDBType dbType = ZDBType.getType(serviceType);

		try {
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbBackupService.getSchedule(txId, namespace, serviceName, serviceType);
				break;
			case Redis:
				result = redisBackupService.getSchedule(txId, namespace, serviceName, serviceType);
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
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}

	}

	/**
	 * Request to Invoke an instant backup
	 * @param serviceType
	 * @param namespace
	 * @param serviceName
	 * @param ucBuilder
	 * @return
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/backup", method = RequestMethod.POST)
	public ResponseEntity<String> backupService(
					@PathVariable("serviceType") String serviceType
					, @PathVariable("namespace") final String namespace
					, @RequestBody final BackupEntity backupEntity) {
//					, final UriComponentsBuilder ucBuilder) {
		Result result = null;
		log.info("===========> backupService invoked!  serviceType : "
					+serviceType
					+", namespace : "
					+namespace+",serviceName : "+backupEntity.getServiceName()
					+", serviceType : "+serviceType);
		String txId = txId();

		ZDBType dbType = ZDBType.getType(serviceType);
		
		try {
			backupEntity.setNamespace(namespace);
			backupEntity.setServiceType(serviceType);
			backupEntity.setScheduleYn("N");

			switch (dbType) {
			case MariaDB:
				result = mariadbBackupService.backupService(txId, backupEntity);
				break;
			case Redis:
				result = redisBackupService.backupService(txId, backupEntity);
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
				break;
			}
			log.info("===========> backupService returns!  serviceType : "
					+serviceType
					+", namespace : "
					+namespace+",serviceName : "+backupEntity.getServiceName());

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (e instanceof NullPointerException) {
				return new ResponseEntity<String>(HttpStatus.EXPECTATION_FAILED);
			} else {
				result = new Result(txId, IResult.ERROR, "").putValue("error", e);
				return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
			}
		}

	}

	/**
	 * Querying for BackupJob List
	 * @param serviceType
	 * @param namespace
	 * @param serviceName
	 * @return
	 */
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/backup-list", method = RequestMethod.GET)
	public ResponseEntity<String> getBackupList(
					@PathVariable("serviceType") String serviceType
					, @PathVariable("namespace") final String namespace
					, @PathVariable("serviceName") final String serviceName) {

		String txId = txId();

		ZDBType dbType = ZDBType.getType(serviceType);

		try {
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbBackupService.getBackupList(txId, namespace, serviceName, serviceType);
				break;
			case Redis:
				result = redisBackupService.getBackupList(txId, namespace, serviceName, serviceType);
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
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}

	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/{backupId}/delete", method = RequestMethod.DELETE)
	public ResponseEntity<String> deleteBackup(
			@PathVariable("namespace") final String namespace
			, @PathVariable("serviceType") final String serviceType
			, @PathVariable("serviceName") final String serviceName
			, @PathVariable("backupId") final String backupId) {

		String txId = txId();

		ZDBType dbType = ZDBType.getType(serviceType);

		try {
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbBackupService.deleteBackup(txId, namespace, serviceType, serviceName, backupId);
				break;
			case Redis:
				result = redisBackupService.deleteBackup(txId, namespace, serviceType, serviceName, backupId);
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
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/{backupId}/restore", method = RequestMethod.POST)
	public ResponseEntity<String> restoreFromBackup(
			@PathVariable("namespace") final String namespace
			, @PathVariable("serviceType") final String serviceType
			, @PathVariable("serviceName") final String serviceName
			, @PathVariable("backupId") final String backupId) {

		String txId = txId();

		ZDBType dbType = ZDBType.getType(serviceType);

		try {
			com.zdb.core.domain.Result result = null;

			switch (dbType) {
			case MariaDB:
				result = mariadbBackupService.restoreFromBackup(txId, namespace, serviceName, serviceType, backupId);
				break;
			case Redis:
				result = redisBackupService.restoreFromBackup(txId, namespace, serviceName, serviceType, backupId);
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
				break;
			}

			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
}
