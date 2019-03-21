package com.zdb.core.service;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.google.gson.Gson;
import com.zdb.core.domain.CommonConstants;
import com.zdb.core.domain.DiskUsage;
import com.zdb.core.domain.EventType;
import com.zdb.core.domain.MetaData;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.ScheduleEntity;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.ZDBPersistenceEntity;
import com.zdb.core.domain.ZDBStatus;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.job.JobHandler;
import com.zdb.core.job.Job.JobResult;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.repository.ScheduleEntityRepository;
import com.zdb.core.repository.TagRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.util.HeapsterMetricUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.PodManager;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.LoadBalancerStatus;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import lombok.extern.slf4j.Slf4j;

@org.springframework.stereotype.Service("k8sService")
@Slf4j
@Configuration
public class K8SService {

	@Autowired
	private  MetadataRepository metadataRepository;
	
	@Autowired
	protected ZDBReleaseRepository releaseRepository;
	
	@Autowired
	protected TagRepository tagRepository;
	
	@Autowired
	protected DiskUsageRepository diskRepository;
	
	@Autowired
	private ScheduleEntityRepository scheduleEntity;
	
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
//		List<io.fabric8.kubernetes.api.model.Service> list = new ArrayList<>();
//		
//		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "Service");
//		
//		for (MetaData metaData : metaList) {
//			String meta = metaData.getMetadata();
//			io.fabric8.kubernetes.api.model.Service pvc = new Gson().fromJson(meta, io.fabric8.kubernetes.api.model.Service.class);
//			
//			list.add(pvc);
//		}
//		
//		return list;
		
