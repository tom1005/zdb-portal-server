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
			
			/*
			메타디비에서 기존에 저장된 ScheduleEntity를 조회하여 있으면 schedule를 갱신하고, 없으면 새로 저장합니다.
			새로 등록할때만 registerDate를 설정합니다.
			 */
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
			//2018-07-25 UI backup 목록 오류 수정
			result = new Result(txid, IResult.OK).putValue("backupSchedule", entity);
			event.setEndTime(new Date(System.currentTimeMillis()));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));
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
			/*
			Meta DB에서 아규먼트로 받은 namespace, serviceType, serviceName에 해당하는 스케줄을 조회하여 결과로 반환합니다.
			 */
			ScheduleEntity schedule = scheduleRepository.findScheduleByName(namespace, serviceType, serviceName);
			//2018-07-25 UI backup 목록 오류 수정
			result = new Result(txid, IResult.OK).putValue("schedule", schedule);
			event.setStatus(Result.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setResultMessage(e.getMessage());
			event.setStatus(IResult.ERROR);
			result = Result.RESULT_FAIL(txid, e);
		} finally {
			event.setEndTime(new Date(System.currentTimeMillis()));
			zdbRepository.save(event);
		}
		return result;
	}
	
/*
backupService 요청시, serviceType 구분없이 zdb-backup-agent로 요청을 전달합니다.
*/
	@Override
	public Result backupService(String txid
				, BackupEntity backupEntity) throws Exception {
		/*
		ZDB-BACKUP-AGENT에 Backup 요청을 위임하기 위해 RestTemplate을 생성하여
		/api/v1/{namespace}/{serviceType}/service/{serviceName}/backup/{txId}를 
		RestAPI URL로 설정하고 POST로 실행합니다.
		*/
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
			event.setEndTime(new Date(System.currentTimeMillis()));
			
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
			event.setEndTime(new Date(System.currentTimeMillis()));
			/*
			zdb-backup-agent의 요청 오류가 발생하면 해당 backup의 오류 상태를 DB에 
			저장하고 오류를 리턴합니다.
			*/
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
		/*
		ZDB META DB에서  namespace, serviceName, serviceType에 해당하는 백업 목록을 조회하여 회신합니다.
		 */
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
			
			List<BackupEntity> list = backupRepository.findBackupByService(serviceType, serviceName);
			//2018-07-25 UI backup 목록 오류 수정
			result = new Result(txid, IResult.OK).putValue("backupList", list);
			event.setEndTime(new Date(System.currentTimeMillis()));
			event.setStatus(IResult.OK);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				result = new Result(txid, Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				result = new Result(txid, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setStatus(IResult.ERROR);
			event.setEndTime(new Date(System.currentTimeMillis()));
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

		/*
		ZDB-BACKUP-AGENT에 Delete Backup 요청을 위임하기 위해 RestTemplate을 생성하여
		/api/v1/api/v1/service/backup/delete/{txId}를 
		RestAPI URL로 설정하고 POST로 실행합니다.
		*/
		Result result = null;
		RestTemplate restTemplate = new RestTemplate();
		
		RequestEvent event = new RequestEvent();
		event.setTxId(txid);
		event.setServiceName(serviceName);
		event.setServiceType(serviceType);
		event.setEventType(EventType.DeleteBackup.name());
		event.setNamespace(namespace);
		event.setStartTime(new Date(System.currentTimeMillis()));
		event.setEndTime(new Date(System.currentTimeMillis()));
		
		StringBuilder sb = new StringBuilder();
		try {
			//service/backup/delete/{txId}
			BackupEntity backup = backupRepository.findBackup(backupId);
			sb.append(K8SUtil.daemonUrl)
				.append("/api/v1/service/backup/delete/")
				.append(txid);
			result = restTemplate.postForObject(sb.toString(), backup, Result.class);
			event.setEndTime(new Date(System.currentTimeMillis()));
			if (result.isOK()) {
				event.setStatus(IResult.OK);
			} else {
				event.setStatusMessage(result.getMessage());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setEndTime(new Date(System.currentTimeMillis()));
			result = new Result(txid, Result.ERROR, e.getMessage(), e);
		} finally {
			zdbRepository.save(event);
		}
		return result;
	}
	
	@Override
	public Result restoreFromBackup(String txId, String namespace, String serviceType, String serviceName, String backupId) throws Exception {
		/*
		ZDB-BACKUP-AGENT에 Restore Backup을 전달하기 위해 RestTemplate을 생성하여
		/api/v1/api/v1/service/restore/{txId}를 
		RestAPI URL로 설정하고 POST으로 실행합니다.
		*/
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
			/*
			전달 받은 backupId에 해당하는 BackupEntity를 조회하여 POST의 Request Body로 전달합니다.
			*/
			BackupEntity backup = backupRepository.findBackup(backupId);
			//service/restore/{txId}
			sb.append(K8SUtil.daemonUrl)
				.append("/api/v1/service/restore/")
				.append(txId);
			result = restTemplate.postForObject(sb.toString(), backup, Result.class);
			event.setEndTime(new Date(System.currentTimeMillis()));
			event.setStatus(result.getCode());
			if (!result.isOK()) {
				event.setStatusMessage(result.getMessage());
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			event.setEndTime(new Date(System.currentTimeMillis()));
			result = new Result(txId, Result.ERROR, e.getMessage(), e);
		} finally {
			zdbRepository.save(event);
		}
		return result;
	}
	
	public Result removeServiceResource(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		/*
		ZDB-BACKUP-AGENT에 Service Resource를 삭제하기 위해 RestTemplate을 생성하여
		/api/v1/api/v1/{namespace}/{serviceType}/service/{serviceName}/delete/{txId}를 
		RestAPI URL로 설정하고 GET으로 실행합니다.
		*/
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
			event.setEndTime(new Date(System.currentTimeMillis()));
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
