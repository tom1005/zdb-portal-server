package com.zdb.core.event.listener;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Profile({"prod"})
public class WatchEventListener {

	Map<String, CountDownLatch> watcherCountDownLatch = Collections.synchronizedMap(new HashMap<>());

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
	
	@EventListener
	public void handleEvent(Object event) {

		if (event instanceof ApplicationReadyEvent) {
			isShutdown = false;
			try {

				log.info("================================Add Event Watch============================================");
				runEventWatcher();

				log.info("================================Add StatefulSet Watch======================================");
				runStatefulSetMetaDataWatcher();

				log.info("================================Add ConfigMap Watch========================================");
				runConfigMapMetaDataWatcher();

				log.info("================================Add PersistentVolumeClaim Watch============================");
				runPersistentVolumeClaimMetaDataWatcher();

				log.info("================================Add Service Watch==========================================");
				runServiceMetaDataWatcher();

				log.info("================================Add Namespace Watch========================================");
				runNamespaceMetaDataWatcher();

				log.info("================================Add Deployment Watch=======================================");
				runDeploymentMetaDataWatcher();

				log.info("================================Add Pod Watch==============================================");
				runPodMetaDataWatcher();

				log.info("================================Add ReplicaSet Watch=======================================");
				runReplicaSetMetaDataWatcher();

			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}

		} else if (event instanceof ContextClosedEvent) {
			isShutdown = true;
			countDown();
		}
	}
	
	public void countDown() {
		for (Iterator<String> iterator = watcherCountDownLatch.keySet().iterator(); iterator.hasNext();) {
			String key = iterator.next();
			CountDownLatch latch = watcherCountDownLatch.get(key);

			try {
				latch.countDown();
			} catch (Exception e) {
			}
		}
	}

	private void runReplicaSetMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("ReplicaSet", closeLatch);

					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().extensions().replicaSets().watch(new MetaDataWatcher<ReplicaSet>(metaRepo) {
							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 60, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("ReplicaSet MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
			
			}
		}).start();
	}

	private void runPodMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("Pod", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().pods().watch(new MetaDataWatcher<Pod>(metaRepo) {
							protected void sendWebSocket() {
								messageSender.sendToClient("pods");
							}
							
							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 10, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("Pods MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
			}
		}).start();
	}

	private void runDeploymentMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("Deployment", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().extensions().deployments().watch(new MetaDataWatcher<Deployment>(metaRepo) {
							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 10, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("Deployments MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
			}
		}).start();
	}

	private void runNamespaceMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("Namespace", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().namespaces().watch(new MetaDataWatcher<Namespace>(metaRepo) {
							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 60, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("Namespaces MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
			}
		}).start();
	}

	private void runServiceMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("Service", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().services().watch(new MetaDataWatcher<Service>(metaRepo) {
							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 10, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("Services MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}

			}
		}).start();
	}

	private void runPersistentVolumeClaimMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("PersistentVolumeClaim", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().persistentVolumeClaims().watch(new MetaDataWatcher<PersistentVolumeClaim>(metaRepo) {
							protected void sendWebSocket() {
								messageSender.sendToClient("persistentVolumeClaims");
							}

							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 10, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("PersistentVolumeClaims MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
			}
		}).start();
	}

	private void runConfigMapMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("ConfigMap", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().configMaps().watch(new MetaDataWatcher<ConfigMap>(metaRepo) {
							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 60, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("ConfigMaps MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
			}
					
		}).start();
	}

	private void runStatefulSetMetaDataWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("StatefulSet", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().apps().statefulSets().watch(new MetaDataWatcher<StatefulSet>(metaRepo) {
							protected void sendWebSocket() {
								messageSender.sendToClient("statefulSets");
							}
							
							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 10, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("StatefulSets MetaDataWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
							
			}
		}).start();
	}

	private void runEventWatcher() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {

					final CountDownLatch closeLatch = new CountDownLatch(1);
					watcherCountDownLatch.put("Event", closeLatch);
					try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

						try (Watch watch = client.inAnyNamespace().events().watch(new EventWatcher<Event>(eventRepo, metaRepo, messagingTemplate) {
							protected void sendWebSocket() {
								messageSender.sendToClient("events");
							}

							@Override
							public void onClose(KubernetesClientException e) {
								if (e != null) {
									closeLatch.countDown();
								}
								super.onClose(e);
							}
						})) {
							closeLatch.await(60 * 30, TimeUnit.SECONDS);
						} catch (KubernetesClientException | InterruptedException e) {
							log.error("EventWatcher - Could not watch resources", e);
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);

						Throwable[] suppressed = e.getSuppressed();
						if (suppressed != null) {
							for (Throwable t : suppressed) {
								log.error(t.getMessage(), t);
							}
						}
					}

					if (isShutdown) {
						break;
					}
				}
			}
		}).start();
	}
}