		return K8SUtil.getServices(namespace, serviceName);
	
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

//		List<StatefulSet> list = new ArrayList<>();
//		
//		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "StatefulSet");
//		
//		for (MetaData metaData : metaList) {
//			String meta = metaData.getMetadata();
//			StatefulSet pvc = new Gson().fromJson(meta, StatefulSet.class);
//			
//			list.add(pvc);
//		}
//		
//		return list;
		return K8SUtil.getStatefulSets(namespace, serviceName);
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public List<HasMetadata> getServiceOverviewMeta(final String namespace, final String serviceName, boolean isDetail) throws Exception {

		List<HasMetadata> list = new ArrayList<>();
		long s = System.currentTimeMillis();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseName(namespace, serviceName);
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			if("StatefulSet".equals(metaData.getKind())) {
//				StatefulSet data = new Gson().fromJson(meta, StatefulSet.class);
//				list.add(data);
			} else if("Pod".equals(metaData.getKind())) {
//				Pod data = new Gson().fromJson(meta, Pod.class);
//				list.add(data);
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
//		System.out.println(">>>>>>>>>>>> getServiceOverviewMeta : " + (System.currentTimeMillis() - s));
		return list;
	}
	
	public ServiceOverview getServiceWithName(String namespace, String serviceType, String serviceName) throws Exception {
		// @getService
		ArrayList <ServiceOverview> serviceList = new ArrayList<>();

		List<String> apps = new ArrayList<>();
		ZDBType[] values = ZDBType.values();
		for (ZDBType type : values) {
			apps.add(type.getName().toLowerCase());
		}

		Iterable<ReleaseMetaData> releaseList = null;
		
		if (namespace == null || namespace.isEmpty()) {
			releaseList = releaseRepository.findAll();
		} else {
			releaseList = releaseRepository.findByNamespace(namespace);
		}

		List<ScheduleEntity> scheduleEntityList = scheduleEntity.findAll();
		
		for (ReleaseMetaData release : releaseList) {
			if("DELETED".equals(release.getStatus()) || "DELETING".equals(release.getStatus())) {
				continue;
			}
			
			String svcName = release.getReleaseName();
			String svcType = release.getApp();
			if (apps.contains(serviceType) && serviceType.equals(svcType) && serviceName.equals(svcName)) {
				ServiceOverview so = new ServiceOverview();

				so.setServiceName(serviceName);
				so.setNamespace(namespace);
				so.setPurpose(release.getPurpose());
				
				String version = release.getAppVersion();
				
				so.setServiceType(serviceType);
				so.setVersion(version);
				
				for (ScheduleEntity scheduleEntity : scheduleEntityList) {
					String sn = scheduleEntity.getServiceName();
					if(serviceName.equals(sn) && release.getNamespace().equals(namespace)) {
						String useYn = scheduleEntity.getUseYn();
						if(useYn != null && useYn.equals("Y")) {
							so.setBackupEnabled(true);
						} else {
							so.setBackupEnabled(false);
						}
						break;
					}
				}
				so.setPublicEnabled(release.getPublicEnabled() == null ? false : release.getPublicEnabled());
				
				so.setDeploymentStatus(release.getStatus());
				
				setServiceOverview(so, true);

				serviceList.add(so);

			}
		}

		return serviceList.isEmpty() ? null : serviceList.get(0);
	}
	
	
	
	/**
	 * @param serviceType
	 * @return
	 * @throws Exception
	 */
	public List<ServiceOverview> getServiceInServiceType(String serviceType) throws Exception {
		return getServiceInNamespaceInServiceType(null, serviceType);
	}
	
	/**
	 * @param namespaces : zdb-001, zdb002
	 * @return
	 * @throws Exception
	 */
	public List<ServiceOverview> getServiceInNamespaces(String namespaces, boolean detail) throws Exception {
		// @getService
		List<ServiceOverview> serviceList = new ArrayList<>();
		List<String> apps = new ArrayList<>();
		ZDBType[] values = ZDBType.values();
		for (ZDBType type : values) {
			apps.add(type.getName().toLowerCase());
		}
		
		List<ReleaseMetaData> releaseListWithNamespaces = new ArrayList<>();
		
		if (namespaces == null || namespaces.isEmpty()) {
			Iterable<ReleaseMetaData> releaseList = releaseRepository.findAll();
			for (ReleaseMetaData release : releaseList) {
				releaseListWithNamespaces.add(release);
			}
		} else {
			String[] split = namespaces.split(",");
			Set<String> set = new HashSet<String>();
			for (String ns : split) {
				set.add(ns.trim());
			}
			for (String ns : set) {
				
				String namespace = ns.trim();
				
				Iterable<ReleaseMetaData> releaseList = releaseRepository.findByNamespace(namespace);
				for (ReleaseMetaData release : releaseList) {
					releaseListWithNamespaces.add(release);
				}
			}
		}
		
		List<Namespace> namespaceList = getNamespaces();
		List<String> nsNameList = new ArrayList<>();
		for (Namespace ns : namespaceList) {
			nsNameList.add(ns.getMetadata().getName());
		}
		
		List<ScheduleEntity> scheduleEntityList = scheduleEntity.findAll();
		
		for (ReleaseMetaData release : releaseListWithNamespaces) {
			if ("DELETED".equals(release.getStatus()) || "DELETING".equals(release.getStatus())) {
				continue;
			}
			if(!nsNameList.contains(release.getNamespace())) {
				continue;
			}
			
			ServiceOverview so = new ServiceOverview();

			so.setServiceName(release.getReleaseName());
			so.setNamespace(release.getNamespace());
			so.setPurpose(release.getPurpose());
			
			String serviceType = release.getApp();
			String version = release.getAppVersion();
			
			so.setServiceType(serviceType);
			so.setVersion(version);
			
			for (ScheduleEntity scheduleEntity : scheduleEntityList) {
				String serviceName = scheduleEntity.getServiceName();
				String namespace = scheduleEntity.getNamespace();
				if(release.getReleaseName().equals(serviceName) && release.getNamespace().equals(namespace)) {
					String useYn = scheduleEntity.getUseYn();
					if(useYn != null && useYn.equals("Y")) {
						so.setBackupEnabled(true);
					} else {
						so.setBackupEnabled(false);
					}
					break;
				}
			}
			so.setPublicEnabled(release.getPublicEnabled() == null ? false : release.getPublicEnabled());
			
			so.setDeploymentStatus(release.getStatus());
			
			setServiceOverview(so, detail);
			serviceList.add(so);
		}
		return serviceList;
	}

	
	/**
	 * @param namespace
	 * @param serviceType
	 * @return
	 * @throws Exception
	 */
	public List<ServiceOverview> getServiceInNamespaceInServiceType(String namespace, String serviceType) throws Exception {
		// @getService
		ArrayList <ServiceOverview> serviceList = new ArrayList<>();

		List<String> apps = new ArrayList<>();
		ZDBType[] values = ZDBType.values();
		for (ZDBType type : values) {
			apps.add(type.getName().toLowerCase());
		}

		Iterable<ReleaseMetaData> releaseList = null;
		
		if (namespace == null || namespace.isEmpty()) {
			releaseList = releaseRepository.findAll();
		} else {
			releaseList = releaseRepository.findByNamespace(namespace);
		}

		List<ScheduleEntity> scheduleEntityList = scheduleEntity.findAll();
		
		for (ReleaseMetaData release : releaseList) {
			if("DELETED".equals(release.getStatus()) || "DELETING".equals(release.getStatus())) {
				continue;
			}
			String svcType = release.getApp();
			if (apps.contains(serviceType) && serviceType.equals(svcType)) {
				ServiceOverview so = new ServiceOverview();

				so.setServiceName(release.getReleaseName());
				so.setNamespace(release.getNamespace());
				so.setPurpose(release.getPurpose());
				
				String version = release.getAppVersion();
				
				so.setServiceType(serviceType);
				so.setVersion(version);
				
				for (ScheduleEntity scheduleEntity : scheduleEntityList) {
					String serviceName = scheduleEntity.getServiceName();
					if(release.getReleaseName().equals(serviceName) && release.getNamespace().equals(namespace)) {
						String useYn = scheduleEntity.getUseYn();
						if(useYn != null && useYn.equals("Y")) {
							so.setBackupEnabled(true);
						} else {
							so.setBackupEnabled(false);
						}
						break;
					}
				}
				so.setPublicEnabled(release.getPublicEnabled());
				
				so.setDeploymentStatus(release.getStatus());
				
				setServiceOverview(so, false);

				serviceList.add(so);

			}
		}

		return serviceList;
	
	}

	/**
	 * 
	 * 
	 * Config Maps Persistent Volume Claims Pods Secrets Services Stateful Sets Deployments Replica Sets
	 * 
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	private void setServiceOverview(ServiceOverview so, boolean detail) throws Exception {
		String serviceName = so.getServiceName();
		String namespace = so.getNamespace();

		List<HasMetadata> serviceOverviewMeta = getServiceOverviewMeta(namespace, serviceName, detail);
		
		List<ReplicaSet> replicaSets = new ArrayList<>();
		for (HasMetadata obj : serviceOverviewMeta) {
			if (obj instanceof ReplicaSet) {
				replicaSets.add((ReplicaSet) obj);
			}
		}
		
		List<Pod> pods = K8SUtil.getPods(namespace, serviceName);
		List<StatefulSet> statefulSets = K8SUtil.getStatefulSets(namespace, serviceName);
		
		so.getPods().addAll(pods);
		so.getStatefulSets().addAll(statefulSets);
		so.getReplicaSets().addAll(replicaSets);

		// 클러스터 사용 여부 
		so.setClusterEnabled(isClusterEnabled(so));
		
		// failover 사용 여부
		so.setFailoverEnabled(isFailoverEnabled(so));
		
		// failover 상태 
		//   - 정상 : MasterToMaster, 
		//   - failover 된 상태 : MasterToSlave
		so.setServiceFailOverStatus(getServiceFailOverStatus(namespace, "mariadb", serviceName));
		
		// 태그 정보 

		List<Tag> tagList = tagRepository.findByNamespaceAndReleaseName(namespace, serviceName);
		so.getTags().addAll(tagList);
		
		if ("CREATING".equals(so.getDeploymentStatus()) || "REQUEST".equals(so.getDeploymentStatus())) {
			so.setStatus(ZDBStatus.GRAY);
		} else {
			// 상태정보
			so.setStatus(getStatus(so));
		}
		
		so.setElapsedTime("");
		String lastTransitionTime = null;
		if (pods != null && pods.size() > 0) {
			for (Pod pod : pods) {
				
				Map<String, String> labels = pod.getMetadata().getLabels();
				String app = labels.get("app");
				boolean isMaster = false;
				if ("redis".equals(app)) {
					String role = labels.get("role");
					if ("master".equals(role)) {
						isMaster = true;
					}
				} else if ("mariadb".equals(app)) {
					String component = labels.get("component");
					if ("master".equals(component)) {
						isMaster = true;
					}
				}
				if (!isMaster) {
					continue;
				}

				PodStatus status = pod.getStatus();

				List<PodCondition> conditions = status.getConditions();

				for (PodCondition condition : conditions) {
					if ("Ready".equals(condition.getType())) {
						try {
							lastTransitionTime = condition.getLastTransitionTime();
							lastTransitionTime = lastTransitionTime.replace("T", " ").replace("Z", "");
							
							String elapsedTime = elapsedTime(lastTransitionTime);
							so.setElapsedTime(elapsedTime);
							
						} catch (Exception e) {
							so.setElapsedTime("");
						}
						break;
					}

				}
			}
		}
		

		if (detail) {
			List<ConfigMap> configMaps = new ArrayList<>();
			List<PersistentVolumeClaim> persistentVolumeClaims = new ArrayList<>();
			List<Secret> secrets = new ArrayList<>();
			List<io.fabric8.kubernetes.api.model.Service> services = new ArrayList<>();
			
			List<Deployment> deployments = new ArrayList<>();
			
			for (HasMetadata obj : serviceOverviewMeta) {
				if (obj instanceof ConfigMap) {
					configMaps.add((ConfigMap) obj);
				} else if (obj instanceof PersistentVolumeClaim) {
					persistentVolumeClaims.add((PersistentVolumeClaim) obj);
				} else if (obj instanceof Secret) {
					secrets.add((Secret) obj);
				} else if (obj instanceof io.fabric8.kubernetes.api.model.Service) {
					services.add((io.fabric8.kubernetes.api.model.Service) obj);
				} else if (obj instanceof Deployment) {
					deployments.add((Deployment) obj);
				}
			}

			so.getServices().addAll(services);
			so.getPersistentVolumeClaims().addAll(persistentVolumeClaims);
			so.getConfigMaps().addAll(configMaps);
			so.getSecrets().addAll(secrets);
			so.getDeployments().addAll(deployments);

			so.setClusterSlaveCount(getClusterSlaveCount(so));
			so.setResourceSpec(getResourceSpec(so));
			so.setPersistenceSpec(getPersistenceSpec(so));
			
			for (Pod pod : so.getPods()) {
				String podName = pod.getMetadata().getName();
				so.getResourceSpecOfPodMap().put(podName, getResourceSpec(so, podName));
				so.getPersistenceSpecOfPodMap().put(podName, getPersistenceSpec(so, podName));
			}

			for (Pod pod : so.getPods()) {
				String podName = pod.getMetadata().getName();
				DiskUsage disk = diskRepository.findOne(podName);
				so.getDiskUsageOfPodMap().put(podName, disk);
			}

			// metric 정보를 ui 에 담에서 전송...
			for (Pod pod : so.getPods()) {
				if (PodManager.isReady(pod)) {
					String podName = pod.getMetadata().getName();

					try {
						HeapsterMetricUtil metricUtil = new HeapsterMetricUtil();
						Object cpuUsage = metricUtil.getCPUUsage(namespace, podName);
						so.getCpuUsageOfPodMap().put(podName, cpuUsage);
						Object memoryUsage = metricUtil.getMemoryUsage(namespace, podName);
						so.getMemoryUsageOfPodMap().put(podName, memoryUsage);
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
			}
		}
	}
	
	/**
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public String getServiceFailOverStatus(String namespace, String serviceType, String serviceName) throws Exception {
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			
			List<io.fabric8.kubernetes.api.model.Service> services = getServices(namespace, serviceName);
			for (io.fabric8.kubernetes.api.model.Service service : services) {
				String sName = service.getMetadata().getName();
				String role = null;
				
				String selectorTarget = null;
				if("redis".equals(serviceType)) {
					if(sName.endsWith("master")) {
						role = "master";
					}
					
					selectorTarget = service.getSpec().getSelector().get("role");
					
				} else if("mariadb".equals(serviceType)) {
					role = service.getMetadata().getLabels().get("component");
					
					selectorTarget = service.getSpec().getSelector().get("component");
				}
				
				if(!"master".equals(role)) {
					continue;
				}
				
				// takeover 된 상태
				if("master".equals(role) && "slave".equals(selectorTarget)) {
					return "MasterToSlave";
				} else if("master".equals(role) && "master".equals(selectorTarget)) {
					return "MasterToMaster";
				} else {
					return "unknown";
				}
			}

			return "unknown";
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw e;
		}
	}
	
	private int getClusterSlaveCount(ServiceOverview so) {
		try {
			if (ZDBType.MariaDB.name().toLowerCase().equals(so.getServiceType().toLowerCase())) {
				return so.getPods().size() - 1;
			} else if (ZDBType.Redis.name().toLowerCase().equals(so.getServiceType().toLowerCase())) {
				if (!so.getReplicaSets().isEmpty()) {

					if(so.getReplicaSets().size() > 1) {
						for (ReplicaSet replicaSet : so.getReplicaSets()) {
							int intValue = replicaSet.getStatus().getReplicas().intValue();
							if(intValue < 1) {
								continue;
							} else {
								Integer availableReplicas = replicaSet.getStatus().getAvailableReplicas();
								if( availableReplicas != null && availableReplicas.intValue() > 0)
									return intValue;
							}
							
						}
					} else {
						return so.getReplicaSets().get(0).getStatus().getReplicas().intValue();
					}
				}
			}
		} finally {
		}
		return 0;
	}
	
	private PersistenceSpec getPersistenceSpec(ServiceOverview so, String podName) {
		PersistenceSpec pSpec = new PersistenceSpec();
		
		int storageSum = 0;
		String storageUnit = "";
		
		Pod pod = null;
		
		List<Pod> podsList = so.getPods();
		for (Pod p : podsList) {
			if(podName.equals(p.getMetadata().getName())) {
				pod = p;
				break;
			}
		}
		
		if(pod.getSpec().getVolumes() == null || pod.getSpec().getVolumes().size() == 0) {
			return null;
		}
		
		if(pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim() == null) {
			return null;
		}
		
		String claimName = pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim().getClaimName();
		
		List<PersistentVolumeClaim> pvcs = so.getPersistentVolumeClaims();
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
			if(claimName.equals(pvc.getMetadata().getName())) {
				if (so.getServiceName().equals(pvc.getMetadata().getLabels().get("release"))) {
					PersistentVolumeClaimSpec pvcSpec = pvc.getSpec();
					
					if (so.getServiceType().toLowerCase().equals(pvc.getMetadata().getLabels().get("app").toLowerCase())) {
						ResourceRequirements resources = pvcSpec.getResources();
						if (resources != null) {
							try {
								if(resources.getRequests() == null) {
									continue;
								}
								
								String[] unit = new String[] { "E", "P", "T", "G", "M", "K" };
								String amount = resources.getRequests().get("storage").getAmount();
								for (String u : unit) {
									if (amount.indexOf(u) > 0) {
										
										storageSum += Integer.parseInt(amount.substring(0, amount.indexOf(u)));
										storageUnit = amount.substring(amount.indexOf(u));
										break;
									}
									
									if(amount != null) {
										try {
											storageSum += Integer.parseInt(amount);
											storageUnit = "G";
											break;
										}catch(Exception e) {
										}
									}
								}
							} finally {
							}
						}
					}
				}
				break;
			}
		}
		
		pSpec.setSize(storageSum+""+storageUnit);
		
		return pSpec;
	
	}
	
	private PersistenceSpec getPersistenceSpec(ServiceOverview so) {
		PersistenceSpec pSpec = new PersistenceSpec();
		
		int storageSum = 0;
		String storageUnit = "";
		
		List<PersistentVolumeClaim> pvcs = so.getPersistentVolumeClaims();
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
			if (so.getServiceName().equals(pvc.getMetadata().getLabels().get("release"))) {
				// podList.add(pod);
				PersistentVolumeClaimSpec pvcSpec = pvc.getSpec();

				if (so.getServiceType().toLowerCase().equals(pvc.getMetadata().getLabels().get("app").toLowerCase())) {
					ResourceRequirements resources = pvcSpec.getResources();
					if (resources != null) {
						try {
							if(resources.getRequests() == null) {
								continue;
							}
							
						    String[] unit = new String[] { "E", "P", "T", "G", "M", "K" };
							String amount = resources.getRequests().get("storage").getAmount();
							for (String u : unit) {
								if (amount.indexOf(u) > 0) {

									storageSum += Integer.parseInt(amount.substring(0, amount.indexOf(u)));
									storageUnit = amount.substring(amount.indexOf(u));
									break;
								}
								
								if(amount != null) {
									try {
										storageSum += Integer.parseInt(amount);
										storageUnit = "G";
										break;
									}catch(Exception e) {
									}
								}
							}
						    
						} finally {

						}
					}
				}
			}
		}
		
		pSpec.setSize(storageSum+""+storageUnit);
		
		return pSpec;
	}
	
	private ResourceSpec getResourceSpec(ServiceOverview so, String podName) {
		String selector = new String();
		ZDBType dbType = ZDBType.getType(so.getServiceType());
		
		List<Pod> items = so.getPods();

		ResourceSpec resourceSpec = new ResourceSpec();
		
		int cpuSum = 0;
		int memSum = 0;
		
		String cpuUnit = "";
		String memUnit = "";
		
		for (Pod pod : items) {
			if(pod.getMetadata().getLabels() == null) {
				continue;
			}
			if(pod.getMetadata().getLabels().get("release") == null) {
				continue;
			}
			
			if (podName.equals(pod.getMetadata().getName()) && so.getServiceName().equals(pod.getMetadata().getLabels().get("release"))) {
				io.fabric8.kubernetes.api.model.PodSpec spec = pod.getSpec();
				List<Container> containers = spec.getContainers();

			    switch (dbType) {
			    case MariaDB: 
			    	selector = so.getServiceType();
			    	break;
			    case Redis:
			    	selector = so.getServiceName();
			    	break;
			    default:
			    	break;
			    }
			    
				for (Container container : containers) {
					if (container.getName().toLowerCase().startsWith(selector.toLowerCase())) {
						ResourceRequirements resources = container.getResources();
						if (resources != null) {
							try {
								if (resources.getRequests() == null) {
									continue;
								}
								String cpu = resources.getRequests().get("cpu").getAmount();

								if (cpu.endsWith("m")) {
									cpuSum += Integer.parseInt(cpu.substring(0, cpu.indexOf("m")));
									cpuUnit = "m";
								} else {
									cpuSum += Integer.parseInt(cpu);
								}

								// E, P, T, G, M, K
								String[] unit = new String[] { "E", "P", "T", "G", "M", "K" };
								String memory = resources.getRequests().get("memory").getAmount();

								for (String u : unit) {
									if (memory.indexOf(u) > 0) {

										memSum += Integer.parseInt(memory.substring(0, memory.indexOf(u)));

										memUnit = memory.substring(memory.indexOf(u));

										break;
									}
								}
							} finally {
							}
						}
					}
				}
			}
		}
		
		resourceSpec.setCpu(cpuSum+""+cpuUnit);
		resourceSpec.setMemory(memSum+""+memUnit);
		
		return resourceSpec;
	}
	
	private ResourceSpec getResourceSpec(ServiceOverview so) {
		String selector = new String();
		ZDBType dbType = ZDBType.getType(so.getServiceType());
		
		List<Pod> items = so.getPods();

		ResourceSpec resourceSpec = new ResourceSpec();
		
		int cpuSum = 0;
		int memSum = 0;
		
		String cpuUnit = "";
		String memUnit = "";
		
	    switch (dbType) {
	    case MariaDB: 
	    	selector = so.getServiceType();
	    	break;
	    case Redis:
	    	selector = so.getServiceName();
	    	break;
	    default:
	    	break;
	    }
	    
		for (Pod pod : items) {
			if(pod.getMetadata().getLabels() == null) {
				continue;
			}
			if(pod.getMetadata().getLabels().get("release") == null) {
				continue;
			}
			if (so.getServiceName().equals(pod.getMetadata().getLabels().get("release"))) {
				io.fabric8.kubernetes.api.model.PodSpec spec = pod.getSpec();
				List<Container> containers = spec.getContainers();

				for (Container container : containers) {
					if (container.getName().toLowerCase().startsWith(selector.toLowerCase())) {
						ResourceRequirements resources = container.getResources();
						if (resources != null) {
							try {
								PodSpec podSpec = new PodSpec();
								if (resources.getRequests() == null) {
									continue;
								}
								String cpu = resources.getRequests().get("cpu").getAmount();

								if (cpu.endsWith("m")) {
									cpuSum += Integer.parseInt(cpu.substring(0, cpu.indexOf("m")));
									cpuUnit = "m";
								} else {
									cpuSum += Integer.parseInt(cpu);
								}

								// E, P, T, G, M, K
								String[] unit = new String[] { "E", "P", "T", "G", "M", "K" };
								String memory = resources.getRequests().get("memory").getAmount();

								for (String u : unit) {
									if (memory.indexOf(u) > 0) {

										memSum += Integer.parseInt(memory.substring(0, memory.indexOf(u)));

										memUnit = memory.substring(memory.indexOf(u));

										break;
									}
								}
							} finally {
							}
						}
					}
				}
			}
		}
		
		resourceSpec.setCpu(cpuSum+""+cpuUnit);
		resourceSpec.setMemory(memSum+""+memUnit);
		
		return resourceSpec;
	}
	
	@Autowired
	private MessageSource messageSource;
	
	/**
	 * 경과 시간 계산.
	 * 
	 * @param endDate
	 * @return
	 */
	public String elapsedTime(String dateStr){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		long elapsedDays;
		long elapsedHours;
		long elapsedMinutes;
		long elapsedSeconds;
		try {
			Date endDate = sdf.parse(dateStr);
			long currentTime = System.currentTimeMillis();
			
			//milliseconds
			long different = currentTime - endDate.getTime();
			
			long secondsInMilli = 1000;
			long minutesInMilli = secondsInMilli * 60;
			long hoursInMilli = minutesInMilli * 60;
			long daysInMilli = hoursInMilli * 24;

			elapsedDays = different / daysInMilli;
			different = different % daysInMilli;
			
			elapsedHours = different / hoursInMilli;
			different = different % hoursInMilli;
			
			elapsedMinutes = different / minutesInMilli;
			different = different % minutesInMilli;
			
			elapsedSeconds = different / secondsInMilli;

			if (elapsedDays > 0) {
				return elapsedDays +" "+ messageSource.getMessage("elapsed.days", null, Locale.KOREA);
			} else if (elapsedHours > 0) {
				return elapsedHours +" "+ messageSource.getMessage("elapsed.hours", null, Locale.KOREA);
			} else if (elapsedMinutes > 0) {
				return elapsedMinutes +" "+ messageSource.getMessage("elapsed.minutes", null, Locale.KOREA);
			} else if (elapsedSeconds > 0) {
				return elapsedSeconds +" "+ messageSource.getMessage("elapsed.seconds", null, Locale.KOREA);
			} else {
				return "";
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return "";
	}
	
	private boolean isClusterEnabled(ServiceOverview so) {
		if (ZDBType.MariaDB.name().toLowerCase().equals(so.getServiceType().toLowerCase())) {
			return so.getStatefulSets().size() > 1 ? true : false;
		} else if (ZDBType.Redis.name().toLowerCase().equals(so.getServiceType().toLowerCase())) {
			return so.getStatefulSets().size() == 1 && so.getReplicaSets().size() >= 1 ? true : false;
		} else {
			return false;
		}
	}
	
	private String isFailoverEnabled(ServiceOverview so) {
		String eventKey = "service_"+so.getNamespace()+"_"+so.getServiceName();
		
		// 상태 조회..
		JobResult status = JobHandler.getInstance().getStatus(EventType.Auto_Failover_Enable, eventKey);
		if(status != null && status == JobResult.RUNNING) {
			return "running";
		}
		
		if (ZDBType.MariaDB.name().toLowerCase().equals(so.getServiceType().toLowerCase())) {
			if(so.getStatefulSets().size() > 1) {
//				"zdb-failover-enable", "true"
				List<StatefulSet> stsList = so.getStatefulSets();
				for (StatefulSet statefulSet : stsList) {
					Map<String, String> labels = statefulSet.getMetadata().getLabels();
					String enable = labels.get("zdb-failover-enable");
					if(enable != null && "true".equals(enable)) {
						return "on";
					}
					
				}
				
			}
		}
		
		return "off";
	}
	
	private ZDBStatus getStatus(ServiceOverview so) {
		
		if(so.getDeploymentStatus().equals("ERROR")) {
			return ZDBStatus.GRAY;
		}
		
		String app = null;
		String component = null;
		
		boolean masterStatus = false;
		boolean slaveStatus = false;
		
		for(Pod pod : so.getPods()) {
			app = pod.getMetadata().getLabels().get("app");
			
			if("mariadb".equals(so.getServiceType())) {
				component = pod.getMetadata().getLabels().get("component");
				
			} else if("redis".equals(so.getServiceType())) {
				component = pod.getMetadata().getLabels().get("role");
			}
			
			if (so.isClusterEnabled()) {
				if ("master".equals(component)) {
					masterStatus = K8SUtil.IsReady(pod);
				}

				if ("slave".equals(component)) {
					slaveStatus = K8SUtil.IsReady(pod);
				}
			} else {
				masterStatus = K8SUtil.IsReady(pod);
			}
		}
		
		if (so.isClusterEnabled()) {

			if (masterStatus && slaveStatus) {
				return ZDBStatus.GREEN;
			} else if (masterStatus && !slaveStatus) {
				return ZDBStatus.YELLOW;
			} else {
				return ZDBStatus.RED;
			}
		} else {
			if (masterStatus) {
				return ZDBStatus.GREEN;
			} else {
				return ZDBStatus.RED;
			}
		}
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
			ReplicaSet replicaSet = new Gson().fromJson(meta, ReplicaSet.class);
			
			list.add(replicaSet);
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

//		List<Pod> list = new ArrayList<>();
//		
//		List<MetaData> metaList = metadataRepository.findNamespaceAndReleaseNameAndKind(namespace, serviceName, "Pod");
//		
//		for (MetaData metaData : metaList) {
//			String meta = metaData.getMetadata();
//			Pod pod = new Gson().fromJson(meta, Pod.class);
//			
//			list.add(pod);
//		}
//		
//		return list;
		return K8SUtil.getPods(namespace, serviceName);
	}
	
	public Pod getPod(final String namespace, final String serviceName, String role) throws Exception {
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		
		List<Pod> pods = client.pods().inNamespace(namespace).withLabel("release", serviceName).list().getItems();
		
		for (Pod pod : pods) {
			String app = pod.getMetadata().getLabels().get("app");

			if("mariadb".equals(app)) {
				String component = pod.getMetadata().getLabels().get("component");
				
				if(role.equals(component)) {
					return pod;
				}
			} else if("redis".equals(app)) {
				String component = pod.getMetadata().getLabels().get("role");
				if(role.equals(component)) {
					return pod;
				}
			}
		}
		
		return null;
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
			Deployment deployment = new Gson().fromJson(meta, Deployment.class);
			
			list.add(deployment);
		}
		
		return list;
	}

	public List<Namespace> getNamespaces() throws Exception {
		List<Namespace> list = new ArrayList<>();
		
		List<MetaData> metaList = metadataRepository.findNamespaceAll();
		
		for (MetaData metaData : metaList) {
			String meta = metaData.getMetadata();
			Namespace namespace = new Gson().fromJson(meta, Namespace.class);

			Map<String, String> labels = namespace.getMetadata().getLabels();
			if (labels != null) {
				// zdb namespace label
				String name = labels.get(CommonConstants.ZDB_LABEL);
				if ("true".equals(name)) {
					list.add(namespace);
				}
			}
		}
		
		return list;
	
	}
	
	/**
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @throws Exception
	 */
	public int createPublicService(String namespace, String serviceType, String serviceName) throws Exception {
		ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
		
		String chartVersion = releaseMetaData.getChartVersion();
		Boolean clusterEnabled = releaseMetaData.getClusterEnabled();
		
		int createServiceCount = 0;
		
		{
			InputStream templateInputStream = new ClassPathResource(serviceType+"/create_public_svc.template").getInputStream();
			String inputYaml = IOUtils.toString(templateInputStream, StandardCharsets.UTF_8.name());
			inputYaml = inputYaml.replace("${role}", "master");
			inputYaml = inputYaml.replace("${chartVersion}", chartVersion);
			inputYaml = inputYaml.replace("${serviceName}", serviceName);
	
			log.info(inputYaml);
	
			InputStream is = new ByteArrayInputStream(inputYaml.getBytes(StandardCharsets.UTF_8));
			Service ss = K8SUtil.kubernetesClient().services().inNamespace(namespace).load(is).get();
			K8SUtil.kubernetesClient().services().inNamespace(namespace).createOrReplace(ss);
			
			releaseMetaData.setPublicEnabled(true);
			releaseRepository.save(releaseMetaData);
			
			createServiceCount++;
		}
		
		if(clusterEnabled == null) {
			try {
				List<Pod> pods = getPods(namespace, serviceName);
				if(pods.size() > 1) {
					clusterEnabled = true;
				} else {
					clusterEnabled = false;
				}
				
				releaseMetaData.setClusterEnabled(clusterEnabled);
				releaseRepository.save(releaseMetaData);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
		
		if(clusterEnabled) {
			InputStream templateInputStream = new ClassPathResource(serviceType+"/create_public_svc.template").getInputStream();
			String inputYaml = IOUtils.toString(templateInputStream, StandardCharsets.UTF_8.name());
			inputYaml = inputYaml.replace("${role}", "slave");
			inputYaml = inputYaml.replace("${chartVersion}", chartVersion);
			inputYaml = inputYaml.replace("${serviceName}", serviceName);
	
			log.info(inputYaml);
	
			InputStream is = new ByteArrayInputStream(inputYaml.getBytes(StandardCharsets.UTF_8));
			Service ss = K8SUtil.kubernetesClient().services().inNamespace(namespace).load(is).get();
			K8SUtil.kubernetesClient().services().inNamespace(namespace).createOrReplace(ss);
			
			createServiceCount++;
		}
		
		if (createServiceCount > 0) {
			final int createdServiceCnt = createServiceCount;
			long s = System.currentTimeMillis();
			
			Thread.sleep(1000);
			
			while ( (System.currentTimeMillis() - s) < 30 * 1000 ) {
				
				try {

					Thread.sleep(500);
					int svcCount = 0;
					List<Service> services = getServices(namespace, serviceName);
					for (Service service : services) {
						Map<String, String> annotations = service.getMetadata().getAnnotations();
						if (annotations != null && annotations.containsKey("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type")) {

							String type = annotations.get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type");

							if ("public".equals(type)) {
								svcCount++;
							}
						}
					}

					if (svcCount == createdServiceCnt) {
						System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						break;
					}

				} catch (Exception e) {
				}
				
			}
		}
		
		return createServiceCount;
	}
	
	/**
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public int deletePublicService(String namespace, String serviceType, String serviceName) throws Exception {
		ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);

		int deleteServiceCount = 0;

		List<Service> services = K8SUtil.getServices(namespace, serviceName);
		
		for (Service service : services) {
			// "service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type": "public"
			Map<String, String> annotations = service.getMetadata().getAnnotations();
			if (annotations != null && annotations.containsKey("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type")) {

				String type = annotations.get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type");

				if ("public".equals(type)) {
					Boolean delete = K8SUtil.kubernetesClient().inNamespace(namespace).services().withName(service.getMetadata().getName()).delete();
					if (delete) {
						deleteServiceCount++;
					}
				}
			}
		}
		
		if (deleteServiceCount > 0) {
			releaseMetaData.setPublicEnabled(false);

			releaseRepository.save(releaseMetaData);
		}

		return deleteServiceCount;
	}
	
	/**
	 * @param namespace
	 * @param serviceName
	 * @param podName
	 * @param persistenceSpec
	 * @throws Exception
	 */
	public PersistenceSpec createPersistentVolumeClaim(ZDBPersistenceEntity entity) throws Exception {
		
		String namespace = entity.getNamespace();
		String serviceName = entity.getServiceName();
		String podName = entity.getPodName();
		String serviceType = entity.getServiceType();
		
		PersistenceSpec persistenceSpec = entity.getPersistenceSpec();
		
		String pvcName = null;
		String storageSize = "";
		String billingType = "hourly";
		String storageClassName = "";
		
		if(namespace == null || namespace.isEmpty()) {
			throw new IllegalArgumentException("namespace 입력하세요.");
		}
		
		if(serviceName == null || serviceName.isEmpty()) {
			throw new IllegalArgumentException("serviceName 입력하세요.");
		}
		
		if(podName == null || podName.isEmpty()) {
			throw new IllegalArgumentException("podName 입력하세요.");
		}
		
		if(serviceType == null || serviceType.isEmpty()) {
			throw new IllegalArgumentException("serviceType 입력하세요.");
		}
		
		if(persistenceSpec != null) {
			pvcName = persistenceSpec.getPvcName();
			storageClassName = persistenceSpec.getStorageClass();
			billingType = persistenceSpec.getBillingType();
			storageSize = persistenceSpec.getSize();
			if(storageSize != null && !storageSize.isEmpty()) {
				if (storageSize.indexOf("Gi") > 0) {

				} else {
					storageSize = storageSize+"Gi";
				}
			} else {
				throw new IllegalArgumentException("PersistentVolumeClaim storage size 를 입력하세요.");
			}
		} else {
			throw new IllegalArgumentException("PersistentVolumeClaim 정보를 입력하세요.");
		}
		
		String role = "";
		
		List<Pod> pods = getPods(namespace, serviceName);
		for (Pod pod : pods) {
			
			String name = pod.getMetadata().getName();
			if(podName.equals(name)) {
				serviceType = pod.getMetadata().getLabels().get("app");
				if ("mariadb".equals(serviceType)) {
					role = pod.getMetadata().getLabels().get("component");
				} else if ("redis".equals(serviceType)) {
					role = pod.getMetadata().getLabels().get("role");
				}
				
				break;
			}
		}
		
		try {
			List<PersistentVolumeClaim> pvcList = getPersistentVolumeClaims(namespace, serviceName);
			for (PersistentVolumeClaim persistentVolumeClaim : pvcList) {

				String svcRole = "";
				if ("mariadb".equals(serviceType)) {
					svcRole = persistentVolumeClaim.getMetadata().getLabels().get("component");
				} else if ("redis".equals(serviceType)) {
					svcRole = persistentVolumeClaim.getMetadata().getLabels().get("role");
				}

				if (!role.equals(svcRole)) {
					continue;
				}

				if (storageClassName == null || storageClassName.isEmpty()) {
					storageClassName = persistentVolumeClaim.getSpec().getStorageClassName();
				}
				
				if (billingType == null || billingType.isEmpty()) {
					billingType = "hourly";
				}
				
				if (pvcName == null || pvcName.isEmpty()) {
					String name = persistentVolumeClaim.getMetadata().getName();

					// data-ns-zdb-02-demodb-mariadb-slave-0
					String[] split = name.split("-");
					String indexValue = split[split.length - 1];
					int index = Integer.parseInt(indexValue) + 1;

					pvcName = name.substring(0, name.length() - 1) + "" + index;
					
					while(getPersistentVolumeClaim(namespace, pvcName) != null) {
						pvcName = name.substring(0, name.length() - 1) + "" + ++index;
					}
					
					persistenceSpec.setPvcName(pvcName);
				} else {
					break;
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		Map<String, String> labels = new HashMap<>();
		labels.put("release", serviceName);
		labels.put("billingType", billingType);
		
		if("mariadb".equals(serviceType)) {
			labels.put("app", "mariadb");
			labels.put("component", role);
		} else if("redis".equals(serviceType)) {
			labels.put("app", "redis");
			labels.put("role", role);
		} else {
			log.error("{} 는 지원하지 않는 서비스 타입입니다.",serviceType);
		}
		
		new CreatePersistentVolumeClaim().createPersistentVolumeClaim(namespace, storageSize, storageClassName, pvcName, labels);
		
//		PersistentVolumeClaimSpec pvcSpec = new PersistentVolumeClaimSpec();
//
//		ResourceRequirements rr = new ResourceRequirements();
//
//		Map<String, Quantity> req = new HashMap<String, Quantity>();
//		req.put("storage", new Quantity(storageSize));
//		rr.setRequests(req);
//		pvcSpec.setResources(rr);
//
//		List<String> access = new ArrayList<String>();
//		access.add("ReadWriteOnce");
//		pvcSpec.setAccessModes(access);
//
//		Map<String, String> annotations = new HashMap<>();
//		annotations.put("volume.beta.kubernetes.io/storage-class", storageClassName);
//		
//		PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder()
//				.withNewMetadata()
//				.withName(pvcName)
//				.withAnnotations(annotations)
//				.withLabels(labels)
//				.endMetadata()
//				.withSpec(pvcSpec)
//				.build();
//
//		
//		DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();
//		PersistentVolumeClaim pvc = kubernetesClient.persistentVolumeClaims().inNamespace(namespace).create(pvcCreating);
//
//		final CountDownLatch latch = new CountDownLatch(1);
//		
//		final String _pvcName = pvcName;
//		
//		kubernetesClient.persistentVolumeClaims().inNamespace(namespace).watch(new Watcher<PersistentVolumeClaim>() {
//
//			@Override
//			public void eventReceived(Action action, PersistentVolumeClaim resource) {
//				String status = null;
//				if (resource instanceof PersistentVolumeClaim) {
//					PersistentVolumeClaim meta = (PersistentVolumeClaim) resource;
//					if(_pvcName.equals(meta.getMetadata().getName()))
//					status = meta.getStatus().getPhase();
//					if("Bound".equalsIgnoreCase(status)) {
//						System.out.println(_pvcName + " created.!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//						latch.countDown();
//					}
//				}
//			}
//
//			@Override
//			public void onClose(KubernetesClientException cause) {
//				
//			}
//			
//		});
//		
//		latch.await();
		
		return persistenceSpec;
	}
	
	public List<StatefulSet> getAutoFailoverServices(String namespace, String serviceName) {
		List<StatefulSet> serviceList = new ArrayList<StatefulSet>();
		
		if(namespace == null) {
			namespace = "-";
		}
		if(serviceName == null) {
			serviceName = "-";
		}

		try {
			List<HasMetadata> zdbhaInstances = getZDBHAInstances(namespace, serviceName);
			for (HasMetadata hasMetadata : zdbhaInstances) {

				// master pod 가 down 상태일 경우에도 체크 대상에는 포함되어야 되기때문에
				// StatefulSet 으로 부터 대상을 선정한다.
				if ("StatefulSet".equals(hasMetadata.getKind())) {
					if (namespace != null && !namespace.isEmpty()) {
						if ("-".equals(namespace)) {
							serviceList.add((StatefulSet) hasMetadata);
						} else {
							String ns = hasMetadata.getMetadata().getNamespace();
							if (ns.equals(namespace)) {
								serviceList.add((StatefulSet) hasMetadata);
							}
						}
					} else {
						serviceList.add((StatefulSet) hasMetadata);
					}
				}
			}
		} catch (Exception e) {
			log.error("AutoFailover 대상 수집중 오류 발생.", e);
		}

		return serviceList;
	}
	
	public static boolean isFailoverService(String namespace, String serviceName) throws Exception {

		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();){
			return isFailoverService(client, namespace, serviceName);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return false;
	}	
	
	public static boolean isFailoverEnable(String namespace, String serviceName) throws Exception {

		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
			if (serviceName != null && !serviceName.isEmpty()) {
				List<StatefulSet> items = client.inNamespace(namespace).apps().statefulSets()
						.withLabel("app", "mariadb")
						.withLabel("component", "master")
						.withLabel("zdb-failover-enable", "true")
						.withLabel("release", serviceName).list().getItems();

				if (items != null && !items.isEmpty()) {
					return true;
				} else {
					return false;
				}
			}
			return false;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return false;
	}	
	
	/**
	 * 이미 failover 된 인스턴스 여부 
	 * Service 의 label 이 아래 조건인 경우
	 *  - withLabel("app", "mariadb")
	 *  - withLabel("component", "master")
	 * 
	 * 아래와 같이 selector.component 가 slave 인 경우 failover 된 서비스로 판단 true 반환 
	 *  => '{"spec": {"selector": {"component": "slave"}}}'
	 * 
	 * @param client
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public static boolean isFailoverService(DefaultKubernetesClient client, String namespace, String serviceName) throws Exception {

		try{
			// app=mariadb,chart=mariadb-4.2.3,component=master,heritage=Tiller,release=zdb-test2-mha
			if(serviceName != null && !serviceName.isEmpty()) {
				List<Service> items = client.inNamespace(namespace).services()
				.withLabel("app", "mariadb")
				.withLabel("component", "master")
				.withLabel("release", serviceName)
				.list().getItems();
				
				if(items != null && !items.isEmpty()) {
					for (Service service : items) {
						String selector = service.getSpec().getSelector().get("component");
						if("slave".equals(selector)) {
							return true;
						}
					}
				} else {
					log.warn("master 서비스 LB 가 존재하지 않습니다. {}", serviceName);
					return true;
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return false;
	}	
	
	/**
	 * zdb-ha-manager를 이용한 auto-failover 를 사용하는 인스턴스 목록 반환.
	 * 
	 * 조건 : 
	 *  # StatefulSet label 에 아래와 같이 설정된 경우 
	 *  - withLabel("app", "mariadb")
	 *  - withLabel("zdb-failover-enable", "true")
	 *  - withLabel("component", "master")
	 *  
	 *  위 조건에 맞는 StatefulSet 중에서 이미 failover 된 서비스 제외하고
	 *  pod slave 상태가 ready = true 의 경우 모니터링 대상에 포함됨. 
	 *  
	 * @return
	 * @throws Exception
	 */
	public List<HasMetadata> getZDBHAInstances(String ns, String releaseName) throws Exception {

		List<HasMetadata> haZDBInstanceList = new ArrayList<>();
		
		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
			// app=mariadb,chart=mariadb-4.2.3,component=master,heritage=Tiller,release=zdb-test2-mha
			NamespacedKubernetesClient namespaceClient = null;
			if(ns == null || ns.isEmpty() || "-".equals(ns)) {
				namespaceClient = client.inAnyNamespace();
			} else {
				namespaceClient = client.inNamespace(ns);
			}
			
			List<StatefulSet> stsItems = null;

			if(releaseName == null || releaseName.isEmpty() || "-".equals(releaseName)) {
				stsItems = namespaceClient.apps().statefulSets()
						.withLabel("app", "mariadb")
						.withLabel("zdb-failover-enable", "true")
						.withLabel("component", "master")
						.list().getItems();
			} else {
				stsItems = namespaceClient.apps().statefulSets()
						.withLabel("app", "mariadb")
						.withLabel("zdb-failover-enable", "true")
						.withLabel("component", "master")
						.withLabel("release", releaseName)
						.list().getItems();
			}
			
			for (StatefulSet sts : stsItems) {
				String namespace = sts.getMetadata().getNamespace();
				String serviceName = sts.getMetadata().getLabels().get("release");
				
				// failover 된 서비스는 skip 
				boolean isFailoverService = isFailoverService(client, namespace, serviceName);
				if(isFailoverService) {
					log.warn("{} {} slave failover 된 서비스 입니다.", namespace, serviceName);
					continue;
				}
				
				// slave 가 live 한 경우만 failover 대상임.
				List<Pod> podItems = client.inNamespace(namespace).pods()
						.withLabel("app", "mariadb")
						.withLabel("release", serviceName)
						.list().getItems();
				
				for (Pod pod : podItems) {
					String component = pod.getMetadata().getLabels().get("component");
					
					if ("slave".equals(component) && PodManager.isReady(pod)) {
						haZDBInstanceList.add(sts);
						haZDBInstanceList.addAll(podItems);
						break;
					}
				}
				
				
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return haZDBInstanceList;
	}
	
	public List<String> getWorkerPools() throws Exception {
		List<String> workerPools = new ArrayList<>();
		NodeList nodeList = K8SUtil.kubernetesClient().nodes().list();
		List<Node> nodes = nodeList.getItems();
		for (Node node : nodes) {
			String role = node.getMetadata().getLabels().get("role");
			String workerPool = node.getMetadata().getLabels().get("worker-pool");
			if ("zdb".equals(role) && workerPool != null) {
				workerPools.add(node.getMetadata().getLabels().get("worker-pool"));
			}
			HashSet<String> distinctData = new HashSet<String>(workerPools);
			workerPools = new ArrayList<String>(distinctData);
		}
		return workerPools;
	}
	
	public String getServicePort(String namespace, String name) throws Exception {
		List<Service> serviceList = K8SUtil.getServices(namespace, name);
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
}
