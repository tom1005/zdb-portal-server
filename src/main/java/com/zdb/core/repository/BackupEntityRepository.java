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
	
	@Query("SELECT t FROM BackupEntity t "
			+ " WHERE backupId=:backupId" )
	BackupEntity findBackup(@Param("backupId") String backupId);

	@Query("SELECT t FROM BackupEntity t "
			+ " WHERE serviceType=:serviceType"
			+ " and serviceName=:serviceName"
			+ " and startDatetime is not null"
			+ " order by startDatetime desc" )
	List<BackupEntity> findBackupByService(@Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	@Query(value =  "SELECT * FROM zdb.backup_entity "
            + " WHERE namespace=:namespace "
            + " and service_type=:serviceType "
            + " and service_name=:serviceName "
            + " order by accepted_datetime desc limit 1" , nativeQuery = true)
	BackupEntity findBackupStatus(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	@Query("SELECT t FROM BackupEntity t "
			+ " WHERE namespace=:namespace"
			+ " and serviceType=:serviceType"
			+ " and serviceName=:serviceName"
			+ " and status='OK'"
			+ " and DATE_FORMAT(start_datetime,'%Y%m%d%H') <= :expiredDate order by start_datetime desc" )
	List<BackupEntity> findExpiredBackup(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName
			, @Param("expiredDate") String expiredDate);

	@Query("SELECT t FROM BackupEntity t "
			+ " WHERE namespace=:namespace"
			+ " and serviceType=:serviceType"
			+ " and serviceName=:serviceName"
			+ " and status='OK'" )
	List<BackupEntity> findValidBackup(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
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
			+ ", t.status=:status "
			+ "WHERE t.backupId=:backupId")
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
	
	@Query("SELECT t from BackupEntity t WHERE t.scheduleId=:scheduleId and t.status='OK'" )
	List<BackupEntity> findBackupListByScheduleId(@Param("scheduleId") String scheduleId);

	@Query(value = "SELECT * from zdb.backup_entity "
			+ "WHERE namespace = :namespace "
			+ "and service_type = :serviceType "
			+ "and service_name = :serviceName "
			+ "and type = 'FULL' "
			+ "and status = 'OK' "
			+ "order by complete_datetime desc limit 1", nativeQuery = true)
	BackupEntity findFromBackup(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET "
			+ "t.ondisk = 'N' "
			+ "WHERE t.namespace=:namespace"
			+ " and t.serviceType=:serviceType"
			+ " and t.serviceName=:serviceName"
			+ " and t.ondisk = 'Y'"
			)
	int modify2OndiskDelete( @Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	
	@Query(value =  "SELECT * from zdb.backup_entity "
            + " WHERE namespace=:namespace "
            + " and service_type=:serviceType "
            + " and service_name=:serviceName "
            + " and ondisk='Y' "
            + " and status = 'OK' "
            + " and type = :type", nativeQuery = true)
	List<BackupEntity> findOndiskBackupList(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName
			, @Param("type") String type);
	
	@Query(value =  "SELECT * from zdb.backup_entity "
            + " WHERE namespace=:namespace "
            + " and service_type=:serviceType "
            + " and service_name=:serviceName "
            + " and DATE_FORMAT(accepted_datetime,'%Y%m%d%H') = :targetDate "
            + " and ondisk='Y' "
            + " and status = 'OK' "
            + " and type = 'INCR'", nativeQuery = true)
	List<BackupEntity> findOndiskIncrBackup(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName
			, @Param("targetDate") String targetDate);
	
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupEntity t SET "
			+ "t.ondisk = :ondiskYn "
			+ "WHERE t.backupId=:backupId")
	int modify2OndiskYn( @Param("ondiskYn") String ondiskYn
			, @Param("backupId") String backupId);
	
}
