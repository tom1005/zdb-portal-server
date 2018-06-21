package com.zdb.core.event.listener;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.zdb.core.event.MetaDataWatcher;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.util.K8SUtil;

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
import io.fabric8.kubernetes.client.Watch;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WatchEventListener {

	List<Watch> watchList = new ArrayList<>();

	@Autowired
	MetadataRepository metaRepo;

	@Autowired
	EventRepository eventRepo;

	@EventListener
	public void handleEvent(Object event) {

		if (event instanceof ApplicationReadyEvent) {

			try {

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch eventsWatcher;
						try {
							eventsWatcher = K8SUtil.kubernetesClient().events().watch(new MetaDataWatcher<Event>(eventRepo));
							watchList.add(eventsWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch statefulSetsWatcher;
						try {
							statefulSetsWatcher = K8SUtil.kubernetesClient().apps().statefulSets().watch(new MetaDataWatcher<StatefulSet>(metaRepo));
							watchList.add(statefulSetsWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}

					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch configMapWatcher;
						try {
							configMapWatcher = K8SUtil.kubernetesClient().configMaps().watch(new MetaDataWatcher<ConfigMap>(metaRepo));
							watchList.add(configMapWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch persistentVolumeClaimsWatcher;
						try {
							persistentVolumeClaimsWatcher = K8SUtil.kubernetesClient().persistentVolumeClaims().watch(new MetaDataWatcher<PersistentVolumeClaim>(metaRepo));
							watchList.add(persistentVolumeClaimsWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch servicesWatcher;
						try {
							servicesWatcher = K8SUtil.kubernetesClient().services().watch(new MetaDataWatcher<Service>(metaRepo));
							watchList.add(servicesWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch namesapcesWatcher;
						try {
							namesapcesWatcher = K8SUtil.kubernetesClient().namespaces().watch(new MetaDataWatcher<Namespace>(metaRepo));
							watchList.add(namesapcesWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch deploymentsWatcher;
						try {
							deploymentsWatcher = K8SUtil.kubernetesClient().extensions().deployments().watch(new MetaDataWatcher<Deployment>(metaRepo));
							watchList.add(deploymentsWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch podsWatcher;
						try {
							podsWatcher = K8SUtil.kubernetesClient().pods().watch(new MetaDataWatcher<Pod>(metaRepo));
							watchList.add(podsWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch replicaSetsWatcher;
						try {
							replicaSetsWatcher = K8SUtil.kubernetesClient().extensions().replicaSets().watch(new MetaDataWatcher<ReplicaSet>(metaRepo));
							watchList.add(replicaSetsWatcher);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

				log.info("================================Add Watch end=======================================");
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}

		} else if (event instanceof ContextClosedEvent) {
			for (Watch watch : watchList) {
				watch.close();
			}
		}
	}
}