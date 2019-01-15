package com.zdb.core.event.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.util.CopyOnWriteMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zdb.core.event.EventWatcher;
import com.zdb.core.event.MetaDataWatcher;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.service.ZDBRestService;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.ws.MessageSender;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.Watch;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
//@Profile({"prod"})
public class WatchEventListener {

	List<Watch> watchList = Collections.synchronizedList(new ArrayList());
	List<EventWatcher<?>> eventWatcherList = Collections.synchronizedList(new ArrayList());
	Map<String, MetaDataWatcher<?>> metaDataWatcherMap = Collections.synchronizedMap(new HashMap());

	@Autowired
	MetadataRepository metaRepo;

	@Autowired
	EventRepository eventRepo;
	
	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private MessageSender messageSender;
	
	@Autowired
	@Qualifier("commonService")
	private ZDBRestService commonService;
	
	private boolean isShutdown;
	
	@Scheduled(initialDelayString = "10000", fixedRateString = "3000")
	public void watcherHealthCheck() {
		if(isShutdown) {
			return;
		}
		log.debug(">>>>>>>>>>>>> watcherHealthCheck <<<<<<<<<<<<<<<<<");
		
		for (Iterator<EventWatcher<?>> iterator = eventWatcherList.iterator(); iterator.hasNext();) {
			EventWatcher<?> eventWatcher = iterator.next();
			
			if(eventWatcher.isClosed()) {
				iterator.remove();
				
				sleep(500);
				log.warn("EventWatcher 재시작...");
				runEventWatcher();
			}
		}
		
		for (Iterator<String> iterator = metaDataWatcherMap.keySet().iterator(); iterator.hasNext();) {
			String key = iterator.next();
			MetaDataWatcher<?> metadataWatcher = metaDataWatcherMap.get(key);
			
			if(metadataWatcher.isClosed()) {
 				iterator.remove();
				
				sleep(500);
				log.warn(key +" MetaDataWatcher 재시작...");
				if("StatefulSet".equals(key)) {
					runStatefulSetMetaDataWatcher();
				} else if("ConfigMap".equals(key)) {
					runConfigMapMetaDataWatcher();
				} else if("PersistentVolumeClaim".equals(key)) {
					runPersistentVolumeClaimMetaDataWatcher();
				} else if("Service".equals(key)) {
					runServiceMetaDataWatcher();
				} else if("Namespace".equals(key)) {
					runNamespaceMetaDataWatcher();
				} else if("Deployment".equals(key)) {
					runDeploymentMetaDataWatcher();
				} else if("Pod".equals(key)) {
					runPodMetaDataWatcher();
				} else if("ReplicaSet".equals(key)) {
					runReplicaSetMetaDataWatcher();
				}
			}
		}
	}
	
	public void sleep(int s) {
		try {
			Thread.sleep(s);
		} catch (Exception e) {
		}
	}

	@EventListener
	public void handleEvent(Object event) {

		if (event instanceof ApplicationReadyEvent) {
			isShutdown = false;
			try {
				log.info("================================Add Event Watch=======================================");
				runEventWatcher();

				log.info("================================Add StatefulSet Watch=======================================");
				runStatefulSetMetaDataWatcher();

				log.info("================================Add ConfigMap Watch=======================================");
				runConfigMapMetaDataWatcher();

				log.info("================================Add PersistentVolumeClaim Watch=======================================");
				runPersistentVolumeClaimMetaDataWatcher();

				log.info("================================Add Service Watch=======================================");
				runServiceMetaDataWatcher();

				log.info("================================Add Namespace Watch=======================================");
				runNamespaceMetaDataWatcher();

				log.info("================================Add Deployment Watch=======================================");
				runDeploymentMetaDataWatcher();

				log.info("================================Add Pod Watch=======================================");
				runPodMetaDataWatcher();

				log.info("================================Add ReplicaSet Watch=======================================");
				runReplicaSetMetaDataWatcher();

				log.info("================================Add ReplicaSet Watch=======================================");
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}

		} else if (event instanceof ContextClosedEvent) {
			isShutdown = true;
			for (Watch watch : watchList) {
				watch.close();
			}
		}
	}

