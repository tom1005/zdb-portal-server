package com.zdb.core.collector;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.zdb.core.domain.MetaData;
import com.zdb.core.domain.PersistentVolumeClaimEntity;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.repository.PersistentVolumeClaimRepository;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.ws.MessageSender;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Job;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MetaDataCollector {
	
	@Autowired
	MetadataRepository metaRepo;
	
	@Autowired
	PersistentVolumeClaimRepository pvcRepo;
	
	@Autowired
	private MessageSender messageSender;
	
	/**
	 * 마지막으로 전송한 메세지를 담는 공간...
	 */
	public static LinkedHashMap<String, HasMetadata> METADATA_CACHE = new LinkedHashMap<String, HasMetadata>() {
		private static final long serialVersionUID = -1L;

		protected boolean removeEldestEntry(java.util.Map.Entry<String, HasMetadata> eldest) {
			return size() > 1;
		};
	};
	
    public static void putMetaData(String key, HasMetadata value) {
    	synchronized (METADATA_CACHE) {
    		METADATA_CACHE.put(key, value);
    	}
    }

    @Value("${spring.profiles.active}")
    private String profile;

	@Scheduled(initialDelayString = "20000", fixedRateString = "90000")
	public void collect() {
		try {
			if (!"prod".equals(profile)) {
				return;
			}
			
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
				long s = System.currentTimeMillis();
				List<Namespace> namespaces = K8SUtil.getNamespaces();
				DefaultKubernetesClient client = K8SUtil.kubernetesClient();

				save(namespaces);
				
				List<Deployment> allDeployments = new ArrayList<>();
				List<Pod> allPods = new ArrayList<>();
				List<ReplicaSet> allReplicaaSets = new ArrayList<>();
				List<StatefulSet> allStatuefulSets = new ArrayList<>();
				List<Service> allServices = new ArrayList<>();
				List<ConfigMap> allConfigMaps = new ArrayList<>();
				List<Secret> allSecrets = new ArrayList<>();
				List<PersistentVolumeClaim> allPvcs = new ArrayList<>();
				
				// Kube 기준 동기화
				for (Namespace ns : namespaces) {
					String name = ns.getMetadata().getName();
					
					// deployments
					List<Deployment> deployments = client.inNamespace(name).extensions().deployments().list().getItems();
					save(deployments);
					allDeployments.addAll(deployments);
					
					// pods
					List<Pod> pods = client.inNamespace(name).pods().list().getItems();
					save(pods);
					allPods.addAll(pods);
					
					// replicasets
					List<ReplicaSet> replicaSets = client.inNamespace(name).extensions().replicaSets().list().getItems();
					save(replicaSets);
					allReplicaaSets.addAll(replicaSets);
					
					// statefulsets
					List<StatefulSet> statefulSets = client.inNamespace(name).apps().statefulSets().list().getItems();
					save(statefulSets);
					allStatuefulSets.addAll(statefulSets);
					
					// services
					List<Service> services = client.inNamespace(name).services().list().getItems();
					save(services);
					allServices.addAll(services);
					
					// configmap
					List<ConfigMap> configMaps = client.inNamespace(name).configMaps().list().getItems();
					save(configMaps);
					allConfigMaps.addAll(configMaps);
					
					// secrets
					List<Secret> secrets = client.inNamespace(name).secrets().list().getItems();
					save(secrets);
					allSecrets.addAll(secrets);
					
					List<PersistentVolumeClaim> pvcs = client.inNamespace(name).persistentVolumeClaims().list().getItems();
					save(pvcs);
					allPvcs.addAll(pvcs);
					
				}
				
				List<PersistentVolumeClaim> allPersistentVolumeClaimList = client.inAnyNamespace().persistentVolumeClaims().list().getItems();
				
				for (PersistentVolumeClaim pvc : allPersistentVolumeClaimList) {
					try {
						PersistentVolumeClaimEntity entity = pvcRepo.findOne(pvc.getSpec().getVolumeName());
						if(entity != null) {
							if("DELETED".equals(entity.getPhase())) {
								continue;
							}
						} else {
							entity = new PersistentVolumeClaimEntity();
							entity.setVolumeName(pvc.getSpec().getVolumeName());
							
						}
						entity.setCreationTimestamp(pvc.getMetadata().getCreationTimestamp());
						try {
							entity.setApp(pvc.getMetadata().getLabels().get("app"));
						} catch (Exception e) {
						}
						try {
							entity.setBillingType(pvc.getMetadata().getLabels().get("billingType"));
						} catch (Exception e) {
						}
						try {
							entity.setComponent(pvc.getMetadata().getLabels().get("component"));
						} catch (Exception e) {
						}
						try {
							entity.setRelease(pvc.getMetadata().getLabels().get("release"));
						} catch (Exception e) {
						}
						try {
							entity.setZone(pvc.getMetadata().getLabels().get("zone"));
						} catch (Exception e) {
						}
						try {
							entity.setRegion(pvc.getMetadata().getLabels().get("region"));
						} catch (Exception e) {
						}
						entity.setNamespace(pvc.getMetadata().getNamespace());
						entity.setName(pvc.getMetadata().getName());
						entity.setPhase(pvc.getStatus().getPhase());
						entity.setUid(pvc.getMetadata().getUid());
						entity.setStorageClassName(pvc.getSpec().getStorageClassName());
						try {
							entity.setAccessModes(pvc.getSpec().getAccessModes().get(0));
						} catch (Exception e) {
						}
						try {
							String amount = pvc.getSpec().getResources().getRequests().get("storage").getAmount();
							entity.setStoragSize(amount);
						} catch (Exception e) {
						}
						entity.setResourceVersion(pvc.getMetadata().getResourceVersion());
						entity.setUpdateTimestamp(DateUtil.formatDate(DateUtil.currentDate()));
						
						pvcRepo.save(entity);
					} catch (Exception e) {
						log.error("PersistentVolumeClaim "+pvc.getMetadata().getName() +" 정보 저장 에러.");
					}
				}
				
				// DB기준 체크(Kube에 삭제되고 DB에 남아있는 데이터 삭제)
				Iterable<MetaData> findAll = metaRepo.findAll();
				for (MetaData metaData : findAll) {
					
					String kind = metaData.getKind();
					String namespace = metaData.getNamespace();
					String name = metaData.getName();
					String uid = String.format("%s-%s", kind, name);//metaData.getUid();
					
					boolean flag = false;
					if("Deployment".equals(kind)) {
						flag = exist(allDeployments, uid, namespace, name);
					} else if("Pod".equals(kind)) {
						flag = exist(allPods, uid, namespace, name);
					} else if("ReplicaSet".equals(kind)) {
						flag = exist(allReplicaaSets, uid, namespace, name);
					} else if("StatefulSet".equals(kind)) {
						flag = exist(allStatuefulSets, uid, namespace, name);
					} else if("Service".equals(kind)) {
						flag = exist(allServices, uid, namespace, name);
					} else if("ConfigMap".equals(kind)) {
						flag = exist(allConfigMaps, uid, namespace, name);
					} else if("Secret".equals(kind)) {
						flag = exist(allSecrets, uid, namespace, name);
					} else if("Namespace".equals(kind)) {
						flag = exist(namespaces, uid, name);
					} else if("PersistentVolumeClaim".equals(kind)) {
						flag = exist(allPvcs, uid, name);
					}
					
					// not exist
					if(!flag) {
						metaData.setAction("DELETED");
						metaData.setUpdateTime(DateUtil.currentDate());
						metaData.setStatus("");
						metaRepo.save(metaData);
						log.info("MetaData DELETED.{} {} {}", kind, namespace, name);
					} else {
						metaData.setAction("AUTO_SYNC");
						metaData.setUpdateTime(DateUtil.currentDate());
						metaRepo.save(metaData);
					}
					
				}
				
				log.info("MetaData Sync : " + (System.currentTimeMillis() - s));
				
			} else {
				log.debug("{}", "zdb-portal-job 을 통해 MetaData 정보 조회 됩니다.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	private boolean exist(List<? extends HasMetadata> allMetadata, String uid, String namespace, String name) {
		boolean flag = false;
		for (HasMetadata metaData : allMetadata) {
			String _kind = metaData.getKind();
			String _namespace = metaData.getMetadata().getNamespace();
			String _name = metaData.getMetadata().getName();
			
			String _uid = String.format("%s-%s", _kind, _name);
			if(_uid.equals(uid) && metaData.getMetadata().getNamespace().equals(namespace) && metaData.getMetadata().getName().equals(name)) {
				flag = true;
				break;
			}
		}
		return flag;
	}
	
	private boolean exist(List<? extends HasMetadata> allSecrets, String uid, String name) {
		boolean flag = false;
		for (HasMetadata metaData : allSecrets) {
			String _kind = metaData.getKind();
			String _namespace = metaData.getMetadata().getNamespace();
			String _name = metaData.getMetadata().getName();
			
			String _uid = String.format("%s-%s", _kind, _name);
			if(_uid.equals(uid) && metaData.getMetadata().getName().equals(name)) {
				flag = true;
				break;
			}
		}
		return flag;
	}
	
	public void save(List<? extends HasMetadata> metadataList) {
		for (HasMetadata metaObj : metadataList) {
			String kind = metaObj.getKind();
			String namespace = metaObj.getMetadata().getNamespace();
			String name = metaObj.getMetadata().getName();
			
			String uid = String.format("%s-%s", kind, name);
			putMetaData(uid, metaObj);
//			putMetaData(metaObj.getMetadata().getUid(), metaObj);
			
			MetaData m = null;
			if (metaObj instanceof Namespace) {
				m = ((MetadataRepository) metaRepo).findNamespace(metaObj.getMetadata().getName());
			} else {
				m = ((MetadataRepository) metaRepo).findNamespaceAndNameAndKind(metaObj.getMetadata().getNamespace(), metaObj.getMetadata().getName(), metaObj.getKind());
			}
			if (m == null) {
				m = new MetaData();
				try {
					String ct = metaObj.getMetadata().getCreationTimestamp();
					ct = ct.replace("T", " ").replace("Z", "");

					m.setCreateTime(DateUtil.parseDate(ct));
					m.setKind(metaObj.getKind());
					m.setNamespace(metaObj.getMetadata().getNamespace());
					m.setName(metaObj.getMetadata().getName());
					m.setReleaseName(getReleasename(metaObj));
					m.setAction("AUTO_SYNC");
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
			String metaToJon = new Gson().toJson(metaObj);
			m.setApp(getApp(metaObj));
//			m.setUid(metaObj.getMetadata().getUid());
			m.setUid(uid);
			m.setStatus(getStatus(metaObj));
			m.setMetadata(metaToJon);
			m.setUpdateTime(new Date(System.currentTimeMillis()));
			((MetadataRepository) metaRepo).save(m);
		}
	}
	
	private String getReleasename(HasMetadata resource) {
		String releasename = null;
		
		if(resource != null) {
			Map<String, String> labels = resource.getMetadata().getLabels();
			if (labels != null) {
				releasename = labels.get("release");
			}
		}
		
		return releasename;
	}
	
	private String getApp(HasMetadata resource) {
		String app = null;

		if (resource != null) {
			Map<String, String> labels = resource.getMetadata().getLabels();
			if (labels != null) {
				app = labels.get("app");
			}
		}

		return app;
	}
	
	private String getStatus(HasMetadata resource) {
		String status = "";
		if (resource instanceof PersistentVolumeClaim) {
			PersistentVolumeClaim meta = (PersistentVolumeClaim) resource;
			status = meta.getStatus().getPhase();
		} else if (resource instanceof Namespace) {
			Namespace meta = (Namespace) resource;
			status = meta.getStatus().getPhase();
		} else if (resource instanceof Pod) {
			Pod meta = (Pod) resource;
			status = meta.getStatus().getPhase();
		} else if (resource instanceof Deployment) {
			Deployment meta = (Deployment) resource;
		} else if (resource instanceof Service) {
			Service meta = (Service) resource;
		} else if (resource instanceof ConfigMap) {
			ConfigMap meta = (ConfigMap) resource;
		} else if (resource instanceof StatefulSet) {
			StatefulSet meta = (StatefulSet) resource;
		} else if (resource instanceof ReplicaSet) {
			ReplicaSet meta = (ReplicaSet) resource;
		} else if (resource instanceof Secret) {
			Secret meta = (Secret) resource;
		} else if (resource instanceof Event) {
			Event meta = (Event) resource;
		} else {
			log.warn("not support instance. [" + resource.getClass().getName() + "]");
		}
		
		return status;
	}
}
