package com.zdb.core.service;

import java.util.ArrayList;
import java.util.List;

import com.zdb.core.domain.ZDBConfig;
import com.zdb.core.repository.ZDBConfigRepository;

public class ZDBConfigService {
	static String globalNamespace = "global";
	
	static int configCount = 5;
	
	/* Public Network 사용 여부" */
	static String publicNetworkConfig = "public_network_enabled";
	static String publicNetworkConfigName = "Public Network 사용 여부";
	static String publicNetworkDBType = "common";
	static String publicNetworkValue = "false";
	
	/* 리소스 가용량 체크 */
	static String freeResourceCheckConfig = "free_resource_check";
	static String freeResourceCheckConfigName = "리소스 가용량 체크";
	static String freeResourceCheckDBType = "common";
	static String freeResourceCheckValue = "true";
	
	/* Backup 보관 기간 */
	static String backupDurationConfig = "backup_duration";
	static String backupDurationConfigName = "Backup 보관 기간	";
	static String backupDurationDBType = "mariadb";
	static String backupDuratioValue = "7";
	
	/* Backup 시간 */
	static String backupTimeConfig = "backup_time";
	static String backupTimeConfigName = "Backup 시각";
	static String backupTimeDBType = "mariadb";
	static String backupTimeValue = "03:00";
	
	/* auto failover 사용 여부 */
	static String autoFailoverConfig = "auto_failover_enabled";
	static String autoFailoverConfigName = "Auto Failover 사용 여부";
	static String autoFailoverDBType = "mariadb";
	static String autoFailoverValue = "false";
	
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
		
		ZDBConfig zdbConfigFreeResourceCheck = new ZDBConfig();
		zdbConfigFreeResourceCheck.setNamespace(globalNamespace);
		zdbConfigFreeResourceCheck.setConfig(freeResourceCheckConfig);
		zdbConfigFreeResourceCheck.setConfigName(freeResourceCheckConfigName);
		zdbConfigFreeResourceCheck.setDbType(freeResourceCheckDBType);
		zdbConfigFreeResourceCheck.setValue(freeResourceCheckValue);

		ZDBConfig zdbConfigAutoFailover = new ZDBConfig();
		zdbConfigAutoFailover.setNamespace(globalNamespace);
		zdbConfigAutoFailover.setConfig(autoFailoverConfig);
		zdbConfigAutoFailover.setConfigName(autoFailoverConfigName);
		zdbConfigAutoFailover.setDbType(autoFailoverDBType);
		zdbConfigAutoFailover.setValue(autoFailoverValue);
		
		List<ZDBConfig> configList = new ArrayList<>();
		configList.add(zdbConfigPublicNetwork);
		configList.add(zdbConfigBackupDuration);
		configList.add(zdbConfigBackupTime);
		configList.add(zdbConfigFreeResourceCheck);
		configList.add(zdbConfigAutoFailover);
		
		for (ZDBConfig zdbConfig : configList) {
			
			List<ZDBConfig> config = zdbConfigRepository.findByNamespaceAndConfig("global", zdbConfig.getConfig());
			if(config == null || config.isEmpty()) {
				zdbConfigRepository.save(zdbConfig);
			}
			
		}
	}
	
	public static void updateZDBConfig(ZDBConfigRepository zdbConfigRepository, ZDBConfig zdbConfig) {
		String namespace = zdbConfig.getNamespace();
		String config = zdbConfig.getConfig();
		String value = zdbConfig.getValue();
		
		List<ZDBConfig> configList = zdbConfigRepository.findByNamespaceAndConfig(namespace, zdbConfig.getConfig());
		if(configList == null || configList.isEmpty()) {
			zdbConfigRepository.save(zdbConfig);
		} else {
			ZDBConfig c = configList.get(0);
			c.setConfig(config);
			c.setValue(value);
			zdbConfigRepository.updateZDBConfig(namespace, config, value);
		}
		
	}
	
	public static void deleteZDBConfig(ZDBConfigRepository zdbConfigRepository, ZDBConfig zdbConfig) {
		String namespace = zdbConfig.getNamespace();
		String config = zdbConfig.getConfig();
		zdbConfigRepository.deleteZDBConfig(namespace, config);
	}
}