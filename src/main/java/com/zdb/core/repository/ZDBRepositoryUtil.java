package com.zdb.core.repository;

import com.google.gson.Gson;
import com.zdb.core.domain.RequestEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZDBRepositoryUtil {

	public static void saveRequestEvent(ZDBRepository metaRepository, RequestEvent event) {
		log.info(new Gson().toJson(event));
		
		if(metaRepository != null) {
			metaRepository.save(event);
		} else {
			log.error("Save fail. (ZDBRepository is null.)");
		}
	}
	
	public static void deleteRequestEvent(ZDBRepository metaRepository, String namespace, String serviceName) {
		if(metaRepository != null) {
			metaRepository.deleteByServiceName(namespace, serviceName);
		} 
	}
}
