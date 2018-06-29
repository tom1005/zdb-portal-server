package com.zdb.core.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.zdb.core.domain.BackupEntity;
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
@Configuration
public class AbstractBackupServiceImpl implements ZDBBackupService {

	@Autowired
	protected ZDBRepository zdbRepository;
	
	@Autowired
	protected  ScheduleEntityRepository scheduleRepository;
	
	@Autowired
	BackupEntityRepository backupRepository;
	
	@Override
	public Result saveSchedule(String txid, ScheduleEntity entity) throws Exception {
		String serviceName = entity.getServiceName();//String.format("%s-%s-%s", cluster, service.getNamespace(), service.getServiceName());

		// 占쎈쐻占쎈윞占쎈쭓�뜝�럥�몡�넭怨ｋ쳳獒뺧옙 占쎈쐻占쎈윪占쎈�듸┼�슪�맔占쎌굲 占쎈쐻占쎈윪占쎌젳占쎌녃域밟뫁�굲 占쎈섀饔낅챸占썩뼺鍮녑뜝占�
		RequestEvent event = new RequestEvent();

		event.setTxId(txid);
		event.setServiceName(entity.getServiceName());
		event.setServiceType(entity.getServiceType());
		event.setEventType(EventType.BackupSchedule.name());
		event.setNamespace(entity.getNamespace());
		event.setStartTime(new Date(System.currentTimeMillis()));
		try {			
			log.debug("save : "+entity);
			ScheduleEntity oldSche = scheduleRepository.findScheduleByName(entity.getNamespace(), entity.getServiceType(), entity.getServiceName());
			if (oldSche != null) {
				log.debug("update : "+entity);
				scheduleRepository.modify(entity.getStartTime(), entity.getStorePeriod(), entity.getUseYn(), oldSche.getScheduleId());
			} else {
				scheduleRepository.save(entity);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			return Result.RESULT_FAIL(txid, e);
		} finally {
			zdbRepository.save(event);
		}
		return new Result(txid, Result.OK).putValue("backupSchedule", entity);
	}
	
	@Override
	public Result getSchedule(String txid, String namespace, String serviceName, String serviceType) throws Exception {
		RequestEvent event = new RequestEvent();
		Result result = null;
		event.setTxId(txid);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.BackupSchedule.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		try {			
			log.debug("namespace : "+namespace+", serviceName : "+serviceName+", serviceType : "+serviceType);
			ScheduleEntity schedule = scheduleRepository.findScheduleByName(namespace, serviceType, serviceName);
			log.debug("schedule : "+ schedule);
			result = new Result(txid, Result.OK).putValue("getSchedule", schedule);
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
	public Result backupService(String txid, BackupEntity backupEntity) throws Exception {
		RequestEvent event = new RequestEvent();
		Result result = null;
		RestTemplate restTemplate = new RestTemplate();
		event.setTxId(txid);
		event.setServiceName(backupEntity.getServiceName());
		event.setServiceType(backupEntity.getServiceType());
		event.setEventType(EventType.Backup.name());
		event.setNamespace(backupEntity.getNamespace());
		event.setStartTime(new Date(System.currentTimeMillis()));
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
				.append("/backup/")
				.append(txid);
		
		log.info(">>>>>>> uri : "+sb.toString());
		
		zdbRepository.save(event);
		return restTemplate.postForObject(sb.toString(), backupEntity, Result.class);
	}
	
	@Override
	public Result getBackupList(String txid, String namespace, String serviceName, String serviceType) throws Exception {
		RequestEvent event = new RequestEvent();
		Result result = null;
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
			List<BackupEntity> list = backupRepository.findBackupByService(serviceType, serviceName);
			if (list != null) {
				result = new Result(txid, Result.OK).putValue("backupList", list);
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			event.setEndTIme(new Date(System.currentTimeMillis()));
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				result = new Result(txid, Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				result = new Result(txid, Result.UNAUTHORIZED, e.getMessage(), e);
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
	public Result deleteBackup(String txid
				, String namespace
				, String serviceType
				, String serviceName
				, String backupId) throws Exception {

		RequestEvent event = new RequestEvent();
		Result result = null;
		event.setTxId(txid);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.DeleteBackup.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setEndTIme(new Date(System.currentTimeMillis()));
		BackupEntity backup = backupRepository.findBackup(backupId);
		
		if (backup != null) {
			log.debug("namespace : "+namespace
					+", serviceName : "+serviceName
					+", serviceType : "+serviceType);
			event.setResultMessage("Delete Backup requested");
			StringBuilder sb = new StringBuilder();
			sb.append(K8SUtil.daemonUrl)
					.append("/api/v1/service/backup/delete/")
					.append(txid);
			RestTemplate restTemplate = new RestTemplate();
			log.info(">>>> uri : "+sb.toString());
			result = restTemplate.postForObject(sb.toString(), backup, Result.class);
			zdbRepository.save(event);
		} else {
			result = new Result(txid, IResult.ERROR).putValue("DeleteBackup", backupId+" not found");
		}
		return result;
	}
	
	@Override
	public Result restoreFromBackup(String txId, String namespace, String serviceType, String serviceName, String backupId) throws Exception {
		RequestEvent event = new RequestEvent();
		Result result = null;
		event.setTxId(txId);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.Restore.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		BackupEntity backup = backupRepository.findBackup(backupId);
		if (backup != null) {
			event.setResultMessage("Accepted");
			
			StringBuilder sb = new StringBuilder();
			sb.append(K8SUtil.daemonUrl)
					.append("/api/v1/")
					.append(namespace).append("/")
					.append(serviceType)
					.append("/service/")
					.append(serviceName)
					.append("/restore/")
					.append(txId);
			RestTemplate restTemplate = new RestTemplate();
			log.info(">>>> uri : "+sb.toString());
			
			result = restTemplate.postForObject(sb.toString()
					, backup
					, Result.class);
			log.debug("restoreBackup accepted {namespace : "+namespace+", serviceName : "+serviceName+", serviceType : "+serviceType+"}");
		} else {
			event.setResultMessage(backupId+" not found");
			result = new Result(txId, IResult.ERROR).putValue("restore", backupId+" not found");
			log.debug("restoreBackup not found {namespace : "+namespace+", serviceName : "+serviceName+", serviceType : "+serviceType+"}");
		}
		event.setEndTIme(new Date(System.currentTimeMillis()));
		zdbRepository.save(event);
		return result;
	}
	
	public Result updateDBInstanceConfiguration(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
		return null;
	}
}
