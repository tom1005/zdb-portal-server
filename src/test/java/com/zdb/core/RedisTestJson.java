package com.zdb.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.MariaDBConfig;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.ServiceSpec;
import com.zdb.core.domain.ZDBEntity;

public class RedisTestJson {

	public static void main(String[] args) {

		System.out.println("###################### createDeployment #####################");
		createDeployment("zdb-redis", "redis", "zdb-redis-namyu1");
		System.out.println("");
		
		System.out.println("###################### Scale Up #####################");
		scaleUp("zdb-redis", "redis", "zdb-redis-namyu1");
		System.out.println("");

		System.out.println("###################### Scale Out #####################");
		scaleOut("zdb-redis", "redis", "zdb-redis-namyu1");	
		System.out.println("");

		System.out.println("###################### Redis Config Update #####################");
		configUpdate("zdb-redis", "redis", "zdb-redis-namyu1");	
	
	}  
	 
	public static void createDeployment(String namespace, String serviceType, String serviceName) {
		Map<String, Object> inputValues 	= new LinkedHashMap<String, Object>();
		Map<String, Object> podSpec 		= new LinkedHashMap<String, Object>();
		Map<String, Object> requestResource = new LinkedHashMap<String, Object>();
		Map<String, Object> limitResource   = new LinkedHashMap<String, Object>();
		Map<String, Object> serviceSpec 	= new LinkedHashMap<String, Object>();
		Map<String, Object> redisConfig 	= new LinkedHashMap<String, Object>();
		List<Object> resourceSpecList  	    = new ArrayList<Object>();
		List<Object> podSpecList  	    	= new ArrayList<Object>();
		List<Object> serviceSpecList      	= new ArrayList<Object>();
		List<Object> redisConfigList      	= new ArrayList<Object>();
		
		requestResource.put("resourceType", "requests");
		requestResource.put("cpu"		  , "100");
		requestResource.put("memory"	  , "256");
		resourceSpecList.add(requestResource);
		
		limitResource.put("resourceType", "limits"); 
		limitResource.put("cpu"			, "100");
		limitResource.put("memory"		, "256"); 
		resourceSpecList.add(limitResource); 

		podSpec.put("podType", "master");
		podSpec.put("resourceSpec", resourceSpecList); 
		
		podSpecList.add(podSpec);
		
		serviceSpec.put("podType", "master");
		serviceSpec.put("loadBalancerType", "public");
 
		serviceSpecList.add(serviceSpec);
		
		Map<String, Object> config 	= new LinkedHashMap<String, Object>();
		config.put("timeout"					, "0");
		config.put("tcp-keepalive"				, "300");
		config.put("maxmemory-policy"			, "noeviction");
		config.put("maxmemory-samples"			, "5");
		config.put("slowlog-log-slower-than"	, "10000");
		config.put("slowlog-max-len"			, "128");
		config.put("notify-keyspace-events"		, "");
		config.put("hash-max-ziplist-entries"	, "512");
		config.put("hash-max-ziplist-value"		, "64");
		config.put("list-max-ziplist-size"		, "-2");
		config.put("zset-max-ziplist-entries"	, "128");
		config.put("zset-max-ziplist-value"		, "64");
		config.put("save"						, "900 1 300 10 60 10000");

		redisConfig.put("podType", "master");
		redisConfig.put("config", config);
		
		redisConfigList.add(redisConfig);
		
		inputValues.put("version" 		, "4.0.9");	
		inputValues.put("serviceType" 	, serviceType);	
		inputValues.put("serviceName" 	, serviceName);	
		inputValues.put("namespace" 	, namespace);	
		inputValues.put("purpose" 		, "SESSION");	
		inputValues.put("podSpec" 		, podSpecList);	
		inputValues.put("serviceSpec" 	, serviceSpecList);	
		inputValues.put("redisConfig" 	, redisConfigList);	
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(inputValues);
		
		System.out.println(json);
	}
	
	
	public static void scaleOut(String namespace, String serviceType, String serviceName) {
		Map<String, Object> inputValues 	= new LinkedHashMap<String, Object>();
 
		inputValues.put("namespace" 		, namespace);	
		inputValues.put("serviceType" 		, serviceType);	
		inputValues.put("serviceName" 		, serviceName);	
		inputValues.put("clusterSlaveCount" , 3);	

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(inputValues);
		
		System.out.println(json);
	}

	public static void scaleUp(String namespace, String serviceType, String serviceName) {
		Map<String, Object> inputValues 	= new LinkedHashMap<String, Object>();
		Map<String, Object> podSpec 		= new LinkedHashMap<String, Object>();
		Map<String, Object> requestResource = new LinkedHashMap<String, Object>();
		Map<String, Object> limitResource   = new LinkedHashMap<String, Object>();
		List<Object> resourceSpecList  	    = new ArrayList<Object>();
		List<Object> podSpecList  	    	= new ArrayList<Object>();
		
		requestResource.put("resourceType", "requests");
		requestResource.put("cpu"		  , "100");
		requestResource.put("memory"	  , "256");
		resourceSpecList.add(requestResource);
		
		limitResource.put("resourceType", "limits"); 
		limitResource.put("cpu"			, "100");
		limitResource.put("memory"		, "256"); 
		resourceSpecList.add(limitResource); 

		podSpec.put("podType", "master");
		podSpec.put("resourceSpec", resourceSpecList);
		
		podSpecList.add(podSpec);
		
		inputValues.put("serviceType" 	, serviceType);	 
		inputValues.put("serviceName" 	, serviceName);	
		inputValues.put("namespace" 	, namespace);	
		inputValues.put("podSpec" 		, podSpecList);	
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(inputValues);
		
		System.out.println(json);
	}	
	
	public static void configUpdate(String namespace, String serviceType, String serviceName) {
		Map<String, Object> inputValues 	= new LinkedHashMap<String, Object>();
 
		inputValues.put("timeout" 					, "0");	
		inputValues.put("tcp-keepalive" 			, "300");	
		inputValues.put("maxmemory-policy" 			, "noeviction");	
		inputValues.put("maxmemory-samples" 		, "10");	
		inputValues.put("slowlog-log-slower-than" 	, "10000");	
		inputValues.put("slowlog-max-len" 			, "128");	
		inputValues.put("notify-keyspace-events" 	, "\"\"");	
		inputValues.put("hash-max-ziplist-entries" 	, "512");	
		inputValues.put("hash-max-ziplist-value" 	, "64");	
		inputValues.put("list-max-ziplist-size" 	, "-2");	
		inputValues.put("zset-max-ziplist-entries" 	, "128");	
		inputValues.put("zset-max-ziplist-value" 	, "64");	
		inputValues.put("save" 						, "900 1 300 10 60 10000");	

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(inputValues);
		
		System.out.println(json);
	}	
	
}
