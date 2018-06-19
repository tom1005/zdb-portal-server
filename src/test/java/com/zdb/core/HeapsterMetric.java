package com.zdb.core;

import java.net.URI;
import java.util.Map;

import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class HeapsterMetric {
	static String metricUrl = String.format("http://%s:%s/api/v1/model/namespaces/%s/pod-list/%s/metrics", "169.56.71.110", "80", "zdb-maria", "zdb-306-mariadb-master-0");

	public static void main(String[] args) {
//		// TODO Auto-generated method stub
//		// http://169.56.71.110/api/v1/model/namespaces/zdb-maria/pod-list/maria-test777-mariadb-0/metrics/cpu-usage
//		
//
//
//		RestTemplate restTemplate = new RestTemplate();
//		URI uri = URI.create(metricUrl + "/filesystem/usage");
//		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
//		
//		Gson gson = new GsonBuilder().setPrettyPrinting().create();
//		String json = gson.toJson(responseMap);
//		System.out.println(json);
		
		filesystemInodesFree();
		
		

	}
	
	public static void print(Map<String, Object> responseMap) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(responseMap);
		System.out.println(json);
	}
	
	
	public static void filesystemUsage() {
		System.out.println("/filesystem/usage");
		RestTemplate restTemplate = new RestTemplate();
		URI uri = URI.create(metricUrl + "/filesystem/usage");
		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
		
		print(responseMap);
	}
	
	public static void filesystemLimit() {
		System.out.println("/filesystem/limit");
		RestTemplate restTemplate = new RestTemplate();
		URI uri = URI.create(metricUrl + "/filesystem/limit");
		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
		
		print(responseMap);
	}
	
	public static void filesystemAvailable() {
		System.out.println("/filesystem/available");
		RestTemplate restTemplate = new RestTemplate();
		URI uri = URI.create(metricUrl + "/filesystem/available");
		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
		
		print(responseMap);
	}
	
	public static void filesystemInodes() {
		System.out.println("/filesystem/inodes");
		RestTemplate restTemplate = new RestTemplate();
		URI uri = URI.create(metricUrl + "/filesystem/inodes");
		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
		
		print(responseMap);
	}
	
	public static void filesystemInodesFree() {
		System.out.println("/filesystem/inodes_free");
		RestTemplate restTemplate = new RestTemplate();
		URI uri = URI.create(metricUrl + "ephemeral_storage/usage");
		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
		
		print(responseMap);
	}
	
	public static void cpuUsage() {
		System.out.println("/cpu-usage");
		
		RestTemplate restTemplate = new RestTemplate();
		URI uri = URI.create(metricUrl + "/cpu-usage");
		Map<String, Object> responseMap = restTemplate.getForObject(uri, Map.class);
		
		print(responseMap);
	}

}
