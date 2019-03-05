package com.zdb.manager;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public class ChangeService {

	public static void main(String[] args) {
		idToken = System.getProperty("token");
		masterUrl = System.getProperty("masterUrl");
		
		ChangeService sts = new ChangeService();
		
		String namespace = "zdb-ha";
		String serviceName = "zdb-ha-failover-mariadb";
		String role = "slave";
		
		// 1. Slave To Master
//		 sts.chageServiceSlaveToMaster(namespace, serviceName);
		
		// 1. Master To Slave
//		 sts.chageServiceMasterToSlave(namespace, serviceName);
		
		 sts.chageServiceMasterToSlaveByREST_API(namespace, serviceName, role);
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
	
	public void chageServiceMasterToSlaveByREST_API(String namespace, String serviceName, String role) {

		try {
			
			RestTemplate rest = K8SUtil.getRestTemplate();
			String idToken = System.getProperty("token");
			String masterUrl = System.getProperty("masterUrl");
			
//			String namespace = "zdb-ha";
//			String name = "zdb-ha-test2-mariadb-master";
			
			
			HttpHeaders headers = new HttpHeaders();
			headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
			headers.set("Authorization", "Bearer " + idToken);
			headers.set("Content-Type", "application/json-patch+json");
			
//			{ "spec": { "selector": { "component": "slave", } } }
			
			String data = "[{\"op\":\"replace\",\"path\":\"/spec/selector/component\",\"value\":\""+role+"\"}]";
		    
			HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

			String endpoint = masterUrl + "/api/v1/namespaces/{namespace}/services/{name}";
			ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, serviceName);
			
			if (response.getStatusCode() == HttpStatus.OK) {
				String body = response.getBody();
				System.out.println(body);
			} else {
				System.err.println("HttpStatus ; " + response.getStatusCode());
				
			}
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
