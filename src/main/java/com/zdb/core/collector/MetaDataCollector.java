package com.zdb.core.collector;

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
	@Scheduled(initialDelayString = "10000", fixedRateString = "300000")
	public void collect() {
		try {
			long s = System.currentTimeMillis();
			List<Namespace> namespaces = K8SUtil.getNamespaces();
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			save(namespaces);
			
			for (Namespace ns : namespaces) {
				String name = ns.getMetadata().getName();
				
				// deployments
				List<Deployment> deployments = client.inNamespace(name).extensions().deployments().list().getItems();
				save(deployments);
				
				// pods
				List<Pod> pods = client.inNamespace(name).pods().list().getItems();
				save(pods);
				
				// replicasets
				List<ReplicaSet> replicaSets = client.inNamespace(name).extensions().replicaSets().list().getItems();
				save(replicaSets);
				
				// statefulsets
				List<StatefulSet> statefulSets = client.inNamespace(name).apps().statefulSets().list().getItems();
				save(statefulSets);
				
				// services
				List<Service> services = client.inNamespace(name).services().list().getItems();
				save(services);
				
				// configmap
				List<ConfigMap> configMaps = client.inNamespace(name).configMaps().list().getItems();
				save(configMaps);
				
				// secrets
				List<Secret> secrets = client.inNamespace(name).secrets().list().getItems();
				save(secrets);
			}
			
			log.info("MetaData Sync : " + (System.currentTimeMillis() - s));
			
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
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
					m.setAction("ADDED");
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
