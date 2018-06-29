package com.zdb.core.collector;

import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.util.K8SUtil;

import hapi.release.ReleaseOuterClass.Release;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReleaseCollector {
	
	@Autowired
	ZDBReleaseRepository repo;

	// @Scheduled(initialDelayString = "${collector.period.initial-delay}", fixedRateString = "${collector.period.fixed-rate}")
	@Scheduled(initialDelayString = "30000", fixedRateString = "300000")
	public void collect() {
		try {
			List<Release> releaseAllList = K8SUtil.getReleaseAllList();
			
			for (Release release : releaseAllList) {
				ReleaseMetaData releaseMeta = repo.findByReleaseName(release.getName());
				if (releaseMeta == null) {
					releaseMeta = new ReleaseMetaData();
				}

				releaseMeta.setApp(release.getChart().getMetadata().getName());
				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds() * 1000L));
				releaseMeta.setNamespace(release.getNamespace());
				releaseMeta.setReleaseName(release.getName());
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				
				String description = URLEncoder.encode(release.getInfo().getDescription(), "UTF-8");
				releaseMeta.setDescription(description);
				releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
				releaseMeta.setManifest(release.getManifest());
				releaseMeta.setInputValues(release.getConfig().getRaw());
				releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));

				// log.info(new Gson().toJson(release));

				repo.save(releaseMeta);
			}
			
			
			// helm ls --all 에 삭제 되어 없어진 경우 DB에 삭제 상태로 갱신.
			
			Iterable<ReleaseMetaData> findAll = repo.findAll();
			for (ReleaseMetaData releaseMeta : findAll) {
				String releaseName = releaseMeta.getReleaseName();
				
				boolean exist = false;
				for (Release release : releaseAllList) {
					String name = release.getName();
					
					if(releaseName.equals(name)) {
						exist = true;
						break;
					}
				}
				
				if(!exist) {
					releaseMeta.setStatus("DELETED");
					repo.save(releaseMeta);
				}
			}
			
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}
}
