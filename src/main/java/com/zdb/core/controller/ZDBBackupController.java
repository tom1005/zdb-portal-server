package com.zdb.core.controller;

import java.text.SimpleDateFormat;
import java.util.Date;
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
import com.zdb.core.exception.BackupException;
import com.zdb.core.service.BackupProviderImpl;
import com.zdb.core.service.K8SService;

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
	
	@Autowired
	private K8SService k8sService;

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
		Result result = null;

		try {
			/*
			1) verifyParameters
			아규먼트로 전달받은 scheduleEntity의 namespace, serviceType, serviceName은 null이거나 공백이면 안되므로 verify를 수행합니다.
			*/
			verifyParameters(scheduleEntity);
			/*
			2) verifyService
			아규먼트로 전달받은 scheduleEntity의 namespace, serviceType, serviceName에 해당하는 서비스가 존재하는지 검증합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyService(scheduleEntity.getNamespace(), serviceType, scheduleEntity.getServiceName());
			
			result = backupProvider.saveSchedule(txId, scheduleEntity);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (e instanceof BackupException) {
				result = new Result(txId, IResult.ERROR, e.getMessage());
				//result.setCode(((BackupException)e).getStatusCode());
			} else {
				result = new Result(txId, IResult.ERROR, "").putValue("error", e);
				result.setCode(HttpStatus.EXPECTATION_FAILED.value());
			}
		} 
		return new ResponseEntity<String>(result.toJson(), result.status());
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
					@PathVariable("namespace") final String namespace
					, @PathVariable("serviceType") String serviceType
					, @PathVariable("serviceName") final String serviceName) {

		if (log.isInfoEnabled()) {
			log.info(">>>> getSchedule Interface :GET /{namespace}/{serviceType}/service/{serviceName}/schedule {"
					+"namespace:"+namespace
					+",serviceType:"+serviceType
					+",serviceName:"+serviceName+"}");
		}

		String txId = txId();
		Result result = null;

		try {
			/*
			1) verifyParameters
			아규먼트로 전달받은 namespace, serviceType, serviceName은 null이거나 공백이면 안되므로 verify를 수행합니다.
			*/
			verifyParameters(namespace, serviceType, serviceName);
			/*
			2) verifyService
			아규먼트로 전달받은 namespace, serviceType, serviceName에 해당하는 서비스가 존재하는지 검증합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyService(namespace, serviceType, serviceName);
			
			result = backupProvider.getSchedule(txId, namespace, serviceName, serviceType);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (e instanceof BackupException) {
				result = new Result(txId, IResult.ERROR, e.getMessage());
				result.setCode(((BackupException)e).getStatusCode());
			} else {
				result = new Result(txId, IResult.ERROR, "").putValue("error", e);
				result.setCode(HttpStatus.EXPECTATION_FAILED.value());
			}
		} 
		return new ResponseEntity<String>(result.toJson(), result.status());
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
			/*
			1) verifyParameters
			아규먼트로 전달받은 namespace, serviceType, serviceName은 null이거나 공백이면 안되므로 verify를 수행합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyParameters(namespace, serviceType, backupEntity.getServiceName());
			/*
			2) verifyService
			아규먼트로 전달받은 namespace, serviceType, serviceName에 해당하는 서비스가 존재하는지 검증합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyService(namespace, serviceType, backupEntity.getServiceName());
			
			result = backupProvider.backupService(txId, backupEntity);
			log.info("===========> backupService returns!  serviceType : "
					+serviceType
					+", namespace : "
					+namespace+",serviceName : "+backupEntity.getServiceName());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (e instanceof BackupException) {
				result = new Result(txId, IResult.ERROR, e.getMessage());
				result.setCode(((BackupException)e).getStatusCode());
			} else {
				result = new Result(txId, IResult.ERROR, "").putValue("error", e);
				result.setCode(HttpStatus.EXPECTATION_FAILED.value());
			}
		} 
		return new ResponseEntity<String>(result.toJson(), result.status());
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
					@PathVariable("namespace") final String namespace
					, @PathVariable("serviceType") String serviceType
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
			/*
			1) verifyParameters
			아규먼트로 전달받은 namespace, serviceType, serviceName은 null이거나 공백이면 안되므로 verify를 수행합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyParameters(namespace, serviceType, serviceName);
			/*
			2) verifyService
			아규먼트로 전달받은 namespace, serviceType, serviceName에 해당하는 서비스가 존재하는지 검증합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyService(namespace, serviceType, serviceName);

			result = backupProvider.getBackupList(txId, namespace, serviceName, serviceType);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (e instanceof BackupException) {
				result = new Result(txId, IResult.ERROR, e.getMessage());
				result.setCode(((BackupException)e).getStatusCode());
			} else {
				result = new Result(txId, IResult.ERROR, "").putValue("error", e);
				result.setCode(HttpStatus.EXPECTATION_FAILED.value());
			}
		} 
		return new ResponseEntity<String>(result.toJson(), result.status());
	}

	
	@RequestMapping(value = "/{namespace}/{serviceType}/service/{serviceName}/{backupId}/delete", method = RequestMethod.DELETE)
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
			/*
			1) verifyParameters
			아규먼트로 전달받은 namespace, serviceType, serviceName은 null이거나 공백이면 안되므로 verify를 수행합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyParameters(namespace, serviceType, serviceName);
			/*
			2) verifyService
			아규먼트로 전달받은 namespace, serviceType, serviceName에 해당하는 서비스가 존재하는지 검증합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			verifyService(namespace, serviceType, serviceName);
			*/
			
			result = backupProvider.deleteBackup(txId, namespace, serviceType, serviceName, backupId);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (e instanceof BackupException) {
				result = new Result(txId, IResult.ERROR, e.getMessage());
				result.setCode(((BackupException)e).getStatusCode());
			} else {
				result = new Result(txId, IResult.ERROR, "").putValue("error", e);
				result.setCode(HttpStatus.EXPECTATION_FAILED.value());
			}
		} 
		return new ResponseEntity<String>(result.toJson(), result.status());
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
		Result result = null;

		try {
			/*
			1) verifyParameters
			아규먼트로 전달받은 namespace, serviceType, serviceName은 null이거나 공백이면 안되므로 verify를 수행합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyParameters(namespace, serviceType, serviceName);
			/*
			2) verifyService
			아규먼트로 전달받은 namespace, serviceType, serviceName에 해당하는 서비스가 존재하는지 검증합니다.
			이때 검증 오류가 발생하면 BackupException 발생시켜 오류를 리턴합니다.
			*/
			verifyService(namespace, serviceType, serviceName);
			result = backupProvider.restoreFromBackup(txId, namespace, serviceName, serviceType, backupId);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (e instanceof BackupException) {
				result = new Result(txId, IResult.ERROR, e.getMessage());
				result.setCode(((BackupException)e).getStatusCode());
			} else {
				result = new Result(txId, IResult.ERROR, "").putValue("error", e);
				result.setCode(HttpStatus.EXPECTATION_FAILED.value());
			}
		} 
		return new ResponseEntity<String>(result.toJson(), result.status());
	}


	private void verifyService(String namespace, String serviceType, String serviceName) throws BackupException {
		StringBuilder sb = new StringBuilder();
		try {
		
			if (k8sService.getServiceWithName(namespace, serviceType, serviceName) == null) {
//			if (K8SUtil.isServiceExist(namespace, serviceName) == false) {
				sb.append("Service(namespace:"+namespace+",serviceName:"+serviceName+") does not exits").append(",");
				throw new BackupException(sb.toString(), BackupException.NOT_FOUND);
			}
		} catch (Exception e) {
			if (e instanceof BackupException) {
				throw (BackupException)e;
			} else {
				sb.append("Service(namespace:"+namespace+",serviceName:"+serviceName+" has errors ").append(",");
				throw new BackupException(e);
			}
		}
	}
	
	private void verifyParameters(String namespace, String serviceType) throws BackupException {
		boolean result = true;
		StringBuilder sb = new StringBuilder();
		if ("".equals(namespace) || namespace == null) {
			result = false;
			sb.append("namespace empty or null");
		} else if("".equals(serviceType) || serviceType == null) {
			result = false;
			sb.append("serviceType empty or null");
		} else {
			if (log.isInfoEnabled()) {
				sb.append("Parameters OK {namespace:"+namespace
							+",serviceType:"+serviceType+"}");
				log.info(sb.toString());
			}
		}
		if (result == false) {
			throw new BackupException(sb.toString(), BackupException.BAD_REQUEST);
		}
	}
	
	private void verifyParameters(String namespace, String serviceType, String serviceName) throws BackupException {
		boolean result = true;
		StringBuilder sb = new StringBuilder();
		verifyParameters(namespace, serviceType);
		if ("".equals(serviceName) || serviceName == null) {
			result = false;
			sb.append("serviceName empty or null");
		}
		if (result == false) {
			throw new BackupException(sb.toString(), BackupException.BAD_REQUEST);
		}
	}
	private void verifyParameters(ScheduleEntity schedule) throws BackupException {
		boolean result = true;
		StringBuilder sb = new StringBuilder();
		if (schedule == null) {
			result = false;
			sb.append("scheduleEntity is null,");
		} else if("".equals(schedule.getStartTime()) || schedule.getStartTime() == null) {
			result = false;
			sb.append("startTime empty or null");
		} else if(schedule.getStorePeriod() < 1 || schedule.getStorePeriod() > 8) {
			result = false;
			sb.append("storePeriod more then 0 and less then 8 : "+schedule.getStorePeriod());
		} else if ("".equals(schedule.getUseYn()) || schedule.getUseYn()==null) {
			result = false;
			sb.append("useYn is empty or null : "+schedule.getStorePeriod());
		} else {
			verifyParameters(schedule.getNamespace(), schedule.getServiceType(), schedule.getServiceName());
			try {
				Date date = new SimpleDateFormat("HH:mm").parse(schedule.getStartTime());
				if (log.isInfoEnabled()) {
					log.info("Schedule requested to change to startTime("+date+")");
				}
				//업무 외 시간으로 백업 설정이 가능하도록 설정 
				if( !(Integer.parseInt(schedule.getStartTime().substring(0, 2)) > 18 
						|| Integer.parseInt(schedule.getStartTime().substring(0, 2)) < 8) ) {
					if (log.isInfoEnabled()) {
						log.info("Availble backup-schedule startTime : 18:00 ~ 07:00 / input time(" + schedule.getStartTime() + ")");
						sb.append("Availble backup-schedule startTime : 18:00 ~ 07:00 / "+schedule.getStartTime());
					}
					result = false;
				}
			} catch (Exception e) {
				sb.append("startTime is unknown format :"+schedule.getStartTime());
				result = false;
			}
		}
		if (result == false) {
			throw new BackupException(sb.toString(), BackupException.BAD_REQUEST);
		}
	}
}
