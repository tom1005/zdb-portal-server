package com.zdb.core.service;

import com.zdb.core.domain.BackupEntity;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ScheduleEntity;

/**
 * ZDBRestService interface
 * 
 */
public interface ZDBBackupProvider {
	
	static final int MAX_QUEUE_SIZE = 10;
	
	/**
	 * 
	 * @param txid
	 * @param entity
	 * @return
	 * @throws Exception
	 */
	Result saveSchedule(String txid, ScheduleEntity entity) throws Exception;
	
	/**
	 * 
	 * @param txid
	 * @param namespace
	 * @param serviceName
	 * @param serviceType
	 * @return
	 * @throws Exception
	 */
	Result getSchedule(String txid, String namespace, String serviceName, String serviceType) throws Exception;
	
	/**
	 * 
	 * @param txid
	 * @param isScheduled
	 * @param namespace
	 * @param backupEntity
	 * @param serviceType
	 * @return
	 * @throws Exception
	 */
	Result backupService(String txid, BackupEntity backupEntity) throws Exception;
	
	/**
	 * 
	 * @param txid
	 * @param namespace
	 * @param serviceName
	 * @param serviceType
	 * @return
	 * @throws Exception
	 */
	Result getBackupList(String txid, String namespace, String serviceName, String serviceType) throws Exception;
	
	/**
	 * 
	 * @param txid
	 * @param namespace
	 * @param serviceName
	 * @param serviceType
	 * @param id
	 * @return
	 * @throws Exception
	 */
	Result deleteBackup(String txid, String namespace, String serviceType, String serviceName, String backupId) throws Exception;
	
	/**
	 * 
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @param serviceType
	 * @param backupId
	 * @return
	 * @throws Exception
	 */
	Result restoreFromBackup(String txId, String namespace, String serviceName, String serviceType, String backupId) throws Exception;

	/**
	 * 
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	Result removeServiceResource(String txId, String namespace, String serviceType, String serviceName) throws Exception;
}
