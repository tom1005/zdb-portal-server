package com.zdb.core.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.zdb.core.domain.BackupEntity;
import com.zdb.core.domain.BackupStatus;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ScheduleEntity;
import com.zdb.core.domain.ScheduleInfoEntity;
import com.zdb.core.repository.BackupEntityRepository;
import com.zdb.core.repository.ScheduleEntityRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("backupProvider")
@Configuration
public class BackupProviderImpl implements ZDBBackupProvider {

	@Autowired
	BackupEntityRepository backupRepository;

	@Autowired
	ScheduleEntityRepository scheduleRepository;

	@Override
	public Result saveSchedule(String txid, ScheduleEntity entity) throws Exception {
		Result result = null;
		
		try {			
			log.info("saveSchedule : "+ new Gson().toJson(entity));
			
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
			result = new Result(txid, IResult.OK, "백업 스케쥴 변경 완료.").putValue("backupSchedule", entity);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = Result.RESULT_FAIL(txid, e);
		} 
		return result;
	}
	
	@Override
	public Result getSchedule(String txid
				, String namespace
				, String serviceName
				, String serviceType) throws Exception {
		Result result = null;
		
		try {			
			log.debug("namespace : "+namespace+", serviceName : "+serviceName+", serviceType : "+serviceType);
			/*
			Meta DB에서 아규먼트로 받은 namespace, serviceType, serviceName에 해당하는 스케줄을 조회하여 결과로 반환합니다.
			 */
			ScheduleEntity schedule = scheduleRepository.findScheduleByName(namespace, serviceType, serviceName);
			//2018-07-25 UI backup 목록 오류 수정
			result = new Result(txid, IResult.OK).putValue(IResult.SCHEDULE, schedule);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = Result.RESULT_FAIL(txid, e);
		} finally {
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
		try {
			log.debug("namespace : "+backupEntity.getNamespace()
				+", serviceName : "+backupEntity.getServiceName()
				+", serviceType : "+backupEntity.getServiceType());
			
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
		} catch(Exception e) {
			log.error(e.getMessage(), e);
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
		} 
		return result;
	}
	
	@Override
	public Result getBackupList(String txid, String namespace, String serviceName, String serviceType) throws Exception {
		/*
		ZDB META DB에서  namespace, serviceName, serviceType에 해당하는 백업 목록을 조회하여 회신합니다.
		 */
		Result result = null;
		
		try {
			log.debug("namespace : "+namespace+", serviceName : "+serviceName+", serviceType : "+serviceType);
			
			List<BackupEntity> list = backupRepository.findBackupByService(serviceType, serviceName);
			
			Calendar cal = Calendar.getInstance();
			list.forEach( i -> {
				cal.setTime(i.getAcceptedDatetime());
				cal.add(Calendar.HOUR_OF_DAY, 9);
				i.setAcceptedDatetime(cal.getTime());
				
				if(i.getArchivedDatetime() != null) {
					cal.setTime(i.getArchivedDatetime());
					cal.add(Calendar.HOUR_OF_DAY, 9);
					i.setArchivedDatetime(cal.getTime());
				}
				
				if(i.getCompleteDatetime() != null) {
					cal.setTime(i.getCompleteDatetime());
					cal.add(Calendar.HOUR_OF_DAY, 9);
					i.setCompleteDatetime(cal.getTime());
				}
				
				if(i.getCreatedDatetime() != null) {
					cal.setTime(i.getCreatedDatetime());
					cal.add(Calendar.HOUR_OF_DAY, 9);
					i.setCreatedDatetime(cal.getTime());
				}
				
				if(i.getDeleteDatetime() != null) {
					cal.setTime(i.getDeleteDatetime());
					cal.add(Calendar.HOUR_OF_DAY, 9);
					i.setDeleteDatetime(cal.getTime());
				}
				
				if(i.getStartDatetime() != null) {
					cal.setTime(i.getStartDatetime());
					cal.add(Calendar.HOUR_OF_DAY, 9);
					i.setStartDatetime(cal.getTime());
				}
				
			});
			
			
			
			//2018-07-25 UI backup 목록 오류 수정
			result = new Result(txid, IResult.OK).putValue("backupList", list);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				result = new Result(txid, Result.UNAUTHORIZED, "Unauthorized", null);
			} else {
				result = new Result(txid, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txid, Result.ERROR, e.getMessage(), e);
		} finally {
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
		
		StringBuilder sb = new StringBuilder();
		try {
			//service/backup/delete/{txId}
			BackupEntity backup = backupRepository.findBackup(backupId);
			sb.append(K8SUtil.daemonUrl)
				.append("/api/v1/service/backup/delete/")
				.append(txid);
			result = restTemplate.postForObject(sb.toString(), backup, Result.class);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txid, Result.ERROR, e.getMessage(), e);
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
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, Result.ERROR, e.getMessage(), e);
		} 
		return result;
	}
	
