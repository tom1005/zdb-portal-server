package com.zdb.backup.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackupConfig {

	private static  String backupTarget;
	private static  String domainType;
	private static String backupShell;
	private static String restoreShell;
	private static String prepareShell;
	private static String masterInfo;
	private static String filemeta;
	private static String workingDir;
	private static String redisDumpPath;
	private static String redisDumpFile;
	private static String cleanShell;

	@Value("${backup.target}")
	public void setBackupTarget(String t) {
		backupTarget = t;
	}

	@Value("${backup.type}")
	public void setDomainType(String t) {
		domainType = t;
	}

	@Value("${backup.backupShell}")
	public void setBackupShell(String s) {
		backupShell = s;
	}

	@Value("${backup.restoreShell}")
	public void setRestoreShell(String s) {
		restoreShell = s;
	}
	
	@Value("${backup.prepareShell}")
	public void setPrepareShell(String s) {
		prepareShell = s;
	}
	
	@Value("${backup.masterInfo}")
	public void setMasterInfoShell(String s) {
		masterInfo = s;
	}
	@Value("${backup.workingDir}")
	public void setWorkingDir(String s) {
		workingDir = s;
	}
	
	@Value("${backup.redisDumpPath}")
	public void setRedisDumpPath(String p) {
		redisDumpPath = p;
	}
	
	@Value("${backup.redisDumpFile}")
	public void setRedisDumpFile(String f) {
		redisDumpFile = f;
	}
	@Value("${backup.filemeta}")
	public void setFilemeta(String s) {
		filemeta = s;
	}
	@Value("${backup.cleanShell}")
	public void setCleanShell(String s) {
		cleanShell = s;
	}
	
	public static String getBackupTarget() {return backupTarget;}
	public static String getDomainType() {return domainType;}
	public static String getBackupShell() {return backupShell;}
	public static String getRestoreShell() {return restoreShell;}
	public static String getPrepareShell() {return prepareShell;}
	public static String getMasterInfo() {return masterInfo;}
	public static String getFilemeta() {return filemeta;}
	public static String getWorkingDir() {return workingDir;}
	public static String getRedisDumpPath() {return redisDumpPath;}
	public static String getRedisDumpFile() {return redisDumpFile;}
	public static String getCleanShell() {return cleanShell;}
}
