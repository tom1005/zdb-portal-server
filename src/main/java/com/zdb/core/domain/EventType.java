package com.zdb.core.domain;

import java.util.Arrays;

public enum EventType {
	
	Install, Delete/*, Restart, RestartPod, Update, UpdatePassword
	, SearchDetail, ServiceAccount, UpdateDBConfig, CreatePersistentVolumeClaim, BackupSchedule, Backup, BackupList, BackupDetail
	, DeleteBackup, Restore, AddTag, DeleteTag*/, EMPTY;

	public static EventType getType(String typeName) {
		return Arrays.stream(EventType.values()).filter(payGroup -> payGroup.isEquals(typeName)).findAny().orElse(EMPTY);
	}
	
	public boolean isEquals(String typeName) {
		return name().equals(typeName);
	}
}
