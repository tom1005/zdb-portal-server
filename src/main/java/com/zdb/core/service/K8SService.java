package com.zdb.core.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.zdb.core.domain.DiskUsage;
import com.zdb.core.domain.MetaData;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.ZDBStatus;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.repository.TagRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
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
	
	@Autowired
	protected ZDBReleaseRepository releaseRepository;
	
	@Autowired
	protected TagRepository tagRepository;
	
	@Autowired
	protected DiskUsageRepository diskRepository;
	
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

		for (ReleaseMetaData release : releaseList) {
			if("DELETED".equals(release.getStatus()) || "DELETING".equals(release.getStatus())) {
				continue;
			}
			String svcType = release.getApp();
			if (apps.contains(serviceType) && serviceType.equals(svcType)) {
				ServiceOverview so = new ServiceOverview();

				so.setServiceName(release.getReleaseName());
				so.setNamespace(release.getNamespace());
				so.setServiceType(serviceType);
				so.setVersion(release.getAppVersion());
				so.setDeploymentStatus(release.getStatus());
				so.setPurpose(release.getPurpose());

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
		
		List<StatefulSet> statefulSets = new ArrayList<>();
		List<Pod> pods = new ArrayList<>();
		List<ReplicaSet> replicaSets = new ArrayList<>();
		for (HasMetadata obj : serviceOverviewMeta) {
			if (obj instanceof StatefulSet) {
				statefulSets.add((StatefulSet) obj);
			} else if (obj instanceof Pod) {
				pods.add((Pod) obj);
			} else if (obj instanceof ReplicaSet) {
				replicaSets.add((ReplicaSet) obj);
			}
		}
		so.getPods().addAll(pods);
		so.getStatefulSets().addAll(statefulSets);
		so.getReplicaSets().addAll(replicaSets);

		// 클러스터 사용 여부 
		so.setClusterEnabled(isClusterEnabled(so));
		
		// 태그 정보 

		List<Tag> tagList = tagRepository.findByNamespaceAndReleaseName(namespace, serviceName);
		so.getTags().addAll(tagList);
		
		if ("CREATING".equals(so.getDeploymentStatus())) {
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
				} else if (obj instanceof Service) {
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
		}
	}
	
	private int getClusterSlaveCount(ServiceOverview so) {
		try {
			if (ZDBType.MariaDB.name().toLowerCase().equals(so.getServiceType().toLowerCase())) {
				return so.getPods().size() - 1;
			} else if (ZDBType.Redis.name().toLowerCase().equals(so.getServiceType().toLowerCase())) {
				if (!so.getReplicaSets().isEmpty()) {
					return so.getReplicaSets().get(0).getStatus().getReplicas().intValue();
				}
			}
		} finally {
		}
		return 0;
	}
	
	private String getVersion(ServiceOverview so) {
		try {
			for (StatefulSet statefulSet : so.getStatefulSets()) {
				List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
				for (Container container : containers) {
					if ("mariadb".equals(container.getName())) {
						String image = container.getImage();

						String[] split = image.split(":");

						return split[1];
					}
				}
			}
		} catch (Exception e) {

		}

		return "unknown";
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
	
	private ZDBStatus getStatus(ServiceOverview so) {
		
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
				// zdb namespace label
				String name = labels.get("cloudzdb.io/zdb-system");
				if ("true".equals(name)) {
					list.add(pvc);
				}
			}
		}
		
		return list;
	
	}
	
}
