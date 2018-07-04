package com.zdb.core.event;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.zdb.core.domain.EventMetaData;
import com.zdb.core.domain.MetaData;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 * @param <T>
 */
@Slf4j
public class MetaDataWatcher<T> implements Watcher<T> {
	Object metaRepo;

	public MetaDataWatcher(Object metaRepo) {
		this.metaRepo = metaRepo;
	}
	
	@Override
	public void eventReceived(Action action, Object resource) {
		String metaToJon = new Gson().toJson(resource);
		HasMetadata metaObj = (HasMetadata) resource;
		
		if (resource instanceof Event) {
			Event event = (Event) resource;
			// EventRepository
			EventMetaData m = new EventMetaData();
			m = new EventMetaData();
			
			String firstTimestamp = event.getFirstTimestamp();
			firstTimestamp = firstTimestamp.replace("T", " ").replace("Z", "");
			
			String lastTimestamp = event.getLastTimestamp();
			lastTimestamp = lastTimestamp.replace("T", " ").replace("Z", "");
			
			m.setFirstTimestamp(firstTimestamp);
			m.setKind(event.getInvolvedObject().getKind());

			m.setLastTimestamp(lastTimestamp);
			m.setMessage(event.getMessage());
			m.setMetadata(metaToJon);
			m.setReason(event.getReason());

			m.setUid(event.getInvolvedObject().getUid());
			m.setName(event.getInvolvedObject().getName());
			m.setNamespace(event.getInvolvedObject().getNamespace());
			
			try {
				Namespace namespace = K8SUtil.getNamespace(event.getInvolvedObject().getNamespace());
				Map<String, String> labels = namespace.getMetadata().getLabels();
				if(labels != null) {
					// zdb namespace label
					String key = labels.get("cloudzdb.io/zdb-system");
					if(!"true".equals(key)) {
						return;
					}
				} else {
					log.debug(event.getInvolvedObject().getNamespace());
					return;
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}

//			log.info(m.getName()+" / "+m.getMessage()+" / "+m.getLastTimestamp());
			EventMetaData findByNameAndMessageAndLastTimestamp = ((EventRepository) metaRepo).findByNameAndMessageAndLastTimestamp(m.getName(), m.getMessage(), m.getLastTimestamp());
			if(findByNameAndMessageAndLastTimestamp == null) {

				// TODO 아래 메세지에 대해 어떻게 처리 할지 결정필요....
				if(event.getMessage().indexOf("Ensuring load balancer") > -1) {
					return;
				}
				if(event.getMessage().indexOf("Error on cloud load balancer") > -1) {
					return;
				}
				log.info(new Gson().toJson(m));
				((EventRepository) metaRepo).save(m);
			} 
		} else {
			if ("kube-system".equals(metaObj.getMetadata().getNamespace())) {
				return;
			}

			String releaseName = null;
			String app = null;
			Map<String, String> labels = metaObj.getMetadata().getLabels();
			if (labels != null) {
				releaseName = labels.get("release");
				app = labels.get("app");
			} else {
				if (!metaObj.getKind().equals("Namespace")) {
					return;
				}
			}

			//2018-06-19T13:12:54Z
			MetaData m = ((MetadataRepository) metaRepo).findNamespaceAndNameAndKind(metaObj.getMetadata().getNamespace(), metaObj.getMetadata().getName(), metaObj.getKind());
			if (m == null) {
				m = new MetaData();
				try {
					String ct = metaObj.getMetadata().getCreationTimestamp();
					ct = ct.replace("T", " ").replace("Z", "");
					
					m.setCreateTime(DateUtil.parseDate(ct));
					m.setKind(metaObj.getKind());
					m.setNamespace(metaObj.getMetadata().getNamespace());
					m.setName(metaObj.getMetadata().getName());
					m.setReleaseName(releaseName);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}

			m.setApp(app);
			m.setUid(metaObj.getMetadata().getUid());
			m.setStatus(getStatus(metaObj));
			m.setMetadata(metaToJon);
			m.setUpdateTime(new Date(System.currentTimeMillis()));
			m.setAction(action.name());

			if (isZDBResource(metaObj)) {
				((MetadataRepository) metaRepo).save(m);
			}
		}
		
		if (resource instanceof PersistentVolumeClaim) {
		} else if (resource instanceof Namespace) {
		} else if (resource instanceof Pod) {
		} else if (resource instanceof Deployment) {
		} else if (resource instanceof Service) {
		} else if (resource instanceof ConfigMap) {
		} else if (resource instanceof StatefulSet) {
		} else if (resource instanceof ReplicaSet) {
		} else if (resource instanceof Secret) {
		} else if (resource instanceof Event) {
		} else {
			log.warn("not support instance. [" + resource.getClass().getName() + "]");
		}
	}
	
	private boolean isZDBResource(HasMetadata resource) {
		if (resource instanceof Namespace) {
			Map<String, String> labels = resource.getMetadata().getLabels();
			if(labels != null) {
				// zdb namespace label
				String key = labels.get("cloudzdb.io/zdb-system");
				if("true".equals(key)) {
					return true;
				}
			}
		} else {
			try {
				String name = resource.getMetadata().getNamespace();
				Namespace namespace = K8SUtil.getNamespace(name);
				Map<String, String> labels = namespace.getMetadata().getLabels();
				if(labels != null) {
					// zdb namespace label
					String key = labels.get("cloudzdb.io/zdb-system");
					if("true".equals(key)) {
						return true;
					}
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
		
		return false;
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
	
	private boolean isReady(Pod pod) {
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
				isInitialized = Boolean.valueOf(condition.getStatus());
			}

			if ("Ready".equals(condition.getType())) {
				isReady = Boolean.valueOf(condition.getStatus());
			}

			if ("PodScheduled".equals(condition.getType())) {
				isPodScheduled = Boolean.valueOf(condition.getStatus());
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

		boolean isSuccess = false;
		if (isInitialized && isReady && isPodScheduled && isContainerReady) {
			isSuccess = true;
		} else {
			log.info("Name : {}, Initialized : {}, Ready : {}, PodScheduled : {}, isContainerReady : {}, reason : {}, message : {}", name, isInitialized, isReady, isPodScheduled, isContainerReady, reason, message);
		}

		return isSuccess;
	}

	@Override
	public void onClose(KubernetesClientException cause) {
	}

}