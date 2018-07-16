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
import com.zdb.core.service.BackupProviderImpl;

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
	@Qualifier("backupProvider")
	private BackupProviderImpl backupProvider;

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

		if (log.isInfoEnabled()) {
			log.info(">>>> saveSchedule Interface :POST /{namespace}/{serviceType}/service/schedule {"
					+"namespace:"+namespace
					+",serviceType:"+serviceType
					+",serviceName:"+scheduleEntity.getServiceName()+"}");
		}
		String txId = txId();

		try {
			Result result = backupProvider.saveSchedule(txId, scheduleEntity);
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

		if (log.isInfoEnabled()) {
			log.info(">>>> getSchedule Interface :GET /{namespace}/{serviceType}/service/{serviceName}/schedule {"
					+"namespace:"+namespace
					+",serviceType:"+serviceType
					+",serviceName:"+serviceName+"}");
		}

		String txId = txId();

		try {
			Result result = backupProvider.getSchedule(txId, namespace, serviceName, serviceType);

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

		if (log.isInfoEnabled()) {
			log.info(">>>> backupService Interface :POST /{namespace}/{serviceType}/service/backup {"
					+"namespace:"+namespace
					+",serviceType:"+serviceType
					+",serviceName:"+backupEntity.getServiceName()+"}");
		}
		String txId = txId();
		Result result = null;

		try {
			result = backupProvider.backupService(txId, backupEntity);
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
		if (log.isInfoEnabled()) {
			log.info(">>>> getBackupList Interface :GET /{namespace}/{serviceType}/service/{serviceName}/backup-list {"
					+"namespace:"+namespace
					+",serviceType:"+serviceType
					+",serviceName:"+serviceName+"}");
		}

		Result result = null;
		String txId = txId();

		try {

			result = backupProvider.getBackupList(txId, namespace, serviceName, serviceType);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/{backupId}/delete", method = RequestMethod.GET)
	public ResponseEntity<String> deleteBackup(
			@PathVariable("namespace") final String namespace
			, @PathVariable("serviceType") final String serviceType
			, @PathVariable("serviceName") final String serviceName
			, @PathVariable("backupId") final String backupId) {
		if (log.isInfoEnabled()) {
			log.info(">>>> deleteBackup Interface :GET /{namespace}/{serviceType}/service/{serviceName}/{backupId}/delete {"
					+"namespace:"+namespace
					+",serviceType:"+serviceType
					+",serviceName:"+serviceName
					+",backupId:"+backupId+"}");
		}

		Result result = null;
		String txId = txId();

		try {

			result = backupProvider.deleteBackup(txId, namespace, serviceType, serviceName, backupId);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}

	////{namespace}/{serviceType}/service/{serviceName}/restore/{txId}
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/{backupId}/restore", method = RequestMethod.GET)
	public ResponseEntity<String> restoreFromBackup(
			@PathVariable("namespace") final String namespace
			, @PathVariable("serviceType") final String serviceType
			, @PathVariable("serviceName") final String serviceName
			, @PathVariable("backupId") final String backupId) {
		if (log.isInfoEnabled()) {
			log.info(">>>> deleteBackup Interface :GET /{namespace}/{serviceType}/service/{serviceName}/{backupId}/restore {"
					+"namespace:"+namespace
					+",serviceType:"+serviceType
					+",serviceName:"+serviceName
					+",backupId:"+backupId+"}");
		}

		String txId = txId();

		try {
			com.zdb.core.domain.Result result = null;
			result = backupProvider.restoreFromBackup(txId, namespace, serviceName, serviceType, backupId);
			return new ResponseEntity<String>(result.toJson(), result.status());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		}
	}
}
