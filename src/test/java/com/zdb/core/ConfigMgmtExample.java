package com.zdb.core;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;

public class ConfigMgmtExample {

	private static final Logger logger = LoggerFactory.getLogger(ConfigMgmtExample.class);

//	private-crabb3cd296aad42418425707d856037ca-alb1   true      enabled   private   10.178.191.134
//	public-crabb3cd296aad42418425707d856037ca-alb1    true      enabled   public    169.56.70.222
	
	public static void main(String[] args) throws Exception {
		
		List<ConfigMap> items = K8SUtil.kubernetesClient().inNamespace("zdb-test").configMaps().withLabel("release", "zdb-test-asc").list().getItems();
		
		Map<String, String> beforeDataMap = new HashMap<>();
		
		for (ConfigMap configMap : items) {
			String configMapName = configMap.getMetadata().getName();
			String beforeValue = configMap.getData().get("my.cnf");
			
			System.out.println("name : "+configMapName);
			System.out.println("value : "+beforeValue);
		}
	}
	public static void main2(String[] args) throws Exception {
		
//		Release release = K8SUtil.getRelease("zdb-dev-test2", "zdb-105");
//		String raw = release.getConfig().getRaw();
//		
//		System.out.println(raw);
		
//		((Map)release.getConfig().).get("master");
		
//		ServiceOverview serviceOverview = K8SUtil.getServiceWithName("zdb-redis", "redis", "redis-namyu-test8");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
//		System.out.println(gson.toJson(serviceOverview));
		
		List<ConfigMap> configMaps = K8SUtil.getConfigMaps("zdb-redis", "redis-namyu-test8");
		
		for (ConfigMap configMap : configMaps) {
			String component = configMap.getMetadata().getLabels().get("component");
			
			System.out.println(component);
			System.out.println(configMap.getData().get("my.cnf"));
			
			System.out.println("-------------------------------------------------------------------------------");
		}
		
		
		
	}

}