package com.zdb.core;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.ZDBMariaDBConfig;
import com.zdb.mariadb.MariaDBConfiguration;

public class MapTOObject {

	public static void main(String[] args) {
//		ZDBMariaDBConfig config = new ZDBMariaDBConfig();
//		config.setEventScheduler(eventSchedule == null ? MariaDBConfiguration.DEFAULT_EVENT_SCHEDULER : eventSchedule);
//		config.setGroupConcatMaxLen(groupConcatMaxLen == null ? MariaDBConfiguration.DEFAULT_GROUP_CONCAT_MAX_LEN : groupConcatMaxLen);
//		config.setMaxConnections(maxConnections == null ? MariaDBConfiguration.DEFAULT_MAX_CONNECTIONS : maxConnections);
//		config.setWaitTimeout(waitTimeout == null ? MariaDBConfiguration.DEFAULT_WAIT_TIMEOUT : waitTimeout);
//		config.setReleaseName(releaseName);
		
		// Map --> ZDBMariaDBConfig
		Map<String, String> configMap = new HashMap<>();
		configMap.put("eventScheduler", MariaDBConfiguration.DEFAULT_EVENT_SCHEDULER);
		configMap.put("groupConcatMaxLen", MariaDBConfiguration.DEFAULT_GROUP_CONCAT_MAX_LEN);
		configMap.put("maxConnections", MariaDBConfiguration.DEFAULT_MAX_CONNECTIONS);
		configMap.put("waitTimeout", MariaDBConfiguration.DEFAULT_WAIT_TIMEOUT);
		configMap.put("releaseName", "test");
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(configMap);
		System.out.println(json);
		ZDBMariaDBConfig mariaDBConfig = gson.fromJson(json, ZDBMariaDBConfig.class);
		
		System.out.println();
		
		// ZDBMariaDBConfig --> Map
		String mariaDBConfigStr = gson.toJson(mariaDBConfig);
		Map map = gson.fromJson(mariaDBConfigStr, Map.class);
		System.out.println(configMap.get("eventScheduler"));
		System.out.println(configMap.get("groupConcatMaxLen"));
		System.out.println(configMap.get("maxConnections"));
		System.out.println(configMap.get("waitTimeout"));
		System.out.println(configMap.get("releaseName"));
		
	}

}
