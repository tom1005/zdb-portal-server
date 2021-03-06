package com.zdb.core.collector;

import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.service.K8SService;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.ws.MessageSender;

import hapi.release.ReleaseOuterClass.Release;
import hapi.release.StatusOuterClass.Status.Code;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Job;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Profile({"prod"})
public class ReleaseCollector {
	
	@Autowired
	ZDBReleaseRepository repo;

	@Autowired
	K8SService k8sService;
	
	@Autowired
	private MessageSender messageSender;

	// @Scheduled(initialDelayString = "${collector.period.initial-delay}", fixedRateString = "${collector.period.fixed-rate}")
	@Scheduled(initialDelayString = "30000", fixedRateString = "120000")
	public void collect() {
		if(messageSender.getSessionCount() < 1) {
			return;
		}
		try {
			boolean useCronJob = false;
			List<Job> jobs = K8SUtil.kubernetesClient().inNamespace("zdb-system").extensions().jobs().list().getItems();
			for (Job job : jobs) {
				if(job.getMetadata().getName().startsWith("zdb-portal-job")) {
					useCronJob = true;
					break;
				}
				
			}
			
			if(!useCronJob) {
				List<Release> releaseAllList = K8SUtil.getReleaseAllList();
				
				for (Release release : releaseAllList) {
					
					if(Code.DELETED == release.getInfo().getStatus().getCode() || Code.FAILED == release.getInfo().getStatus().getCode() || Code.DELETING == release.getInfo().getStatus().getCode() ) {
						continue;
					}
					
					ReleaseMetaData releaseMeta = repo.findByReleaseName(release.getName());
					if (releaseMeta == null) {
						releaseMeta = new ReleaseMetaData();
						releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
						releaseMeta.setAction("SYNC");
					} 
					releaseMeta.setApp(release.getChart().getMetadata().getName());
					
					try {
//						String raw = release.getConfig().getRaw();
//						
//						DumperOptions options = new DumperOptions();
//						options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
//						options.setPrettyFlow(true);
//						
//						Yaml yaml = new Yaml(options);
//						
//						Map<String, Map<String, Object>> flesh = yaml.loadAs(raw, Map.class);
//						Object v = flesh.get("image").get("tag");
						
						List<StatefulSet> statefulSets = k8sService.getStatefulSets(release.getNamespace(), release.getName());
						
						String version = null;
						for (StatefulSet statefulSet : statefulSets) {
							List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
							for (Container container : containers) {
								if (container.getName().indexOf("redis") > -1 || container.getName().endsWith("mariadb")) {
									String image = container.getImage();

									String[] split = image.split(":");

									version = split[1];
									break;
								}
							}
						}
						
						releaseMeta.setAppVersion(version);
						if(releaseMeta.getClusterEnabled() == null) {
							releaseMeta.setClusterEnabled(statefulSets.size() > 1 ? true : false);
						}
						
						if("CREATING".equals(releaseMeta.getStatus())) {
							List<Pod> pods = k8sService.getPods(release.getNamespace(), release.getName());
							
							if(statefulSets.size() == pods.size()) {
								long createTime = release.getInfo().getFirstDeployed().getSeconds() * 1000L;
								long currentTime = System.currentTimeMillis();
								
								// 최초 생성 요청시 생성중이므로 상태값 동기화 skip
								// 화면에서 생성중 동기화 되면 에러로 표시되는 문제발생으로 아래 로직 추가.
								if(currentTime - createTime > 1000 * 60 * 3) {
									releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
								}
							}
						}
						
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
					
					releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
					releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds() * 1000L));
					releaseMeta.setNamespace(release.getNamespace());
					releaseMeta.setReleaseName(release.getName());
					releaseMeta.setChartName(release.getChart().getMetadata().getName());
					
					String description = URLEncoder.encode(release.getInfo().getDescription(), "UTF-8");
					releaseMeta.setDescription(description);
					releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
					releaseMeta.setManifest(release.getManifest());
					releaseMeta.setInputValues(release.getConfig().getRaw());
					releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));

					List<Service> services = k8sService.getServices(release.getNamespace(), release.getName());
					
					boolean publicEnabled = false;
					
					for (Service service : services) {
						Map<String, String> annotations = service.getMetadata().getAnnotations();
						if( annotations != null) {
							String type = annotations.get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type");
							if("public".equals(type)) {
								publicEnabled = true;
								break;
							}
						}
					}
					releaseMeta.setPublicEnabled(publicEnabled);
//					 log.info("{} ------- {}", releaseMeta.getReleaseName(), releaseMeta.getStatus());

					repo.save(releaseMeta);
				}
				
				
				// helm ls --all 에 삭제 되어 없어진 경우 DB에 삭제 상태로 갱신.
				
				Iterable<ReleaseMetaData> findAll = repo.findAll();
				for (ReleaseMetaData releaseMeta : findAll) {
					String releaseName = releaseMeta.getReleaseName();

					String namespace = releaseMeta.getNamespace();

					List<StatefulSet> items = K8SUtil.kubernetesClient().inNamespace(namespace).apps().statefulSets().withLabel("release", releaseName).list().getItems();

					if (items == null || items.isEmpty()) {
						releaseMeta.setStatus("DELETED");
						repo.save(releaseMeta);
					}
				}
				
			} else {
				log.debug("{}", "zdb-portal-job 을 통해 Release 정보 동기화 됩니다.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}
}
