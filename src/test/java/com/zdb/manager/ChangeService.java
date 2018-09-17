package com.zdb.manager;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

public class ChangeService {

	public static void main(String[] args) {
		idToken = System.getProperty("token");
		masterUrl = System.getProperty("masterUrl");
		
		ChangeService sts = new ChangeService();
		
		String namespace = "ns-zdb-02";
		namespace = "ns-zdb-02";
		String serviceName = "ns-zdb-02-ppp-mariadb-public";
		
		// 1. 기본 sts (data 볼륨 마운트)
//		 sts.chageServiceSlaveToMaster(namespace, serviceName);
		 sts.chageServiceMasterToSlave(namespace, serviceName);
		
	
	}
	
	/**
	 * data : data pvc, 
	 * backup : backup pvc (추가)
	 * 
	 * @param namespace
	 * @param stsName
	 * @param pvcName
	 */
	public void chageServiceMasterToSlave(String namespace, String serviceName) {
		try {
			DefaultKubernetesClient client = kubernetesClient();
			MixedOperation<Service, ServiceList, DoneableService, Resource<Service, DoneableService>> services = client.inNamespace(namespace).services();
			Service service = services.withName(serviceName).get();
			service.getMetadata().setUid(null);
			service.getMetadata().setCreationTimestamp(null);
			service.getMetadata().setSelfLink(null);
			service.getMetadata().setResourceVersion(null);
			
			Map<String, String> labels = service.getMetadata().getLabels();
			labels.put("component", "slave");
			
			service.getSpec().getPorts().get(0).setNodePort(null);
			service.getSpec().getSelector().put("component", "slave");
			service.getSpec().setClusterIP(null);
			service.getSpec().setSessionAffinity(null);
			service.getSpec().setExternalTrafficPolicy(null);
			service.setStatus(null);
			
			
			ServiceBuilder svcBuilder = new ServiceBuilder(service);
			Service newSvc = svcBuilder
			.editMetadata().withLabels(labels).endMetadata()
			.build();
			
			services.createOrReplace(newSvc);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void chageServiceSlaveToMaster(String namespace, String serviceName) {
		try {
			DefaultKubernetesClient client = kubernetesClient();
			MixedOperation<Service, ServiceList, DoneableService, Resource<Service, DoneableService>> services = client.inNamespace(namespace).services();
			Service service = services.withName(serviceName).get();
			service.getMetadata().setUid(null);
			service.getMetadata().setCreationTimestamp(null);
			service.getMetadata().setSelfLink(null);
			service.getMetadata().setResourceVersion(null);
			
			Map<String, String> labels = service.getMetadata().getLabels();
			labels.put("component", "master");
			
			service.getSpec().getPorts().get(0).setNodePort(null);
			service.getSpec().getSelector().put("component", "master");
			service.getSpec().setClusterIP(null);
			service.getSpec().setSessionAffinity(null);
			service.getSpec().setExternalTrafficPolicy(null);
			service.setStatus(null);
			
			
			ServiceBuilder svcBuilder = new ServiceBuilder(service);
			Service newSvc = svcBuilder
			.editMetadata().withLabels(labels).endMetadata()
			.build();
			
			services.createOrReplace(newSvc);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	


	
	DefaultKubernetesClient client = null;
	String clusterName = null;
	static String idToken = null;
	static String masterUrl = null;
	
	public DefaultKubernetesClient kubernetesClient() throws Exception {
		if(client !=null) {
			return client;
		}
		


		if (idToken == null || masterUrl == null) {
			System.err.println("VM arguments 설정 후 실행 하세요...\n-DmasterUrl=xxx.xxx.xxx.xx:12345 -Dtoken=xxxxxx");
			System.exit(-1);
		}

		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");

		Config config = new ConfigBuilder().withMasterUrl(masterUrl).withOauthToken(idToken).withTrustCerts(true).withWatchReconnectLimit(-1).build();
		client = new DefaultKubernetesClient(config);

		return client;
	}

}
