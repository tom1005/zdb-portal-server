package com.zdb.storage;

public enum StorageType {
	PSEUDO(0, "PSEUDO"),
	ICOS(1, "ICOS"),
	AWS_S3(2, "AWS_S3"),
	UNKNOWN(3, "UNKNOWN");
	
	public int value;
	public String name;
	
	StorageType(int value, String name) {
		this.value = value;
		this.name = name;
	}
	
	static public StorageService getService(StorageType type) {
		switch (type) {
		case ICOS:
			return new IBMObjectStorageService();
		case AWS_S3:
			return new S3ObjectStorageService();
		case UNKNOWN:
		default:
		case PSEUDO:
			return new PseudoObjectStorageService();
		}
	}
}
