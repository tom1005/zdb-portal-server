package com.zdb.core.repository;

import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.BackupEntity;

/**
 * Repository for ZDBEntity
 * 
 * @author 06919
 *
 */
@Repository
public interface BackupEntityRepository extends CrudRepository<BackupEntity, String> {
	
	@Query("select t from BackupEntity t where backupId=:backupId" )
	BackupEntity findBackup(@Param("backupId") String backupId);

	@Query("select t from BackupEntity t where serviceType=:serviceType"
			+ " and serviceName=:serviceName"
			+ " and start_datetime is not null"
			+ " order by start_datetime desc" )
	List<BackupEntity> findBackupByService(@Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	@Query("select t from BackupEntity t where namespace=:namespace"
			+ " and serviceType=:serviceType"
			+ " and serviceName=:serviceName"
			+ " and status='OK'"
			+ " and DATE_FORMAT(start_datetime,'%Y%m%d') <= :expiredDate order by start_datetime desc" )
	List<BackupEntity> findExpiredBackup(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName
			, @Param("expiredDate") String expiredDate);

	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET"
			+ " t.acceptedDatetime=:acceptedDatetime"
			+ ", t.status=:status"
			+ ", t.reason=:reason "
			+ "WHERE t.backupId=:backupId")
	int modify2Accepted(@Param("acceptedDatetime") Date acceptedDatetime
			, @Param("status") String status
			, @Param("reason") String reason
			, @Param("backupId") String backupId);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET "
			+ "t.startDatetime=:startDatetime"
			+ ", t.status=:status WHERE t.backupId=:backupId")
	int modify2Started(@Param("startDatetime") Date startDatetime
			, @Param("status") String status
			, @Param("backupId") String backupId);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET "
			+ "t.createdDatetime=:createdDatetime"
			+ ", t.archivedDatetime=:archivedDatetime"
			+ ", t.filePath=:filePath"
			+ ", t.fileName=:fileName"
			+ ", t.fileSize=:fileSize"
			+ ", t.archiveName=:archiveName"
			+ ", t.archiveFileSize=:archiveFileSize"
			+ ", t.checkSum=:checkSum"
			+ ", t.status=:status "
			+ ", t.toLsn=:toLsn "
			+ ", t.fromBackupId=:fromBackupId "
			+ ", t.fromLsn=:fromLsn "
			+ ", t.type=:type "
			+ "WHERE t.backupId=:backupId")
	int modify2Archived(@Param("createdDatetime") Date createdDatetime
			, @Param("filePath") String filePath
			, @Param("fileName") String fileName
			, @Param("fileSize") long fileSize
			, @Param("archivedDatetime") Date archivedDatetime
			, @Param("archiveName") String archiveName
			, @Param("archiveFileSize") long archiveFileSize
			, @Param("checkSum") String checkSum
			, @Param("status") String status
			, @Param("backupId") String backupId
			, @Param("toLsn") long toLsn
			, @Param("fromBackupId") String fromBackupId
			, @Param("fromLsn") long fromLsn
			, @Param("type") String type
			);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET "
			+ "t.completeDatetime=:completeDatetime"
			+ ", t.endpointUrl=:endpointUrl"
			+ ", t.bucketName=:bucketName"
			+ ", t.status=:status "
			+ "WHERE t.backupId=:backupId")
	int modify2UploadDONE(@Param("completeDatetime") Date completeDatetime
			, @Param("endpointUrl") String endpointUrl
			, @Param("bucketName") String bucketName
			, @Param("status") String status
			, @Param("backupId") String backupId);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET"
			+ " t.completeDatetime=:completeDatetime"
			+ ", t.status=:status "
			+ ", t.reason=:reason "
			+ "WHERE t.backupId=:backupId")
	int modify2Completed(@Param("completeDatetime") Date completeDatetime
			, @Param("status") String status
			, @Param("reason") String reason
			, @Param("backupId") String backupId);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET"
			+ " t.deleteDatetime=:deleteDatetime"
			+ ", t.status='DELETED' "
			+ "WHERE t.namespace=:namespace"
			+ " and t.serviceType=:serviceType"
			+ " and t.serviceName=:serviceName"
			+ " and t.archiveName=:archiveName")
	int modify2Deleted(@Param("deleteDatetime") Date deleteDatetime
			, @Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName
			, @Param("archiveName") String archiveName);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET"
			+ " t.deleteDatetime=:deleteDatetime"
			+ ", t.status='DELETED' "
			+ "WHERE t.backupId=:backupId")
	int modify2Deleted(@Param("deleteDatetime") Date deleteDatetime
			, @Param("backupId") String backupId);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET t.acceptedDatetime=:acceptedDatetime, t.status='ACCEPTED', t.reason=:reason WHERE t.namespace=:namespace"
			+ " and t.serviceType=:serviceType"
			+ " and t.serviceName=:serviceName")
	int modifyServiceDeleting(@Param("acceptedDatetime") Date acceptedDatetime
			, @Param("reason") String reason
			, @Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	@Modifying
	@Transactional
	@Query("DELETE FROM BackupEntity t WHERE t.backupId=:backupId")
	int deleteBackup(@Param("backupId") String backupId);
	
	@Query("select t from BackupEntity t where t.scheduleId=:scheduleId and t.status='OK'" )
	List<BackupEntity> findBackupListByScheduleId(@Param("scheduleId") String scheduleId);
	
	@Query("select t from BackupEntity t where t.namespace=:namespace"
			+ " and t.serviceType=:serviceType"
			+ " and t.serviceName=:serviceName"
			+ " and scheduleYn='Y' and status = 'OK' and toLsn <> 0 and toLsn is not null order by createdDatetime desc" )
	List<BackupEntity> findFromBackup(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
}
