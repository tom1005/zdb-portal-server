package com.zdb.core.collector;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zdb.core.domain.DiskUsage;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.util.DiskUsageChecker;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DiskUsageCollector {
	
	@Autowired
	DiskUsageRepository repo;

	// @Scheduled(initialDelayString = "${collector.period.initial-delay}", fixedRateString = "${collector.period.fixed-rate}")
	@Scheduled(initialDelayString = "60000", fixedRateString = "300000")
	public void collect() {
		try {
			List<DiskUsage> diskUsage = DiskUsageChecker.getInstance().getAllDiskUsage();
			for (DiskUsage usage : diskUsage) {
				try {
					repo.save(usage);
				} catch (Exception e) {
				}
			}
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}
}
