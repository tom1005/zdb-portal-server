package com.zdb.core.domain;

import java.util.Arrays;

public enum BackupStatus {

	ACCEPTED("ACCEPTED"), DOING("DOING"), OK("OK"), DELETED("DELETED"), FAILED("FAILED"), ABORTED("ABORTED");
	
	private String name;
	BackupStatus(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
	
	public static boolean contains(String typeName) {
		return Arrays.asList(BackupStatus.values()).contains(getType(typeName));
	}

	public static BackupStatus getType(String typeName) {
		return Arrays.stream(BackupStatus.values()).filter(payGroup -> payGroup.isEquals(typeName)).findAny().orElse(null);
	}

	public boolean isEquals(String typeName) {
		return getName().equals(typeName);
	}
}
