package com.zdb.core;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

public class WatchExample3 {

	private static final Logger logger = LoggerFactory.getLogger(WatchExample3.class);

	private static String pvcName = "maria-pvc-004";

	public static void main(String[] args) throws Exception {

		CountDownLatch closeLatch = new CountDownLatch(1);

		DefaultKubernetesClient client = K8SUtil.kubernetesClient();

		client.persistentVolumeClaims().inAnyNamespace().watch(new Watcher<PersistentVolumeClaim>() {

			@Override
			public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, PersistentVolumeClaim resource) {
				System.out.println("PersistentVolumeClaim eventReceived >>>>> "+action + " > " + resource);
			}

			@Override
			public void onClose(KubernetesClientException cause) {
				if (cause != null) {
					// log? throw?
					System.out.println("PersistentVolumeClaim eventReceived >>>>> "+cause);
				}
			}
			
		});
		
		client.services().inAnyNamespace().watch(new Watcher<Service>() {

			@Override
			public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Service resource) {
				System.out.println("Service eventReceived >>>>> "+action + " > " + resource);
			}

			@Override
			public void onClose(KubernetesClientException cause) {
				if (cause != null) {
					// log? throw?
					System.out.println("Service eventReceived >>>>> "+cause);
				}
			}
			
		});
		
		client.extensions().deployments().inAnyNamespace().watch(new Watcher<Deployment>() {

			@Override
			public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Deployment resource) {
				System.out.println("Deployment eventReceived >>>>> "+action + " > " + resource);
			}

			@Override
			public void onClose(KubernetesClientException cause) {
				if (cause != null) {
					// log? throw?
					System.out.println("Deployment eventReceived >>>>> "+cause);
				}
			}
			
		});
		
		client.pods().inAnyNamespace().watch(new Watcher<Pod>() {

			@Override
			public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Pod resource) {
				System.out.println("Pod eventReceived >>>>> "+action + " > " + resource);
			}

			@Override
			public void onClose(KubernetesClientException cause) {
				if (cause != null) {
					// log? throw?
					System.out.println("Pod eventReceived >>>>> "+cause);
				}
			}
			
		});
		
		client.extensions().replicaSets().inAnyNamespace().watch(new Watcher<ReplicaSet>() {

			@Override
			public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, ReplicaSet resource) {
				System.out.println("ReplicaSet eventReceived >>>>> "+action + " > " + resource);
			}

			@Override
			public void onClose(KubernetesClientException cause) {
				if (cause != null) {
					// log? throw?
					System.out.println("ReplicaSet eventReceived >>>>> "+cause);
				}
			}
			
		});
		
		client.events().inAnyNamespace().watch(new Watcher<Event>() {

			@Override
			public void onClose(KubernetesClientException cause) {
				if (cause != null) {
					// log? throw?
					System.out.println("Event onClose >>>>> "+cause);
				}

			}

			@Override
			public void eventReceived(Action action, Event resource) {
				System.out.println("Event eventReceived >>>>> "+action + " > " + resource);

			}
		});
		
		closeLatch.await();

	}

}