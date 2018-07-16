package com.zdb.core.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.zdb.core.domain.BackupEntity;
import com.zdb.core.domain.BackupStatus;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ScheduleEntity;
import com.zdb.core.repository.BackupEntityRepository;
import com.zdb.core.repository.ScheduleEntityRepository;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("backupProvider")
@Configuration
public class BackupProviderImpl implements ZDBBackupProvider {

	@Autowired
	protected ZDBRepository zdbRepository;
	
	@Autowired
	BackupEntityRepository backupRepository;

	@Autowired
	ScheduleEntityRepository scheduleRepository;
	
	@Override
	public Result saveSchedule(String txid, ScheduleEntity entity) throws Exception {
		Result result = null;

		RequestEvent event = new RequestEvent();

		event.setTxId(txid);
		event.setServiceName(entity.getServiceName());
		event.setServiceType(entity.getServiceType());
		event.setEventType(EventType.BackupSchedule.name());
		event.setNamespace(entity.getNamespace());
		event.setStartTime(new Date(System.currentTimeMillis()));
		
		try {			
			log.debug("save : "+entity);
			
			ScheduleEntity oldSche = scheduleRepository.findScheduleByName(entity.getNamespace()
								, entity.getServiceType()
								, entity.getServiceName());
			if (oldSche != null) {
				log.debug("update : "+entity);
				scheduleRepository.modify(entity.getStartTime(), entity.getStorePeriod(), entity.getUseYn(), oldSche.getScheduleId());
				entity.setScheduleId(oldSche.getScheduleId());
			} else {
				entity.setRegisterDate(new Date(System.currentTimeMillis()));
				entity = scheduleRepository.save(entity);
			}
			result = new Result(txid, IResult.OK).putValue(EventType.BackupSchedule.name(), entity);
			event.setEndTIme(new Date(System.currentTimeMillis()));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			result = Result.RESULT_FAIL(txid, e);
		} finally {
			zdbRepository.save(event);
		}
		return result;
	}
	
