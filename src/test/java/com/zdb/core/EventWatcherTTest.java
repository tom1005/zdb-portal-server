package com.zdb.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zdb.core.event.MetaDataWatcher;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.Watcher.Action;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventWatcherTTest {

	static List<Watch> watchList = Collections.synchronizedList(new ArrayList());
	static List<EventWatcherT<?>> eventWatcherList = Collections.synchronizedList(new ArrayList());
	static Map<String, MetaDataWatcher<?>> metaDataWatcherMap = Collections.synchronizedMap(new HashMap());

	public static void main(String[] args) {

		try {
			event();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public static void event() throws InterruptedException {
		new Thread(new Runnable() {
			
			 @Override
			 public void run() {
				 long s = System.currentTimeMillis();
				 while(true) 
				 {
					 
					 final CountDownLatch closeLatch = new CountDownLatch(1);

						try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
							
							try (Watch watch = client.inAnyNamespace().events().watch(new EventWatcherT<Event>() {
								protected void sendWebSocket() {
									// messageSender.sendToClient("events");
								}

								@Override
								public void onClose(KubernetesClientException e) {
									super.onClose(e);
									if (e != null) {
										log.error(e.getMessage(), e);
										closeLatch.countDown();
									}
								}
							})) {
								log.info("1await-----------------------------");
								closeLatch.await(60, TimeUnit.SECONDS);
								log.info("2await-----------------------------");
							} catch (KubernetesClientException | InterruptedException e) {
								log.error("Could not watch resources", e);
							}
						} catch (Exception e) {
							e.printStackTrace();
							log.error(e.getMessage(), e);

							Throwable[] suppressed = e.getSuppressed();
							if (suppressed != null) {
								for (Throwable t : suppressed) {
									log.error(t.getMessage(), t);
								}
							}
						}
					 
					 
					 if((System.currentTimeMillis() - s) > 1000*1000) {
						 break;
					 }
				 }
				 
			 }
			 }).start();
		
		
	}

	public static void runEventWatcher() {
		 new Thread(new Runnable() {
		
		 @Override
		 public void run() {}
		 }).start();

		CountDownLatch closeLatch = new CountDownLatch(1);

		Watch eventsWatcher;
		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

			EventWatcherT<Event> eventWatcher = new EventWatcherT<Event>() {
				protected void sendWebSocket() {
					// messageSender.sendToClient("events");
				}

				@Override
				public void onClose(KubernetesClientException e) {
					super.onClose(e);
					if (e != null) {
						log.error(e.getMessage(), e);
						closeLatch.countDown();
					}
				}
			};
			eventWatcherList.add(eventWatcher);

			eventsWatcher = client.inAnyNamespace().events().watch(eventWatcher);
			watchList.add(eventsWatcher);

			// closeLatch.await(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

	}
}
