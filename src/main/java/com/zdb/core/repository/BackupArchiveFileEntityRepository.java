package com.zdb.core.repository;

import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zdb.core.domain.BackupArchiveFileEntity;

@Repository
public interface BackupArchiveFileEntityRepository extends CrudRepository<BackupArchiveFileEntity, String> {
	
	@Query("SELECT t FROM BackupArchiveFileEntity t "
			+ " WHERE backupArchiveFileId=:backupArchiveFileId" )
	BackupArchiveFileEntity findBackupArchiveFile(@Param("backupArchiveFileId") String backupArchiveFileId);
	
	@Query("SELECT t FROM BackupArchiveFileEntity t "
			+ " WHERE backupId=:backupId" )
	List<BackupArchiveFileEntity> findBackupArchiveFileListByBackupId(@Param("backupId") String backupId);
	
	@Query("SELECT t FROM BackupArchiveFileEntity t "
			+ " WHERE backupId=:backupId"
			+ " and deleteYn='N' ")
	List<BackupArchiveFileEntity> findBackupValidArchiveFileListByBackupId(@Param("backupId") String backupId);
	
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BackupFileEntity t SET "
			+ " t.deleteYn = 'Y' "
			+ " ,t.deleteDatetime = :deleteDatetime "
			+ " ,t.deleteDesc = :deleteDesc "
			+ "WHERE backupArchiveFileId=:backupArchiveFileId")
	int modify2FileDelete(@Param("backupFileId") String backupFileId
			, @Param("deleteDatetime") Date deleteDatetime
			, @Param("deleteDesc") Date deleteDesc
			);
	
}
