package com.zdb.core.repository;

import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.BackupAgentNotiEntity;

@Repository
public interface BackupAgentNotiEntityRepository extends CrudRepository<BackupAgentNotiEntity, String> {
	
	@Query("SELECT t FROM BackupAgentNotiEntity t WHERE backupAgentNotiId=:backupAgentNotiId" )
	BackupAgentNotiEntity findBackupAgentNoti(@Param("backupAgentNotiId") String backupAgentNotiId);

	@Query("SELECT t FROM BackupAgentNotiEntity t WHERE actionId=:actionId")
	List<BackupAgentNotiEntity> findBackupAgentNotiByActionId(@Param("actionId") String actionId);
	
	@Query("SELECT t FROM BackupAgentNotiEntity t WHERE actionType=:actionType")
	List<BackupAgentNotiEntity> findBackupAgentNotiByType(@Param("actionType") String actionType);
	
	@Query("SELECT t FROM BackupAgentNotiEntity t "
			+ " WHERE namespace=:namespace "
			+ " and serviceType=:serviceType"
			+ " and serviceName=:serviceName"
			+ " order by acceptedDatetime desc" )
	List<BackupAgentNotiEntity> findBackupAgentNotiByService(@Param("namespace") String namespace
			, @Param("serviceType") String serviceType
			, @Param("serviceName") String serviceName);
	
	@Query("SELECT t FROM BackupAgentNotiEntity t WHERE notiFlag='N'" )
	List<BackupAgentNotiEntity> findBackupAgentNotiByNotiYn();
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupAgentNotiEntity t SET"
			+ " t.notiFlag='Y' "
			+ " , t.notiDatetime=:notiDatetime "
			+ " WHERE t.backupAgentNotiId=:backupAgentNotiId")
	int modify2Accepted(@Param("backupNotiId") String backupNotiId
			,@Param("backupAgentNotiId") Date backupAgentNotiId);
	
}