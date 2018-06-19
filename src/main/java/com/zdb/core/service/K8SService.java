package com.zdb.core.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.zdb.core.domain.MetaData;
import com.zdb.core.repository.MetadataRepository;

import io.fabric8.kubernetes.api.model.ConfigMap;
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

	
}