	public Result removeServiceResource(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		/*
		ZDB-BACKUP-AGENT에 Service Resource를 삭제하기 위해 RestTemplate을 생성하여
		/api/v1/api/v1/{namespace}/{serviceType}/service/{serviceName}/delete/{txId}를 
		RestAPI URL로 설정하고 GET으로 실행합니다.
		*/
		Result result = null;
		try {
			log.debug("namespace : "+namespace
					+", serviceName : "+serviceName
					+", serviceType : "+serviceType);
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
			result = restTemplate.getForObject(sb.toString(), Result.class);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} 	
		return result;
	}
	
	
	public Result updateDBInstanceConfiguration(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception {
		return null;
	}

	public Result getSchedule(String txId, String namespace) {
		Result result = null;
		
		try {		
			log.debug("namespace : "+namespace);
			List<ScheduleEntity> schedulelist = null;
			if(namespace.equals("all")) {
				schedulelist = scheduleRepository.findScheduleByNamespace();
			}else {
				schedulelist = scheduleRepository.findScheduleByNamespace(namespace);
			}
			List<ScheduleInfoEntity> scheduleInfolist = new ArrayList<ScheduleInfoEntity>();
			
			Calendar cal = Calendar.getInstance();
			schedulelist.forEach(i -> {
				ScheduleInfoEntity scheduleInfo = new ScheduleInfoEntity();
				scheduleInfo.setNamespace(i.getNamespace());
				scheduleInfo.setServiceName(i.getServiceName());
				scheduleInfo.setServiceType(i.getServiceType());
				
				if(i.getRegisterDate() != null) {
					cal.setTime(i.getRegisterDate());
					cal.add(Calendar.HOUR_OF_DAY, 9);
					scheduleInfo.setRegisterDate(cal.getTime());
				}
				scheduleInfo.setStartTime(i.getStartTime());
				scheduleInfo.setStorePeriod(i.getStorePeriod());
				
				List<BackupEntity> backuplist = backupRepository.findBackupListByScheduleId(i.getScheduleId());
				backuplist.forEach(backup -> {
					if(scheduleInfo.getStartDatetime() == null) {
						scheduleInfo.setStartDatetime(backup.getStartDatetime());
						scheduleInfo.setCompleteDatetime(backup.getCompleteDatetime());
						scheduleInfo.setExecutionMilsec(backup.getCompleteDatetime().getTime()-backup.getAcceptedDatetime().getTime());
						scheduleInfo.setFileSize(backup.getFileSize());
					}else if(scheduleInfo.getStartDatetime().before(backup.getStartDatetime())) {
						scheduleInfo.setStartDatetime(backup.getStartDatetime());
						scheduleInfo.setCompleteDatetime(backup.getCompleteDatetime());
						scheduleInfo.setExecutionMilsec(backup.getCompleteDatetime().getTime()-backup.getAcceptedDatetime().getTime());
						scheduleInfo.setExecutionTime(getExecutionTimeConvertion(backup.getCompleteDatetime().getTime()-backup.getAcceptedDatetime().getTime()));
						scheduleInfo.setFileSize(backup.getFileSize());
					}
					scheduleInfo.setFileSumSize(scheduleInfo.getFileSize() + backup.getArchiveFileSize());
				});
				scheduleInfo.setExecutionTime(getExecutionTimeConvertion(scheduleInfo.getExecutionMilsec()));
				scheduleInfolist.add(scheduleInfo);
			});
			result = new Result(txId, IResult.OK).putValue(IResult.SCHEDULE_INFO_LIST, scheduleInfolist);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = Result.RESULT_FAIL(txId, e);
		} finally {
		}
		return result;
	}
	
	private String getExecutionTimeConvertion(long milsec) {
		if(milsec == 0 ) {
			return "";
		}else if (milsec >= 3600000) {
			return Long.toString(milsec/3600000) + "시간 " + getExecutionTimeConvertion(milsec%3600000);
		} else if (milsec >= 60000) {
			return Long.toString(milsec/60000) + "분 " + getExecutionTimeConvertion(milsec%60000);
		} else {
			return Long.toString(milsec/1000) + "초";
		}
	}
}