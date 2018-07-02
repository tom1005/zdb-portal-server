package com.zdb.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.zdb.core.domain.MetaData;
import com.zdb.core.repository.MetadataRepository;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import lombok.extern.slf4j.Slf4j;

@Service("k8sService")
@Slf4j
@Configuration
public class K8SService {

	@Autowired
	private  MetadataRepository metadataRepository;
	
//	'ConfigMap'
//	'Service'
//	'StatefulSet'
//	'PersistentVolumeClaim'
//	'Pod'
//	'Deployment'
	
	/**
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	public List<PersistentVolumeClaim> getPersistentVolumeClaims(final String namespace) throws Exception {
		List<PersistentVolumeClaim> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndKind(namespace, "PersistentVolumeClaim");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			PersistentVolumeClaim pvc = new Gson().fromJson(meta, PersistentVolumeClaim.class);
			
			list.add(pvc);
		}
		
		return list;
	}

	/**
	 * @param namespace
	 * @param pvcName
	 * @return
	 * @throws Exception
	 */
	public PersistentVolumeClaim getPersistentVolumeClaim(final String namespace, final String pvcName) throws Exception {
		log.error("2*********** {}, {}, {}" ,namespace, pvcName, "PersistentVolumeClaim");
		MetaData metaData = metadataRepository.findNamespaceAndNameAndKind(namespace, pvcName, "PersistentVolumeClaim");
		
		if (metaData != null) {
			String meta = metaData.getMetadata();
			PersistentVolumeClaim pvc = new Gson().fromJson(meta, PersistentVolumeClaim.class);
			
			return pvc;
		} 
		
		return null;
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<PersistentVolumeClaim> getPersistentVolumeClaims(final String namespace, final String serviceName) throws Exception {
		List<PersistentVolumeClaim> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "PersistentVolumeClaim");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			PersistentVolumeClaim pvc = new Gson().fromJson(meta, PersistentVolumeClaim.class);
			
			list.add(pvc);
		}
		
		return list;
	
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<Secret> getSecrets(final String namespace, final String serviceName) throws Exception {
		List<Secret> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "Secret");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			Secret pvc = new Gson().fromJson(meta, Secret.class);
			
			list.add(pvc);
		}
		
		return list;
	
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<io.fabric8.kubernetes.api.model.Service> getServices(final String namespace, final String serviceName) throws Exception {
		List<io.fabric8.kubernetes.api.model.Service> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "Service");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			io.fabric8.kubernetes.api.model.Service pvc = new Gson().fromJson(meta, io.fabric8.kubernetes.api.model.Service.class);
			
			list.add(pvc);
		}
		
		return list;
	
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<ConfigMap> getConfigMaps(final String namespace, final String serviceName) throws Exception {

		List<ConfigMap> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "ConfigMap");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			ConfigMap pvc = new Gson().fromJson(meta, ConfigMap.class);
			
			list.add(pvc);
		}
		
		return list;
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<StatefulSet> getStatefulSets(final String namespace, final String serviceName) throws Exception {

		List<StatefulSet> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "StatefulSet");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			StatefulSet pvc = new Gson().fromJson(meta, StatefulSet.class);
			
			list.add(pvc);
		}
		
		return list;
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<HasMetadata> getServiceOverviewMeta(final String namespace, final String serviceName, boolean isDetail) throws Exception {

		List<HasMetadata> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseName(namespace, serviceName);
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			if("StatefulSet".equals(metaData.getKind())) {
				StatefulSet data = new Gson().fromJson(meta, StatefulSet.class);
				list.add(data);
			} else if("Pod".equals(metaData.getKind())) {
				Pod data = new Gson().fromJson(meta, Pod.class);
				list.add(data);
			} else if("ReplicaSet".equals(metaData.getKind())) {
				ReplicaSet data = new Gson().fromJson(meta, ReplicaSet.class);
				list.add(data);
			} else if("ConfigMap".equals(metaData.getKind()) && isDetail) {
				ConfigMap data = new Gson().fromJson(meta, ConfigMap.class);
				list.add(data);
			} else if("PersistentVolumeClaim".equals(metaData.getKind()) && isDetail) {
				PersistentVolumeClaim data = new Gson().fromJson(meta, PersistentVolumeClaim.class);
				list.add(data);
			} else if("Secret".equals(metaData.getKind()) && isDetail) {
				Secret data = new Gson().fromJson(meta, Secret.class);
				list.add(data);
			} else if("Deployment".equals(metaData.getKind()) && isDetail) {
				Deployment data = new Gson().fromJson(meta, Deployment.class);
				list.add(data);
			} else if("Service".equals(metaData.getKind()) && isDetail) {
				io.fabric8.kubernetes.api.model.Service data = new Gson().fromJson(meta, io.fabric8.kubernetes.api.model.Service.class);
				list.add(data);
			}
		}
		
		return list;
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<ReplicaSet> getReplicaSets(final String namespace, final String serviceName) throws Exception {

		List<ReplicaSet> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "ReplicaSet");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			ReplicaSet pvc = new Gson().fromJson(meta, ReplicaSet.class);
			
			list.add(pvc);
		}
		
		return list;
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<Pod> getPods(final String namespace, final String serviceName) throws Exception {

		List<Pod> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "Pod");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			Pod pvc = new Gson().fromJson(meta, Pod.class);
			
			list.add(pvc);
		}
		
		return list;
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<Deployment> getDeployments(final String namespace, final String serviceName) throws Exception {

		List<Deployment> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "Deployment");
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			Deployment pvc = new Gson().fromJson(meta, Deployment.class);
			
			list.add(pvc);
		}
		
		return list;
	}

	public List<Namespace> getNamespaces() throws Exception {
		List<Namespace> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAll();
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			Namespace pvc = new Gson().fromJson(meta, Namespace.class);

			Map<String, String> labels = pvc.getMetadata().getLabels();
			if (labels != null) {
				String name = labels.get("name");
				if ("zdb".equals(name)) {
					list.add(pvc);
				}
			}
		}
		
		return list;
	
	}
	
}
