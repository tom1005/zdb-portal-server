package com.zdb.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

public class WatcherTest {

	static List<Watch> watchList = new CopyOnWriteArrayList<>();
	
	public static void main(String[] args) {
		
		
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				Watch eventsWatcher;
				try {
					eventsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().events().watch(new Watcher<Event>() {

						@Override
						public void eventReceived(Action action, Event resource) {
							
						}

						@Override
						public void onClose(KubernetesClientException cause) {
							
						}
						
					});
					watchList.add(eventsWatcher);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
	
}