	private void runReplicaSetMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch replicaSetsWatcher;
				try {
					MetaDataWatcher<ReplicaSet> watcher = new MetaDataWatcher<ReplicaSet>(metaRepo);
					metaDataWatcherMap.put("ReplicaSet", watcher);
					
					replicaSetsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().extensions().replicaSets().watch(watcher);
					watchList.add(replicaSetsWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}

	private void runPodMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch podsWatcher;
				try {
					MetaDataWatcher<Pod> watcher = new MetaDataWatcher<Pod>(metaRepo) {
						protected void sendWebSocket() {
							messageSender.sendToClient("pods");
						}
					};
					metaDataWatcherMap.put("Pod", watcher);
					
					podsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().pods().watch(watcher);
					watchList.add(podsWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}

	private void runDeploymentMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch deploymentsWatcher;
				try {
					MetaDataWatcher<Deployment> watcher = new MetaDataWatcher<Deployment>(metaRepo);
					metaDataWatcherMap.put("Deployment", watcher);
					
					deploymentsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().extensions().deployments().watch(watcher);
					watchList.add(deploymentsWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}

	private void runNamespaceMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch namesapcesWatcher;
				try {
					MetaDataWatcher<Namespace> watcher = new MetaDataWatcher<Namespace>(metaRepo);
					metaDataWatcherMap.put("Namespace", watcher);
					
					namesapcesWatcher = K8SUtil.kubernetesClient().inAnyNamespace().namespaces().watch(watcher);
					watchList.add(namesapcesWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}

	private void runServiceMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch servicesWatcher;
				try {
					MetaDataWatcher<Service> watcher = new MetaDataWatcher<Service>(metaRepo);
					metaDataWatcherMap.put("Service", watcher);
					
					servicesWatcher = K8SUtil.kubernetesClient().inAnyNamespace().services().watch(watcher);
					watchList.add(servicesWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}

	private void runPersistentVolumeClaimMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch persistentVolumeClaimsWatcher;
				try {
					MetaDataWatcher<PersistentVolumeClaim> watcher = new MetaDataWatcher<PersistentVolumeClaim>(metaRepo) {
						protected void sendWebSocket() {
							messageSender.sendToClient("persistentVolumeClaims");
						}
					};
					metaDataWatcherMap.put("PersistentVolumeClaim", watcher);
					
					persistentVolumeClaimsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().persistentVolumeClaims().watch(watcher);
					watchList.add(persistentVolumeClaimsWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}

	private void runConfigMapMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch configMapWatcher;
				try {
					MetaDataWatcher<ConfigMap> watcher = new MetaDataWatcher<ConfigMap>(metaRepo);
					metaDataWatcherMap.put("ConfigMap", watcher);
					
					configMapWatcher = K8SUtil.kubernetesClient().inAnyNamespace().configMaps().watch(watcher);
					watchList.add(configMapWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}

	private void runStatefulSetMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch statefulSetsWatcher;
				try {
					MetaDataWatcher<StatefulSet> metadataWatcher = new MetaDataWatcher<StatefulSet>(metaRepo);
					metaDataWatcherMap.put("StatefulSet", metadataWatcher);
					
					statefulSetsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().apps().statefulSets().watch(metadataWatcher);
					watchList.add(statefulSetsWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}

			}
		}).start();
	}

	private void runEventWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				Watch eventsWatcher;
				try {
					EventWatcher<Event> eventWatcher = new EventWatcher<Event>(eventRepo, metaRepo, messagingTemplate) {
						protected void sendWebSocket() {
							messageSender.sendToClient("events");
						}
					};
					eventWatcherList.add(eventWatcher);
					
					eventsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().events().watch(eventWatcher);
					watchList.add(eventsWatcher);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}).start();
	}
}
