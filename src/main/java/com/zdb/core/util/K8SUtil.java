package com.zdb.core.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.zdb.core.domain.CommonConstants;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.KubernetesConstants;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.exception.DuplicateException;

import hapi.release.ReleaseOuterClass.Release;
import hapi.release.StatusOuterClass.Status;
import hapi.release.StatusOuterClass.Status.Code;
import hapi.services.tiller.Tiller.ListReleasesRequest;
import hapi.services.tiller.Tiller.ListReleasesResponse;
import hapi.services.tiller.Tiller.ListSort.SortBy;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.LoadBalancerStatus;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.PersistentVolumeList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentStatus;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class K8SUtil {

	private static String profile;

	public static String MASTER_URL;

	public static String daemonUrl;
	
	@Value("${spring.profiles.active}")
	public void setProfileValue(String activeProfile) {
		profile = activeProfile;
	}

	@Value("${k8s.masterUrl}")
	public void setMasterUrl(String url) {
		MASTER_URL = url;
	}

	@Value("${k8s.daemonUrl}")
	public void setDaemonUrl(String url) {
		daemonUrl = url;
	}
	
	/**
	 * Namespace list
	 * 
	 * @return
	 * @throws KubernetesClientException
	 * @throws FileNotFoundException
	 */
	public static List<Namespace> getNamespaces() throws Exception {
		// zdb namespace label
		return kubernetesClient().inAnyNamespace().namespaces().withLabel(CommonConstants.ZDB_LABEL, "true").list().getItems();
	}

	public static Namespace getNamespace(String namespace) throws Exception {
		return kubernetesClient().inAnyNamespace().namespaces().withName(namespace).get();
	}

	/**
	 * 현재 Running 상태의 Pod 를 반환 한다.
	 * 
	 * 조건 : 서비스명으로부터 replicaset 을 찾고 replicaset 명으로 시작하는 pod 명을 검색.
	 * 
	 * 
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<Pod> getActivePodList(String namespace, String serviceName) throws Exception {
		List<Pod> podList = new ArrayList<>();
		List<Pod> items = getPods(namespace, serviceName);
		for (Pod pod : items) {
			String phase = pod.getStatus().getPhase();

			if ("Running".equals(phase)) {
				podList.add(pod);
			}
		}

		return podList;
	}

//	public static List<Pod> getPodList(String namespace, String serviceName) throws Exception {
//		DefaultKubernetesClient client = kubernetesClient();
//
//		List<Pod> podList = new ArrayList<>();
//
//		List<Pod> items = client.inNamespace(namespace).pods().list().getItems();
//		for (Pod pod : items) {
//			if (pod.getMetadata().getLabels() != null) {
//				if (serviceName.equals(pod.getMetadata().getLabels().get("release"))) {
//					podList.add(pod);
//				}
//			}
//		}
//
//		return podList;
//	}
	
	public static Pod getPodWithName(String namespace, String serviceName, String podName) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();
		
		List<Pod> items = client.inNamespace(namespace).pods().list().getItems();
		for (Pod pod : items) {
			if (pod.getMetadata().getLabels() != null) {
				if (serviceName.equals(pod.getMetadata().getLabels().get("release")) & podName.equals(pod.getMetadata().getName())) {
					return pod;
				}
			}
		}		
		
		return null;
	}
	
	public static Pod getPodWithName(String namespace, String podName) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();
		
		List<Pod> items = client.inNamespace(namespace).pods().list().getItems();
		for (Pod pod : items) {
			if (podName.equals(pod.getMetadata().getName())) {
				return pod;
			}
		}		
		
		return null;
	}
	
	/**
	 * @param namespaceName
	 * @return
	 * @throws FileNotFoundException
	 * @throws KubernetesClientException
	 */
	public static Namespace doCreateNamespace(String namespaceName) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();

		if (isNamespaceExist(namespaceName)) {
			throw new DuplicateException("exist namespace");
		} else {

			Map<String, String> labels = new HashMap<>();

			Namespace ns = new NamespaceBuilder().withNewMetadata().withName(namespaceName).withLabels(labels).endMetadata().build();
			Namespace namespace = client.inAnyNamespace().namespaces().create(ns);

			return namespace;
		}
	}

	/**
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	public static List<PersistentVolumeClaim> getPersistentVolumeClaims(final String namespace) throws Exception {
		return kubernetesClient().inNamespace(namespace).persistentVolumeClaims().list().getItems();
	}

	/**
	 * @param namespace
	 * @param pvcName
	 * @return
	 * @throws Exception
	 */
	public static PersistentVolumeClaim getPersistentVolumeClaim(final String namespace, final String pvcName) throws Exception {
		return kubernetesClient().inNamespace(namespace).persistentVolumeClaims().withName(pvcName).get();
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<PersistentVolumeClaim> getPersistentVolumeClaims(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).persistentVolumeClaims().withLabel("release", serviceName).list().getItems();
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<Secret> getSecrets(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).secrets().withLabel("release", serviceName).list().getItems();
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<Service> getServices(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).services().withLabel("release", serviceName).list().getItems();
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<ConfigMap> getConfigMaps(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).configMaps().withLabel("release", serviceName).list().getItems();
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<StatefulSet> getStatefulSets(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).apps().statefulSets().withLabel("release", serviceName).list().getItems();
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<ReplicaSet> getReplicaSets(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).extensions().replicaSets().withLabel("release", serviceName).list().getItems();
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<Pod> getPods(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).pods().withLabel("release", serviceName).list().getItems();
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static List<Deployment> getDeployments(final String namespace, final String serviceName) throws Exception {
		return kubernetesClient().inNamespace(namespace).extensions().deployments().withLabel("release", serviceName).list().getItems();
	}

	/**
	 * Services list
	 * 
	 * @return
	 * @throws KubernetesClientException
	 * @throws FileNotFoundException
	 */
	public static List<Service> getServicesWithNamespace(String namespace) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();
		if (client != null) {
			ServiceList services = client.inNamespace(namespace).services().list();

			if (log.isDebugEnabled()) {
				for (Service service : services.getItems()) {
					log.debug("service : " + service.getMetadata().getName());
				}
			}
			return services.getItems();
		}

		return Collections.emptyList();
	}

	/**
	 * ClusterIP of a service
	 * 
	 * @return String
	 * @throws FileNotFoundException
	 */
	public static String getClusterIp(String namespace, String name) throws Exception {
		List<Service> serviceList = getServices(namespace, name);
		if (serviceList == null || serviceList.isEmpty()) {
			log.warn("service is null. serviceName: {}", name);
			return null;
		}
		Service service = serviceList.get(0);
		if("loadbalancer".equals(service.getSpec().getType().toLowerCase())) {
			List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
			if( ingress != null && ingress.size() > 0) {
				return ingress.get(0).getIp();
			} else {
				throw new Exception("unknown ServicePort");
			}
		} else if ("clusterip".equals(service.getSpec().getType().toLowerCase())) {
			return service.getSpec().getClusterIP();
		} else {
			log.warn("no cluster ip.");
			return null;
		}
	}

	/**
	 * ClusterIP and Port of a service
	 * 
	 * @param name
	 *            - service name
	 * @return String
	 * @throws FileNotFoundException
	 */
	public static String getClusterIpAndPort(String namespace, String name) throws Exception {
		List<Service> serviceList = getServices(namespace, name);
		if (serviceList == null || serviceList.isEmpty()) {
			log.warn("service is null. serviceName: {}", name);
			return null;
		}
		Service service = serviceList.get(0);
		
		for (Service svc : serviceList) {
			Map<String, String> annotations = svc.getMetadata().getAnnotations();
			String value = annotations.get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type");
			if(value != null && "public".equals(value)) {
				LoadBalancerStatus loadBalancer = svc.getStatus().getLoadBalancer();
				if(loadBalancer == null ) {
					continue;
				} else {
					List<LoadBalancerIngress> ingress = loadBalancer.getIngress();
					if(ingress == null || ingress.isEmpty()) {
						continue;
					}
				}
				service = svc;
				break;
			}
		}

		String portStr = null;

		if("loadbalancer".equals(service.getSpec().getType().toLowerCase())) {
			List<ServicePort> ports = service.getSpec().getPorts();
			for(ServicePort port : ports) {
				if("mysql".equals(port.getName())){
					portStr = Integer.toString(port.getPort());
					break;
				}
			}
			
			if (portStr == null) {
				throw new Exception("unknown ServicePort");
			}
			
			List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
			if( ingress != null && ingress.size() > 0) {
				return ingress.get(0).getIp() + ":" + portStr;
			} else {
//				throw new Exception("unknown ServicePort");
				return service.getMetadata().getName()+"."+service.getMetadata().getNamespace() + ":" + portStr;
			}
		} else if ("clusterip".equals(service.getSpec().getType().toLowerCase())) {
			List<ServicePort> ports = service.getSpec().getPorts();
			for(ServicePort port : ports) {
				if("mysql".equals(port.getName())){
					portStr = Integer.toString(port.getPort());
					break;
				}
			}
			if (portStr == null) {
				throw new Exception("unknown ServicePort");
			}
			
			return service.getSpec().getClusterIP() + ":" + portStr;
		} else {
			log.warn("no cluster ip.");
			return null;
		}
	}
	
	public static Integer getServicePort(String namespace, String name) throws Exception{
		List<Service> serviceList = getServices(namespace, name);
		if (serviceList == null || serviceList.isEmpty()) {
			log.warn("service is null. serviceName: {}", name);
			return null;
		}
		Service service = serviceList.get(0);
		Integer servicePort = 6379;
		if("loadbalancer".equals(service.getSpec().getType().toLowerCase())) {
			List<ServicePort> ports = service.getSpec().getPorts();
			for(ServicePort port : ports) {
				if("redis".equals(port.getName())){
					servicePort = port.getNodePort();
					break;
				}
			}
			if (servicePort == null) {
				throw new Exception("unknown ServicePort");
			}
			List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
			if( ingress != null && ingress.size() > 0) {
				return servicePort;
			} else {
				throw new Exception("unknown ServicePort");
			}
		} else if ("clusterip".equals(service.getSpec().getType().toLowerCase())) {
			List<ServicePort> ports = service.getSpec().getPorts();
			for(ServicePort port : ports) {
				if("redis".equals(port.getName())){
					servicePort = port.getPort();
					break;
				}
			}
			if (servicePort == null) {
				throw new Exception("unknown ServicePort");
			}
			
			return servicePort;
		} else {
			log.warn("no cluster ip.");
			return null;
		}
	}
	
	/**
	 * ExternalIPs of a service
	 * 
	 * @return java.util.List<String>
	 * @throws FileNotFoundException
	 */
	public static List<String> getExternalIPs(String namespace, String name) throws Exception {
		List<Service> serviceList = getServices(namespace, name);
		if (serviceList == null || serviceList.isEmpty()) {
			return null;
		}
		Service service = serviceList.get(0);
		return service.getSpec().getExternalIPs();
	}

	/**
	 * 
	 * get deployment list of namespace.
	 * 
	 * @param namespace
	 * @return
	 * @throws FileNotFoundException
	 * @throws KubernetesClientException
	 */
	public static List<Deployment> getInNamespaceDeploymentList(String namespace) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();
		return client.inNamespace(namespace).extensions().deployments().list().getItems();
	}

	/**
	 * 
	 * get deployment list of namespace.
	 * 
	 * @param namespace
	 * @return
	 * @throws FileNotFoundException
	 * @throws KubernetesClientException
	 */
	public static List<Deployment> getDeploymentListByReleaseName(String namespace, String releaseName) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();
		List<Deployment> items = client.inNamespace(namespace).extensions().deployments().list().getItems();

		List<Deployment> deploymentList = new ArrayList<>();

		for (Deployment deployment : items) {
			if (releaseName.equals(deployment.getMetadata().getLabels().get("release"))) {
				deploymentList.add(deployment);
			}
		}

		return deploymentList;
	}

	public static SecretList getSecrets(final String namespace) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();
		if (client != null) {
			SecretList secrets = client.inNamespace(namespace).secrets().list();
			return secrets;
		}

		return null;
	}

	public static Secret getSecret(final String namespace, final String serviceName) throws Exception {
		SecretList secrets = getSecrets(namespace);
		if (secrets != null) {
			List<Secret> secretList = secrets.getItems();
			
			for (Secret secret : secretList) {
				Map<String, String> labels = secret.getMetadata().getLabels();
				if (labels != null) {
					String release = labels.get("release");

					if (serviceName.equals(release)) {
						return secret;
					}
				}
			}
		}

		return null;
	}

	public static void ReplicaSets() {

	}

	/**
	 * @param serviceName
	 * @return
	 * @throws KubernetesClientException
	 * @throws FileNotFoundException
	 */
	public static boolean isAvailableReplicas(String namespace, String serviceName) throws Exception {
		List<Deployment> deploymentList = getInNamespaceDeploymentList(namespace);

		boolean result = true;

		if (deploymentList != null && !deploymentList.isEmpty()) {
			for (Deployment deployment : deploymentList) {
				DeploymentStatus status = deployment.getStatus();

				Integer availableReplicas = status.getAvailableReplicas();

				log.debug("AvailableReplicas : " + availableReplicas);

				if (availableReplicas != null && availableReplicas.intValue() > 0) {
					continue;
				} else {
					return false;
				}
			}
		} else {
			return false;
		}

		return result;
	}

	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static String getChartName(String namespace, String serviceName) throws Exception {
		ReleaseManager releaseManager = null;
		try {
			DefaultKubernetesClient client = (DefaultKubernetesClient) K8SUtil.kubernetesClient().inNamespace(namespace);

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

			final ListReleasesRequest.Builder requestBuilder = ListReleasesRequest.newBuilder();
			Iterator<ListReleasesResponse> requestBuilderList = releaseManager.list(requestBuilder.build());

			while (requestBuilderList.hasNext()) {
				ListReleasesResponse ent = requestBuilderList.next();
				List<Release> releaseList = ent.getReleasesList();

				for (Release release : releaseList) {
					if (namespace.equals(release.getNamespace()) && serviceName.equals(release.getName())) {
						return release.getChart().getMetadata().getName();
					}
				}
			}
		} finally {
			if (releaseManager != null) {
				releaseManager.close();
			}
			// client.close();
		}

		return null;
	}

	/**
	 * service namd : mariadb2-test config Map : mariadb2-test-mariadb ployments : mariadb2-test-mariadb Persistent Volume Claims : mariadb2-test-pvc Pods :
	 * mariadb2-test-mariadb-75c6844b88-x4c9g Replica Sets : mariadb2-test-mariadb-75c6844b88 Secrets : mariadb2-test-mariadb Services : mariadb2-test-mariadb
	 * 
	 * @param namespace
	 * @param serviceName
	 * @param chartName
	 * @return
	 * @throws FileNotFoundException
	 * @throws KubernetesClientException
	 */
	public static boolean isServiceExist(String namespace, String serviceName) throws Exception {

		ReleaseManager releaseManager = null;
		try {
			DefaultKubernetesClient client = kubernetesClient();

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

			final ListReleasesRequest.Builder requestBuilder = ListReleasesRequest.newBuilder();
			
//		    case 0: return UNKNOWN;
//		    case 1: return DEPLOYED;
//		    case 2: return DELETED;
//		    case 3: return SUPERSEDED;
//		    case 4: return FAILED;
//		    case 5: return DELETING;
//		    case 6: return PENDING_INSTALL;
//		    case 7: return PENDING_UPGRADE;
//		    case 8: return PENDING_ROLLBACK;
			requestBuilder.addStatusCodes(Status.Code.UNKNOWN);
			requestBuilder.addStatusCodes(Status.Code.DEPLOYED);
//			requestBuilder.addStatusCodes(Status.Code.DELETED);
			requestBuilder.addStatusCodes(Status.Code.SUPERSEDED);
			requestBuilder.addStatusCodes(Status.Code.FAILED);
			requestBuilder.addStatusCodes(Status.Code.DELETING);
			requestBuilder.addStatusCodes(Status.Code.PENDING_INSTALL);
			requestBuilder.addStatusCodes(Status.Code.PENDING_UPGRADE);
			requestBuilder.addStatusCodes(Status.Code.PENDING_ROLLBACK);
				
			Iterator<ListReleasesResponse> requestBuilderList = releaseManager.list(requestBuilder.build());

			while (requestBuilderList.hasNext()) {
				ListReleasesResponse ent = requestBuilderList.next();
				List<Release> releaseList = ent.getReleasesList();

				for (Release release : releaseList) {
					// Helm Deploy 상태가 FAILED인 경우는 조회가 되지 않는다.
					// FAILED 상태의 Release 까지 조회가 필요한 경우 추가 조치가 필요함.
					if (namespace.equals(release.getNamespace()) && serviceName.equals(release.getName())) {
						return true;
					}
				}
			}
		} finally {
			if (releaseManager != null) {
				releaseManager.close();
			}
		}

		return false;
	
	}
	
	/**
	 * Release 정보 조회
	 * 
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static Release getRelease(String namespace, String serviceName, Code[] statusCodes) throws Exception {

		ReleaseManager releaseManager = null;
		try {
			DefaultKubernetesClient client = kubernetesClient();

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

			final ListReleasesRequest.Builder requestBuilder = ListReleasesRequest.newBuilder();
			
//			Status.Code.UNKNOWN
//			Status.Code.DEPLOYED
//			Status.Code.DELETED
//			Status.Code.SUPERSEDED
//			Status.Code.FAILED
//			Status.Code.DELETING
//			Status.Code.PENDING_INSTALL
//			Status.Code.PENDING_UPGRADE
//			Status.Code.PENDING_ROLLBACK
			for (Status.Code code : statusCodes) {
				requestBuilder.addStatusCodes(code);
			}
			
			Iterator<ListReleasesResponse> requestBuilderList = releaseManager.list(requestBuilder.build());

			while (requestBuilderList.hasNext()) {
				ListReleasesResponse ent = requestBuilderList.next();
				List<Release> releaseList = ent.getReleasesList();

				for (Release release : releaseList) { 
					if (namespace.equals(release.getNamespace()) && serviceName.equals(release.getName())) {
						return release;
					}
				}
			}
		} finally {
			if (releaseManager != null) {
				releaseManager.close();
			}
		}

		return null;
	
	}
	
	
	public static List<Release> getReleaseList() throws Exception {
		List<Release> releaseList = getReleaseAllList();
		
		for (Iterator<Release> iterator = releaseList.iterator(); iterator.hasNext();) {
			Release r = (Release) iterator.next();
			
			if(r.getInfo().getStatus().getCode() == Code.DELETED) {
				iterator.remove();
			}
		}
		
		return releaseList;
	}
	
	/**
	 * @return
	 * @throws Exception
	 */
	public static List<Release> getReleaseAllList() throws Exception {

		Map<String, Release> releaseMap = new HashMap<>();
		ReleaseManager releaseManager = null;
		try {
			DefaultKubernetesClient client = kubernetesClient();

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);

			final ListReleasesRequest.Builder requestBuilder = ListReleasesRequest.newBuilder();

			Code[] codes = new Code[] { Code.DEPLOYED, Code.FAILED, Code.DELETED, Code.DELETING, Code.PENDING_INSTALL, Code.PENDING_UPGRADE, Code.PENDING_ROLLBACK };

			for (Status.Code code : codes) {
				requestBuilder.addStatusCodes(code);
			}

			requestBuilder.setSortBy(SortBy.LAST_RELEASED);

			Iterator<ListReleasesResponse> requestBuilderList = releaseManager.list(requestBuilder.build());

			List<Namespace> namespaces = getNamespaces();
			List<String> namespaceList = new ArrayList<>();
			for (Namespace n : namespaces) {
				String name = n.getMetadata().getName();
				namespaceList.add(name);
			}
			
			while (requestBuilderList.hasNext()) {
				ListReleasesResponse ent = requestBuilderList.next();
				List<Release> list = ent.getReleasesList();

				for (Release release : list) {
					// zdb namespace check
					String namespace = release.getNamespace();
					if(!namespaceList.contains(namespace)) {
						continue;
					}
					
					String name = release.getChart().getMetadata().getName();
					if (ZDBType.contains(name)) {
						releaseMap.put(release.getName(), release);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (releaseManager != null) {
				releaseManager.close();
			}
		}

		return new ArrayList<Release>(releaseMap.values());
	}

	/**
	 * @param namespaceName
	 * @return
	 * @throws FileNotFoundException
	 * @throws KubernetesClientException
	 */
	public static boolean isNamespaceExist(final String namespaceName) throws Exception {

		if (namespaceName != null) {
			Namespace namespace = kubernetesClient().inAnyNamespace().namespaces().withName(namespaceName).get();

			if (namespace != null) {
				return true;
			}
		}
		return false;

	}

	/**
	 * 
	 * @return
	 * @throws FileNotFoundException
	 * @throws KubernetesClientException
	 */
	public static DefaultKubernetesClient kubernetesClient() throws Exception {
		String idToken = getToken();
		String masterUrl = getMasterURL();


		if(idToken == null || masterUrl == null) {
			System.err.println("VM arguments 설정 후 실행 하세요...\n-DmasterUrl=xxx.xxx.xxx.xx:12345 -Dtoken=xxxxxx");
			System.exit(-1);
		}

		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		
		Config config = new ConfigBuilder().withMasterUrl(masterUrl).withOauthToken(idToken).withTrustCerts(true).withWatchReconnectLimit(-1).build();
		DefaultKubernetesClient client = new DefaultKubernetesClient(config);

		return client;

	}
	
	static String idToken = null;

	public static String getMasterURL() {
		if(MASTER_URL == null || MASTER_URL.isEmpty()) {
			MASTER_URL = System.getProperty("masterUrl");
		}
		
		return MASTER_URL;
	}
	
	public static String getToken() {

		if(idToken != null) {
			return idToken;
		}
		
		File tokenFile = new File("/var/run/secrets/kubernetes.io/serviceaccount/token");

		log.debug("Token File exists :" + tokenFile.exists());
		if (tokenFile.exists()) {
			try {
				idToken = readFile(tokenFile);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		} else {
			idToken = System.getProperty("token");
		}
		
		
		return idToken;
	}
	
	/**
	 * HTTP Client
	 * 
	 * @return
	 * @throws Exception
	 */
	public static RestTemplate getRestTemplate() throws Exception {
		TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}
		} };

		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(null, trustManagers, new SecureRandom());

		SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
		CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setHttpClient(httpClient);
		requestFactory.setConnectionRequestTimeout(1000 * 20);
		requestFactory.setConnectTimeout(1000 * 10);
		requestFactory.setReadTimeout(1000 * 10);

		RestTemplate restTemplate = new RestTemplate(requestFactory);

		return restTemplate;
	}

	/**
	 * @param file
	 * @return
	 * @throws IOException
	 */
	private static String readFile(File file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = null;
		StringBuilder stringBuilder = new StringBuilder();

		try {
			while ((line = reader.readLine()) != null) {

				log.debug("$ " + line);

				stringBuilder.append(line);
			}

			return stringBuilder.toString();
		} finally {
			reader.close();
		}
	}

	/**
	 * @param exchange
	 * @throws Exception
	 */
	public static void doCreateService(Exchange exchange) throws Exception {
		Service service = null;
		String serviceName = exchange.getProperty(KubernetesConstants.KUBERNETES_SERVICE_NAME, String.class);
		String namespaceName = exchange.getProperty(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
		String loadBalancerIP = exchange.getProperty("LOAD_BALANCER_IP", String.class);
		String type = exchange.getProperty("TYPE", String.class);
		String component = exchange.getProperty("COMPONENT", String.class);

		if (serviceName == null) {
			log.error("Create a specific service require specify a service name");
			throw new IllegalArgumentException("Create a specific service require specify a service name");
		}
		if (namespaceName == null) {
			log.error("Create a specific service require specify a namespace name");
			throw new IllegalArgumentException("Create a specific service require specify a namespace name");
		}
		ServiceSpec serviceSpec = new ServiceSpec();
		serviceSpec.setType("LoadBalancer");

		Map<String, String> selector = new HashMap<String, String>();
		selector.put("app", serviceName + "-mariadb");
		serviceSpec.setSelector(selector);

		List<ServicePort> ports = new ArrayList<ServicePort>();
		ServicePort sp = new ServicePort();
		sp.setProtocol("TCP");
		sp.setPort(3306);
		ports.add(sp);
		serviceSpec.setPorts(ports);
//		serviceSpec.setLoadBalancerIP(loadBalancerIP);

		Map<String, String> labels = new HashMap<>();
		labels.put("release", serviceName);
		labels.put("app", "mariadb");
		labels.put("component", component);

		Map<String, String> annotations = new HashMap<>();
		annotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", type);

		String loadbalancerName = serviceName + "-mariadb-loadbalancer-" + type;

		Service serviceCreating = new ServiceBuilder().withNewMetadata().withName(loadbalancerName).withLabels(labels).withAnnotations(annotations).endMetadata().withSpec(serviceSpec).build();
		service = kubernetesClient().services().inNamespace(namespaceName).createOrReplace(serviceCreating);
		// MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
		// exchange.getOut().setBody(service);
	}
	
	/**
	 * @param pod
	 * @return
	 */
	public static boolean IsReady(Pod pod) {
		boolean isSuccess = false;
		
		try {
			PodStatus status = pod.getStatus();
			String name = pod.getMetadata().getName();
			String phase = status.getPhase();
			
			String reason = status.getReason();
			String message = status.getMessage();
			
			boolean isInitialized = false;
			boolean isReady = false;
			boolean isPodScheduled = false;
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				String podConditionMessage = condition.getMessage();
				String podConditionReason = condition.getReason();
				
				if ("Initialized".equals(condition.getType())) {
					isInitialized = Boolean.parseBoolean(condition.getStatus());
				}
				
				if ("Ready".equals(condition.getType())) {
					isReady = Boolean.parseBoolean(condition.getStatus());
				}
				
				if ("PodScheduled".equals(condition.getType())) {
					isPodScheduled = Boolean.parseBoolean(condition.getStatus());
				}
			}
			
			List<ContainerStatus> containerStatuses = status.getContainerStatuses();
			
			boolean isContainerReady = false;
			for (ContainerStatus containerStatus : containerStatuses) {
				Boolean ready = containerStatus.getReady();
				if (!ready.booleanValue()) {
					isContainerReady = false;
					break;
				} else {
					isContainerReady = true;
				}
			}
			
			if (isInitialized && isReady && isPodScheduled && isContainerReady) {
				isSuccess = true;
			} else {
				log.info("Name : {}, Initialized : {}, Ready : {}, PodScheduled : {}, isContainerReady : {}, reason : {}, message : {}", name, isInitialized, isReady, isPodScheduled, isContainerReady, reason, message);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return isSuccess;
	}

	/**
	 * @param exchange
	 * @return
	 * @throws Exception
	 */
	public static synchronized boolean doDeleteService(String namespaceName, String exposeServiceName) throws Exception {

		if (exposeServiceName == null) {
			log.error("Delete a specific service require specify a service name");
			throw new IllegalArgumentException("Delete a specific service require specify a service name");
		}
		if (namespaceName == null) {
			log.error("Delete a specific service require specify a namespace name");
			throw new IllegalArgumentException("Delete a specific service require specify a namespace name");
		}
		return kubernetesClient().services().inNamespace(namespaceName).withName(exposeServiceName).delete();
	}


	/**
	 * @param namespace
	 * @param podName
	 * @return
	 * @throws Exception
	 */
	public static String[] getPodLog(String namespace, String podName) throws Exception {
		DefaultKubernetesClient client;
		String[] lines = null;
		
		try {
			client = kubernetesClient();

			if (client != null) {
				String app = client.pods().inNamespace(namespace).withName(podName).get().getMetadata().getLabels().get("app");
				String log = null;
				if ("redis".equals(app)) {
					String name = client.pods().inNamespace(namespace).withName(podName).get().getSpec().getContainers().get(0).getName();
					PrettyLoggable<String, LogWatch> tailingLines = client.pods().inNamespace(namespace).withName(podName).inContainer(name).tailingLines(1000);
					log = tailingLines.getLog();
				} else if ("mariadb".equals(app)) {
					PrettyLoggable<String, LogWatch> tailingLines = client.pods().inNamespace(namespace).withName(podName).inContainer("mariadb").tailingLines(1000);
					log = tailingLines.getLog();
				}
				if (log != null) {
					log = log.replaceAll("\\[\\dm|\\[[\\d]{2}[;][\\d][;][\\d]m", "");
					lines = log.split("\n");
				}
 
//				podLog = unescape(podLog);
//				String unescapeString = unescapeJava(tailingLines.getLog());
//				podLog = unescapeString.replace("\\n",System.getProperty("line.separator"));	
//				podLog = podLog.replaceAll("(\r\n|\r|\n|\n\r)", System.getProperty("line.separator"));				
//				System.out.println("****" + podLog);
			}
			return lines;
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
		}

		return null;
	}

    public static String unescape(String string) {
        StringBuilder builder = new StringBuilder();
        builder.ensureCapacity(string.length());
        int lastPos = 0, pos = 0;
        char ch;
        while (lastPos < string.length()) {
            pos = string.indexOf("%", lastPos);
            if (pos == lastPos) {
                if (string.charAt(pos + 1) == 'u') {
                    ch = (char) Integer.parseInt(string
                            .substring(pos + 2, pos + 6), 16);
                    builder.append(ch);
                    lastPos = pos + 6;
                } else {
                    ch = (char) Integer.parseInt(string
                            .substring(pos + 1, pos + 3), 16);
                    builder.append(ch);
                    lastPos = pos + 3;
                }
            } else {
                if (pos == -1) {
                    builder.append(string.substring(lastPos));
                    lastPos = string.length();
                } else {
                    builder.append(string.substring(lastPos, pos));
                    lastPos = pos;
                }
            }
        }
        return builder.toString();
    }
	
	/**
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static ZDBEntity getPodResources(String namespace, String serviceType, String serviceName) throws Exception {
		DefaultKubernetesClient client = kubernetesClient();

	    ZDBType dbType = ZDBType.getType(serviceType);
	
		ZDBEntity entity = new ZDBEntity();
		entity.setServiceName(serviceName);
		entity.setNamespace(namespace);
		
		List<PodSpec> podSpecList = new ArrayList<>();
		List<PersistenceSpec> pvcSpecList = new ArrayList<>();

		String selector = new String();
		String podType =  new String();
		
		List<Pod> items = client.inNamespace(namespace).pods().list().getItems();
		for (Pod pod : items) {
			if(pod.getMetadata().getLabels() == null) {
				continue;
			}
			if(pod.getMetadata().getLabels().get("release") == null) {
				continue;
			}
			if (serviceName.equals(pod.getMetadata().getLabels().get("release"))) {
//				podList.add(pod);
				
				io.fabric8.kubernetes.api.model.PodSpec spec = pod.getSpec();
				List<Container> containers = spec.getContainers();

			    switch (dbType) {
			    case MariaDB: 
			    	selector = serviceType;
			    	podType = pod.getMetadata().getLabels().get("component");
			    	break;
			    case Redis:
			    	selector = serviceName;
			    	podType = pod.getMetadata().getLabels().get("role");
			    	break;
			    default:
			    	break;
			    }
				
				for (Container container : containers) {

					if(container.getName().toLowerCase().startsWith(selector.toLowerCase())) {	
						ResourceRequirements  resources = container.getResources();
						if(resources != null) {
							try {
								//String podType = pod.getMetadata().getLabels().get("component");
								
								PodSpec podSpec = new PodSpec();
								if(resources.getRequests() == null) {
									continue;
								}
								String cpu = resources.getRequests().get("cpu").getAmount();
								String memory = resources.getRequests().get("memory").getAmount();
								
								podSpec.setPodType(podType);
								podSpec.setPodName(pod.getMetadata().getName());
								
								ResourceSpec rSpec = new ResourceSpec();
								if(!cpu.endsWith("m")) {
									cpu = (Integer.parseInt(cpu) * 1000) +"m";
								}
								rSpec.setCpu(cpu);
								rSpec.setMemory(memory);
								rSpec.setResourceType("requests");
								
								podSpec.setResourceSpec(new ResourceSpec[] {rSpec});
								
								podSpecList.add(podSpec);
							} finally {
								
							}
						}
					}
				}
			}
		}
		
		entity.setPodSpec(podSpecList.toArray(new PodSpec[] {}));
		
		List<PersistentVolumeClaim> pvcs = client.inNamespace(namespace).persistentVolumeClaims().list().getItems();
		for (PersistentVolumeClaim pvc : pvcs) {
			if(pvc.getMetadata().getLabels() == null) {
				continue;
			}
			if(pvc.getMetadata().getLabels().get("release") == null) {
				continue;
			}
			if(pvc.getMetadata().getLabels().get("app") == null) {
				continue;
			}
			if (serviceName.equals(pvc.getMetadata().getLabels().get("release"))) {
				// podList.add(pod);
				PersistentVolumeClaimSpec pvcSpec = pvc.getSpec();

				if (serviceType.toLowerCase().equals(pvc.getMetadata().getLabels().get("app").toLowerCase())) {
					ResourceRequirements resources = pvcSpec.getResources();
					if (resources != null) {
						try {
							if(resources.getRequests() == null) {
								continue;
							}
							
						    switch (dbType) {
						    case MariaDB: 
						    	podType = pvc.getMetadata().getLabels().get("component");
						    	break;
						    case Redis:
						    	podType = pvc.getMetadata().getLabels().get("role");
						    	break;
						    default:
						    	break;
						    }							
							
							PersistenceSpec pSpec = new PersistenceSpec();
							pSpec.setPodType(podType);
							pSpec.setSize(resources.getRequests().get("storage").getAmount());
							pSpec.setPvcName(pvc.getMetadata().getName());
							
							pvcSpecList.add(pSpec);
						} finally {

						}
					}
				}
			}
		}
		entity.setPersistenceSpec(pvcSpecList.toArray(new PersistenceSpec[] {}));

		return entity;
	}
	
	/**
	 * @param namespace
	 * @param podName
	 * @return
	 * @throws Exception
	 */
	public static List<Event> getPodEvent(String namespace, String podName) throws Exception {
		List<Event> items = kubernetesClient().inNamespace(namespace).events().list().getItems();

		List<Event> eventList = new ArrayList<>();
		
		for(Event event : items) {
			ObjectReference involvedObject = event.getInvolvedObject();
			
			if(involvedObject.getKind().equals("Pod") && involvedObject.getName().equals(podName)) {
				eventList.add(event);
			}
		}
		
		return eventList;
	}
	
	/**
	 * @param namespace
	 * @param abnormalType
	 * @return
	 * @throws Exception
	 */
	public static List<PersistentVolumeClaim> getAbnormalPersistentVolumeClaims(String namespace, String abnormalType) throws Exception {
		List<PersistentVolumeClaim> abnormalPvcs = new ArrayList<PersistentVolumeClaim>();
		
		DefaultKubernetesClient client;
		int matchCount = 0;
		
		try {
			client = kubernetesClient();
			if (client != null) {
				PersistentVolumeClaimList pvcs = client.inNamespace(namespace).persistentVolumeClaims().list();
				
				String pvcReleaseName = new String();
				
				if ("unbound".equals(abnormalType)) {
					for (PersistentVolumeClaim pvc : pvcs.getItems()) {
						if (!"Bound".equals(pvc.getStatus().getPhase())) {	 // Available / Released / Failed
							abnormalPvcs.add(pvc);
						}
					}					
				} else if ("unused".equals(abnormalType)) {
					List<Release> releaseList = getReleaseList();
					
					for (PersistentVolumeClaim pvc : pvcs.getItems()) {
						pvcReleaseName = pvc.getMetadata().getLabels().get("release");
						
						matchCount = 0;
						
						for (Release service : releaseList) {
							if (pvcReleaseName.equals(service.getName())) {
								matchCount++;
							}
						}
						
						if (matchCount == 0) {
							abnormalPvcs.add(pvc);
						}
					}
				}
				
				return abnormalPvcs;
			}
		} catch (FileNotFoundException e) {
			log.error(e.getMessage(), e);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
		}

		return Collections.emptyList();
	}	
	
	/**
	 * @return
	 * @throws Exception
	 */
	public static List<PersistentVolume> getAbnormalPersistentVolumes() throws Exception {
		List<PersistentVolume> abnormalPvs = new ArrayList<PersistentVolume>();
		
		DefaultKubernetesClient client;
		try {
			client = kubernetesClient();
			if (client != null) {
				PersistentVolumeList pvs = client.inAnyNamespace().persistentVolumes().list();
				
				for (PersistentVolume pv : pvs.getItems()) {
					if (!"Bound".equals(pv.getStatus().getPhase())) {	 
						abnormalPvs.add(pv);
					}
				}					
	
				return abnormalPvs;
			} 
		} catch (FileNotFoundException e) {
			log.error(e.getMessage(), e);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
		}

		return Collections.emptyList();
	}		
	
	/**
	 * @param namespace
	 * @param secretName
	 * @param secretType
	 * @param newPassword
	 * @return
	 * @throws Exception
	 */
	public static String updateSecrets(final String namespace, final String secretName, final String secretType, final String newPassword) throws Exception {
		DefaultKubernetesClient client;

		String changedPassword = new String();
		
		try {
			client = kubernetesClient();

			if (client != null) {
				String password = Base64.getEncoder().encodeToString(newPassword.getBytes());
				
				Secret secret = client.secrets().inNamespace(namespace).withName(secretName).edit().addToData(secretType, password).done();
				changedPassword = new String(Base64.getDecoder().decode(secret.getData().get(secretType).getBytes()));
			}
		} catch (FileNotFoundException e) {
			log.error(e.getMessage(), e);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
		}
		
		return changedPassword;
	}	
	
	/**
	 * ExternalIPs of a service
	 * 
	 * @return java.util.List<String>
	 * @throws FileNotFoundException
	 */
	public static String getRedisHostIP(String namespace, String serviceName, String redisRole) throws Exception {
		List<Service> services = kubernetesClient().inNamespace(namespace).services().withLabel("release", serviceName).list().getItems();
 
        String ip = new String();

        for (Service service : services) {
	        try {
	          String role = service.getSpec().getSelector().get("role");
	          
	          if (role.equals(redisRole)) {
	        	  String loadbalancerType = service.getMetadata().getAnnotations().get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type");
	      		  if (!"prod".equals(profile)) {
		        	  if ("public".equals(loadbalancerType)) {
		        		  ip = service.getStatus().getLoadBalancer().getIngress().get(0).getIp();
		        	  } else {
		        		  log.error("Redis Service에 연결 할 수 없습니다.");
		        	  }
	    		  } else {	        	  
		        	  if ("private".equals(loadbalancerType)) {
		        		  ip = service.getSpec().getClusterIP();
		        	  } else {
		        		  log.error("Redis Service에 연결 할 수 없습니다.");
		        	  }
	    		  }		        	  
	          }
	        } catch (Exception e) {
	        	log.error(e.getMessage(), e);
	        }
	      }
		return ip;
	}	
	
	
	/**
	 * my.cnf > innodb_buffer_pool_size 값을 서버 환경에 따라 동적으로 계산.
	 * 
	 * 서버 메모리의 50%.
	 * 
	 * @param memory
	 * @return
	 */
//	public static String getBufferSize(String memory) {
//		String[] unit = new String[] { "E", "P", "T", "G", "M", "K" };
//
//		int memSize = 0;
//		String memUnit = "";
//		
//		String bufferSize = "128M";
//		try {
//			
//			for (String u : unit) {
//				if (memory.indexOf(u) > 0) {
//
//					memSize = Integer.parseInt(memory.substring(0, memory.indexOf(u)));
//
//					memUnit = memory.substring(memory.indexOf(u));
//
//					if (memUnit.startsWith("M")) {
//						bufferSize = ""+(memSize * 50 / 100) +"M";
//					} else if (memUnit.startsWith("G")) {
//						bufferSize = ""+(memSize * 1000 * 50 / 100)+"M";
//					}
//
//					break;
//				}
//			}
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//		}
//		
//		return bufferSize;
//	}
	
	public static String getBufferSize(String memory) {
		String bufferSize = "512M";

		try {
			int memSize = Integer.parseInt(memory);
			bufferSize = "" + (memSize * 50 / 100) + "M";
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return bufferSize;
	}
	
	/**
	 * @param memory
	 * @return
	 */
	public static int convertToMemory(String memory) {
		String[] unit = new String[] { "E", "P", "T", "G", "M", "K" };

		int memSize = 0;
		String memUnit = "";
		
		try {
			for (String u : unit) {
				if (memory.indexOf(u) > 0) {

					memSize = Integer.parseInt(memory.substring(0, memory.indexOf(u)));

					memUnit = memory.substring(memory.indexOf(u));

					if (memUnit.startsWith("M")) {
						return memSize;
					} else if (memUnit.startsWith("G")) {
						return memSize * 1000;
					}

					break;
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return -1;
	}
	
	public static int convertToCpu(String cpu) {
		int cpuMilicore = 0;
		
		boolean isMilicore = cpu.endsWith("m");
		
		if(isMilicore) {
			cpuMilicore = Integer.parseInt(cpu.substring(0, cpu.indexOf("m")));
		} else {
			int parseInt = Integer.parseInt(cpu);
			if(parseInt <= 128) {
				cpuMilicore = parseInt * 1000;				
			} else {
				cpuMilicore = parseInt;
			}
		}
		
		return cpuMilicore;
	}
		
	public static NodeList getNodes() throws Exception {
		DefaultKubernetesClient client;

		try {
			client = kubernetesClient();

			if (client != null) {
				return client.nodes().list();
			}			
		} catch (FileNotFoundException e) {
			log.error(e.getMessage(), e);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
		}

		return null;
	}	

	public static int getNodeCount() throws Exception {
		DefaultKubernetesClient client;

		try {
			client = kubernetesClient();

			if (client != null) {
				return client.nodes().list().getItems().size();
			}			
		} catch (FileNotFoundException e) {
			log.error(e.getMessage(), e);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
		}

		return 0;
	}	
	
	
}
