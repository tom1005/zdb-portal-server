package com.zdb.core.service;

import com.zdb.core.domain.ZDBConfig;
import com.zdb.core.repository.ZDBConfigRepository;

public class ZDBConfigService {
	static String globalNamespace = "global";
	
	static int configCount = 3;
	
	/* Public Network 사용 여부" */
	static String publicNetworkConfig = "public_network_enabled";
	static String publicNetworkConfigName = "Public Network 사용 여부";
	static String publicNetworkDBType = "mariadb";
	static String publicNetworkValue = "true";
	
	/* Backup 보관 기간 */
	static String backupDurationConfig = "backup_duration";
	static String backupDurationConfigName = "Backup 보관 기간	";
	static String backupDurationDBType = "mariadb";
	static String backupDuratioValue = "7";
	
	/* Backup 시간 */
	static String backupTimeConfig = "backup_time";
	static String backupTimeConfigName = "Backup 시각";
	static String backupTimeDBType = "mariadb";
	static String backupTimeValue = "03:00:00";
	
	public static int getConfigCount() {
		return configCount;
	}
	
	public static void initZDBConfig(ZDBConfigRepository zdbConfigRepository) {
		ZDBConfig zdbConfigPublicNetwork = new ZDBConfig();
		zdbConfigPublicNetwork.setNamespace(globalNamespace);
		zdbConfigPublicNetwork.setConfig(publicNetworkConfig);
		zdbConfigPublicNetwork.setConfigName(publicNetworkConfigName);
		zdbConfigPublicNetwork.setDbType(publicNetworkDBType);
		zdbConfigPublicNetwork.setValue(publicNetworkValue);
		
		ZDBConfig zdbConfigBackupDuration = new ZDBConfig();
		zdbConfigBackupDuration.setNamespace(globalNamespace);
		zdbConfigBackupDuration.setConfig(backupDurationConfig);
		zdbConfigBackupDuration.setConfigName(backupDurationConfigName);
		zdbConfigBackupDuration.setDbType(backupDurationDBType);
		zdbConfigBackupDuration.setValue(backupDuratioValue);
		
		ZDBConfig zdbConfigBackupTime = new ZDBConfig();
		zdbConfigBackupTime.setNamespace(globalNamespace);
		zdbConfigBackupTime.setConfig(backupTimeConfig);
		zdbConfigBackupTime.setConfigName(backupTimeConfigName);
		zdbConfigBackupTime.setDbType(backupTimeDBType);
		zdbConfigBackupTime.setValue(backupTimeValue);
		
		zdbConfigRepository.save(zdbConfigPublicNetwork);
		zdbConfigRepository.save(zdbConfigBackupDuration);
		zdbConfigRepository.save(zdbConfigBackupTime);
	}
	
	public static void updateZDBConfig(ZDBConfigRepository zdbConfigRepository, ZDBConfig zdbConfig) {
		String namespace = zdbConfig.getNamespace();
		String config = zdbConfig.getConfig();
		String value = zdbConfig.getValue();
		zdbConfigRepository.updateZDBConfig(namespace, config, value);
	}
	
	public static void deleteZDBConfig(ZDBConfigRepository zdbConfigRepository, ZDBConfig zdbConfig) {
		String namespace = zdbConfig.getNamespace();
		String config = zdbConfig.getConfig();
		zdbConfigRepository.deleteZDBConfig(namespace, config);
	}
}