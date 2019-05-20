package com.zdb.core.repository;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.BackupEntity;
import com.zdb.core.domain.BackupDiskEntity;

@Repository
public interface BackupDiskEntityRepository extends CrudRepository<BackupDiskEntity, String> {
	
	@Query("select t from BackupDiskEntity t where backupDiskId=:backupDiskId" )
	BackupDiskEntity findBackupDisk(@Param("backupDiskId") String backupDiskId);

	@Query("select t from BackupDiskEntity t where serviceType=:serviceType"
			+ " and serviceName=:serviceName" )
	BackupDiskEntity findBackupByServiceName(@Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupDiskEntity t SET t.status=:status WHERE backupDiskId=:backupDiskId")
	int modify(@Param("backupDiskId") String backupDiskId
			, @Param("status") String status);
	
}
