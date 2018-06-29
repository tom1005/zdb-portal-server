package com.zdb.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

	private static String storageType;
	private static String accessKey;
	private static String secretKey;
	private static String endpointUrl;
	private static String apiKey;
	private static String serviceInstanceId;
	private static String location;

	@Value("${storage.storageType}")
	public void setStorageType(String s) {
		storageType = s;
	}
	@Value("${storage.accessKey}")
	public void setAccessKey(String s) {
		accessKey = s;
	}
	@Value("${storage.secretKey}")
	public void setsecretKey(String s) {
		secretKey = s;
	}
	@Value("${storage.endpointUrl}")
	public void setEndpointUrl(String s) {
		endpointUrl = s;
	}
	@Value("${storage.apiKey}")
	public void setApiKey(String s) {
		apiKey = s;
	}
	@Value("${storage.serviceInstanceId}")
	public void setServiceInstanceId(String s) {
		serviceInstanceId = s;
	}
	@Value("${storage.location}")
	public void setLocation(String s) {
		location = s;
	}
	
	public static String getStorageType() {return storageType;}
	public static String getAccessKey() {return accessKey;}
	public static String getsecretKey() {return secretKey;}
	public static String getEndpointUrl() { return endpointUrl;}
	public static String getApiKey() {return apiKey;}
	public static String getServiceInstanceId() {return serviceInstanceId;}
	public static String getLocation() {return location;}
}
