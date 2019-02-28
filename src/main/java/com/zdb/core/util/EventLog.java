package com.zdb.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.fabric8.kubernetes.api.model.Event;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventLog {
	public static void printLog(Event event ) {
		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(event);
		log.info(json);
	}
}