	@Override
	public Result getSchedule(String txid
				, String namespace
				, String serviceName
				, String serviceType) throws Exception {
		Result result = null;
		
		
		RequestEvent event = new RequestEvent();
		event.setTxId(txid);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.BackupSchedule.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		
		try {			
			log.debug("namespace : "+namespace+", serviceName : "+serviceName+", serviceType : "+serviceType);
			ScheduleEntity schedule = scheduleRepository.findScheduleByName(namespace, serviceType, serviceName);
			result = new Result(txid, IResult.OK).putValue(EventType.BackupDetail.name(), schedule);
			event.setStatus(Result.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			result = Result.RESULT_FAIL(txid, e);
		} finally {
			event.setEndTIme(new Date(System.currentTimeMillis()));
			zdbRepository.save(event);
		}
		return result;
	}
	
	@Override
	public Result backupService(String txid
				, BackupEntity backupEntity) throws Exception {
		RestTemplate restTemplate = new RestTemplate();
		Result result = null;
		RequestEvent event = new RequestEvent();
		event.setTxId(txid);
		event.setServiceName(backupEntity.getServiceName());
		event.setServiceType(backupEntity.getServiceType());
		event.setEventType(EventType.Backup.name());
		event.setNamespace(backupEntity.getNamespace());
		event.setStartTime(new Date(System.currentTimeMillis()));
		try {
			log.debug("namespace : "+backupEntity.getNamespace()
				+", serviceName : "+backupEntity.getServiceName()
				+", serviceType : "+backupEntity.getServiceType());
			event.setResultMessage("Acceptiong");
			event.setEndTIme(new Date(System.currentTimeMillis()));
			
			StringBuilder sb = new StringBuilder();
			sb.append(K8SUtil.daemonUrl)
					.append("/api/v1/")
					.append(backupEntity.getNamespace()).append("/")
					.append(backupEntity.getServiceType())
					.append("/service/")
					.append(backupEntity.getServiceName())
					.append("/backup/")
					.append(txid);
			
			log.info(">>>>>>> uri : "+sb.toString());
			result = restTemplate.postForObject(sb.toString(), backupEntity, Result.class);
			event.setStatus(result.getCode());
		} catch(Exception e) {
			log.error(e.getMessage(), e);
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			backupEntity.setAcceptedDatetime(new Date(System.currentTimeMillis()));
			backupEntity.setStartDatetime(new Date(System.currentTimeMillis()));
			backupEntity.setCompleteDatetime(new Date(System.currentTimeMillis()));
			backupEntity.setStatus(BackupStatus.FAILED.name());
			backupEntity.setReason(e.getMessage());
			backupRepository.save(backupEntity);
			result = Result.RESULT_FAIL(txid, e);			
		} finally {
			zdbRepository.save(event);
		}
		return result;
	}
	
	@Override
	public Result getBackupList(String txid, String namespace, String serviceName, String serviceType) throws Exception {
		Result result = null;
		RequestEvent event = new RequestEvent();
		event.setTxId(txid);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.BackupList.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		
		try {
			log.debug("namespace : "+namespace+", serviceName : "+serviceName+", serviceType : "+serviceType);
			event.setResultMessage("Not supperted method requested");
			event.setEndTIme(new Date(System.currentTimeMillis()));
			///{namespace}/{serviceType}/service/{serviceName}/backup-list/txId
			List<BackupEntity> list = backupRepository.findBackupByService(serviceType, serviceName);
			result = new Result(txid, IResult.OK).putValue(EventType.BackupList.name(), list);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			event.setStatus(IResult.OK);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				result = new Result(txid, Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				result = new Result(txid, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			result = new Result(txid, Result.ERROR, e.getMessage(), e);
		} finally {
			zdbRepository.save(event);
		}
		return result;
	}
	
	@Override
	public Result deleteBackup(String txid
				, String namespace
				, String serviceType
				, String serviceName
				, String backupId) throws Exception {

		Result result = null;
		RestTemplate restTemplate = new RestTemplate();
		
		RequestEvent event = new RequestEvent();
		event.setTxId(txid);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.DeleteBackup.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setEndTIme(new Date(System.currentTimeMillis()));
		
		StringBuilder sb = new StringBuilder();
		try {
			//service/backup/delete/{txId}
			BackupEntity backup = backupRepository.findBackup(backupId);
			sb.append(K8SUtil.daemonUrl)
				.append("/api/v1/service/backup/delete/")
				.append(txid);
			result = restTemplate.postForObject(sb.toString(), backup, Result.class);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			if (result.isOK()) {
				event.setStatus(IResult.OK);
			} else {
				event.setStatusMessage(result.getMessage());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			result = new Result(txid, Result.ERROR, e.getMessage(), e);
		} finally {
			zdbRepository.save(event);
		}
		return result;
	}
	
	@Override
	public Result restoreFromBackup(String txId, String namespace, String serviceType, String serviceName, String backupId) throws Exception {
		Result result = null;
		RestTemplate restTemplate = new RestTemplate();
		
		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.Restore.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		
		StringBuilder sb = new StringBuilder();
		try {
			BackupEntity backup = backupRepository.findBackup(backupId);
			//service/restore/{txId}
			sb.append(K8SUtil.daemonUrl)
				.append("/api/v1/service/restore/")
				.append(txId);
			result = restTemplate.postForObject(sb.toString(), backup, Result.class);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			event.setStatus(result.getCode());
			if (!result.isOK()) {
				event.setStatusMessage(result.getMessage());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			result = new Result(txId, Result.ERROR, e.getMessage(), e);
		} finally {
			zdbRepository.save(event);
		}
		return result;
	}
	
	public Result removeServiceResource(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		RequestEvent event = new RequestEvent();
		Result result = null;
		try {
			event.setTxId(txId);
			event.setServiceName(serviceName);
			event.setServiceType(serviceType);
			event.setEventType(EventType.Delete.name());
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			
			log.debug("namespace : "+namespace
					+", serviceName : "+serviceName
					+", serviceType : "+serviceType);
			event.setResultMessage("ServiceResource("+serviceName+") to delete");
			StringBuilder sb = new StringBuilder();
			sb.append(K8SUtil.daemonUrl)
					.append("/api/v1/")
					.append(namespace).append("/")
					.append(serviceType)
					.append("/service/")
					.append(serviceName)
					.append("/delete/")
					.append(txId);
			RestTemplate restTemplate = new RestTemplate();
			log.info(">>>> uri : "+sb.toString());
			event.setStatus(IResult.RUNNING);
			result = restTemplate.getForObject(sb.toString(), Result.class);
		} catch (Exception e) {
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			event.setResultMessage("ServiceResource("+serviceName+") to delete not found");
		} finally {
			zdbRepository.save(event);
		}		
		return result;
	}
	
	
	public Result updateDBInstanceConfiguration(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
		return null;
	}
}
