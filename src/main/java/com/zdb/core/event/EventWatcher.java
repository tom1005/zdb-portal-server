package com.zdb.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.google.gson.Gson;
import com.zdb.core.collector.MetaDataCollector;
import com.zdb.core.domain.CommonConstants;
import com.zdb.core.domain.EventMetaData;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.util.EventLog;
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
public class EventWatcher<T> implements Watcher<T> {
	
	private SimpMessagingTemplate messageSender;
	
	EventRepository eventRepo;
	
	MetadataRepository metaRepo;
	
	boolean isClosed = false;

	public EventWatcher(EventRepository eventRepo, MetadataRepository metaRepo, SimpMessagingTemplate messageSender) {
		this.eventRepo = eventRepo;
		this.metaRepo = metaRepo;
		this.messageSender = messageSender;
	}
	
	protected void sendWebSocket() {
		
	}
	
	protected void uptime() {
		
	}
	
	public boolean isClosed() {
		return isClosed;
	}
	
	@Override
	public void eventReceived(Action action, Object resource) {
		uptime();
		
		isClosed = false;
		
		String metaToJon = new Gson().toJson(resource);
		
		if (resource instanceof Event) {
			Event event = (Event) resource;
			// EventRepository
			EventMetaData m = new EventMetaData();
			
			// write event log 
			EventLog.printLog(event);
			
			try {
				if( "PersistentVolume".equals(event.getInvolvedObject().getKind())) {
					return;
				}
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
				
				String _kind = event.getInvolvedObject().getKind();
//				String _namespace = event.getInvolvedObject().getNamespace();
				String _name = event.getInvolvedObject().getName();
				
				String _uid = String.format("%s-%s", _kind, _name);
	
				m.setUid(_uid);
//				m.setUid(event.getInvolvedObject().getUid());
				m.setName(event.getInvolvedObject().getName());
				String ns = event.getInvolvedObject().getNamespace();
				
				if(ns == null) {
					ns = event.getInvolvedObject().getName();
				} else {
					m.setNamespace(ns);
				}
				
				Namespace namespace = K8SUtil.getNamespace(ns);
				if(namespace == null) {
					return;
				}
				Map<String, String> labels = namespace.getMetadata().getLabels();
				if(labels != null) {
					// zdb namespace label
					String key = labels.get(CommonConstants.ZDB_LABEL);
					if(!"true".equals(key)) {
						return;
					}
				} else {
					return;
				}
//				System.err.println(event.getReason() +" " +  event.getInvolvedObject().getKind() +" " +  event.getInvolvedObject().getName());
				if(EVENT_KEYWORD.contains(event.getReason())) {
					sendWebSocket();
					try {
						if (event.getInvolvedObject().getKind().equals("Pod")) {
							Pod pod = K8SUtil.getPodWithName(ns, event.getInvolvedObject().getName());
							if (pod != null) {
								String _pkind = pod.getKind();
								String _pname = pod.getMetadata().getName();
								
								String uid = String.format("%s-%s", _pkind, _pname);
								
//								MetaDataCollector.putMetaData(pod.getMetadata().getUid(), pod);
								MetaDataCollector.putMetaData(uid, pod);
							}
						} else if (event.getInvolvedObject().getKind().equals("PersistentVolumeClaim")) {
							PersistentVolumeClaim pvc = K8SUtil.getPersistentVolumeClaim(ns, event.getInvolvedObject().getName());
							if (pvc != null) {
								String _pkind = pvc.getKind();
								String _pname = pvc.getMetadata().getName();
								
								String uid = String.format("%s-%s", _pkind, _pname);
//								MetaDataCollector.putMetaData(pvc.getMetadata().getUid(), pvc);
								MetaDataCollector.putMetaData(uid, pvc);
							}
						}
					} finally {
					}
				}
				
				
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			EventMetaData findByNameAndMessageAndLastTimestamp = eventRepo.findByNameAndMessageAndLastTimestamp(m.getName(), m.getMessage(), m.getLastTimestamp());
			if(findByNameAndMessageAndLastTimestamp == null) {

				// TODO 아래 메세지에 대해 어떻게 처리 할지 결정필요....
				if(event.getMessage().indexOf("Ensuring load balancer") > -1) {
					return;
				}
				if(event.getMessage().indexOf("Error on cloud load balancer") > -1) {
					return;
				}
				eventRepo.save(m);
			} 
		} 
	}
	
	static List<String> EVENT_KEYWORD = new ArrayList<>();
	static {
		EVENT_KEYWORD.add("Provisioning");
		EVENT_KEYWORD.add("ExternalProvisioning");
		EVENT_KEYWORD.add("ProvisioningSucceeded");
		EVENT_KEYWORD.add("FailedScheduling");
		EVENT_KEYWORD.add("SuccessfulMountVolume");
		EVENT_KEYWORD.add("Unhealthy");
		EVENT_KEYWORD.add("SuccessfulCreate");
		EVENT_KEYWORD.add("Started");
		EVENT_KEYWORD.add("Created");
		EVENT_KEYWORD.add("Killing");
//		EVENT_KEYWORD.add("Pulled");
//		EVENT_KEYWORD.add("Scheduled");
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
		isClosed = true;
		
		if(cause != null) {
			log.error(cause.getMessage(), cause);
		} else {
			log.info("EventWatcher closed...........");
		}
	}

}