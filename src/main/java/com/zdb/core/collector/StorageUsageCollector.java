package com.zdb.core.collector;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zdb.core.domain.StorageUsage;
import com.zdb.core.repository.StorageUsageRepository;
import com.zdb.core.util.DiskUsageChecker;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.ws.MessageSender;

import io.fabric8.kubernetes.api.model.Job;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Profile({"prod"})
public class StorageUsageCollector {
	
	@Autowired
	StorageUsageRepository repo;
	
	@Autowired
	private MessageSender messageSender;

	// @Scheduled(initialDelayString = "${collector.period.initial-delay}", fixedRateString = "${collector.period.fixed-rate}")
	@Scheduled(initialDelayString = "30000", fixedRateString = "90000")
	public void collect() {
		try {
			if(messageSender.getSessionCount() < 1) {
				return;
			}
			
			boolean useCronJob = false;
			List<Job> items = K8SUtil.kubernetesClient().inNamespace("zdb-system").extensions().jobs().list().getItems();
			for (Job job : items) {
				if(job.getMetadata().getName().startsWith("zdb-portal-job")) {
					useCronJob = true;
					break;
				}
			}
			
			if(!useCronJob) {
				List<StorageUsage> diskUsage = new DiskUsageChecker().getAllStorageUsage();
				for (StorageUsage usage : diskUsage) {
					try {
						log.info("{} {} {} {}", usage.getPodName(), usage.getSize(), usage.getUsed(), usage.getUpdateTime());
						repo.save(usage);
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
				
				diskUsage.clear();
			} else {
				log.debug("{}", "zdb-portal-job 을 통해 StorageUsage 정보 조회 됩니다.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}
}
