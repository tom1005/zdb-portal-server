package com.zdb.core.repository;

import java.util.Date;

import com.google.gson.Gson;
import com.zdb.core.domain.RequestEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZDBRepositoryUtil {

	public static void saveRequestEvent(ZDBRepository metaRepository, RequestEvent event) {
		log.info(new Gson().toJson(event));
		
		if(metaRepository != null) {
			RequestEvent re = metaRepository.findByTxId(event.getTxId());
			if(re != null) {
				Date updateTime = event.getUpdateTime();
				Date endTime = event.getEndTime();
				int status = event.getStatus();
				String message = event.getResultMessage();
				String statusMsg = event.getStatusMessage();
				
				re.setUpdateTime(updateTime == null ? new Date(System.currentTimeMillis()) : updateTime);
				re.setEndTime(endTime == null ? new Date(System.currentTimeMillis()) : endTime);
				re.setStatus(status);
				re.setResultMessage(message);
				re.setStatusMessage(statusMsg);
				
				if (re.getChartName() == null || re.getChartName().trim().length() == 0) {
					re.setChartName(event.getChartName());
				} 
				if (re.getChartVersion() == null || re.getChartVersion().trim().length() == 0) {
					re.setChartVersion(event.getChartVersion());
				} 
				
				metaRepository.save(re);
			} else {
				event.setStartTime(new Date(System.currentTimeMillis()));
				metaRepository.save(event);
			}
		} else {
			log.error("Save fail. (ZDBRepository is null.)");
		}
	}
}
