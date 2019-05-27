package com.zdb.core.repository;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.BackupDiskEntity;

@Repository
public interface BackupDiskEntityRepository extends CrudRepository<BackupDiskEntity, String> {
	
	@Query("select t from BackupDiskEntity t where backupDiskId=:backupDiskId" )
	BackupDiskEntity findBackupDisk(@Param("backupDiskId") String backupDiskId);

	@Query(value =  "select * from zdb.backup_disk_entity "
            + " where namespace=:namespace "
            + " and service_type=:serviceType "
            + " and service_name=:serviceName "
            + " order by created_datetime desc limit 1" , nativeQuery = true)
	BackupDiskEntity findBackupByServiceName(@Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName
			, @Param("namespace") String namespace);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupDiskEntity t SET t.status=:status WHERE backupDiskId=:backupDiskId")
	int modify(@Param("backupDiskId") String backupDiskId
			, @Param("status") String status);
	
}
