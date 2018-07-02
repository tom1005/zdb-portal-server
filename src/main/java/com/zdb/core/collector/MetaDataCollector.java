package com.zdb.core.collector;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.zdb.core.domain.MetaData;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
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
	ZDBReleaseRepository repo;
	
	@Autowired
	MetadataRepository metaRepo;
	
	// @Scheduled(initialDelayString = "${collector.period.initial-delay}", fixedRateString = "${collector.period.fixed-rate}")
	@Scheduled(initialDelayString = "20000", fixedRateString = "60000")
	public void collect() {
		try {
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
			}
			
			// DB기준 체크(Kube에 삭제되고 DB에 남아있는 데이터 삭제)
			Iterable<MetaData> findAll = metaRepo.findAll();
			for (MetaData metaData : findAll) {
				
				String kind = metaData.getKind();
				String namespace = metaData.getNamespace();
				String name = metaData.getName();
				
				boolean flag = false;
				if("Deployment".equals(kind)) {
					flag = exist(allDeployments, namespace, name);
				} else if("Pod".equals(kind)) {
					flag = exist(allPods, namespace, name);
				} else if("ReplicaSet".equals(kind)) {
					flag = exist(allReplicaaSets, namespace, name);
				} else if("StatefulSet".equals(kind)) {
					flag = exist(allStatuefulSets, namespace, name);
				} else if("Service".equals(kind)) {
					flag = exist(allServices, namespace, name);
				} else if("ConfigMap".equals(kind)) {
					flag = exist(allConfigMaps, namespace, name);
				} else if("Secret".equals(kind)) {
					flag = exist(allSecrets, namespace, name);
				}
				
				// not exist
				if(!flag) {
					metaData.setAction("DELETED");
					metaData.setUpdateTime(DateUtil.currentDate());
					metaData.setStatus("");
					metaRepo.save(metaData);
					log.info("MetaData DELETED.{} {} {}", kind, namespace, name);
				}
				
			}
			
			log.info("MetaData Sync : " + (System.currentTimeMillis() - s));
			
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	private boolean exist(List<? extends HasMetadata> allSecrets, String namespace, String name) {
		boolean flag = false;
		for (HasMetadata secret : allSecrets) {
			if(secret.getMetadata().getNamespace().equals(namespace) && secret.getMetadata().getName().equals(name)) {
				flag = true;
				break;
			}
		}
		return flag;
	}
	
	private void save(List<? extends HasMetadata> metadataList) {
		for (HasMetadata metaObj : metadataList) {
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
			m.setUid(metaObj.getMetadata().getUid());
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
