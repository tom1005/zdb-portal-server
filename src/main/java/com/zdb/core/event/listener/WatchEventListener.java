package com.zdb.core.event.listener;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.event.EventWatcher;
import com.zdb.core.event.MetaDataWatcher;
import com.zdb.core.repository.EventRepository;
import com.zdb.core.repository.MetadataRepository;
import com.zdb.core.service.ZDBRestService;
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
	
	@Autowired
	private SimpMessagingTemplate messageSender;
	
	@Autowired
	@Qualifier("commonService")
	private ZDBRestService commonService;

	@EventListener
	public void handleEvent(Object event) {

		if (event instanceof ApplicationReadyEvent) {

			try {

				new Thread(new Runnable() {

					@Override
					public void run() {
						Watch eventsWatcher;
						try {
							eventsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().events().watch(new EventWatcher<Event>(eventRepo, metaRepo, messageSender) {
								protected void sendWebSocket() {
									try {
										Result result = commonService.getServicesWithNamespaces(null, true);
										if(result.isOK()) {
											Object object = result.getResult().get(IResult.SERVICEOVERVIEWS);
											if(object != null) {
												messageSender.convertAndSend("/services", result);
												
												List<ServiceOverview> overviews = (List<ServiceOverview>) object;
												for (ServiceOverview serviceOverview : overviews) {
													Result r = result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
													messageSender.convertAndSend("/service/"+serviceOverview.getServiceName(), r);
												}
											}
										}
//										
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							});
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
							statefulSetsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().apps().statefulSets().watch(new MetaDataWatcher<StatefulSet>(metaRepo));
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
							configMapWatcher = K8SUtil.kubernetesClient().inAnyNamespace().configMaps().watch(new MetaDataWatcher<ConfigMap>(metaRepo));
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
							persistentVolumeClaimsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().persistentVolumeClaims().watch(new MetaDataWatcher<PersistentVolumeClaim>(metaRepo) {
								protected void sendWebSocket() {
									try {
										Result result = commonService.getServicesWithNamespaces(null, true);
										if(result.isOK()) {
											Object object = result.getResult().get(IResult.SERVICEOVERVIEWS);
											if(object != null) {
												messageSender.convertAndSend("/services", result);
												
												List<ServiceOverview> overviews = (List<ServiceOverview>) object;
												for (ServiceOverview serviceOverview : overviews) {
													Result r = result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
													messageSender.convertAndSend("/service/"+serviceOverview.getServiceName(), r);
												}
											}
										}
//										
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							});
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
							servicesWatcher = K8SUtil.kubernetesClient().inAnyNamespace().services().watch(new MetaDataWatcher<Service>(metaRepo));
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
							namesapcesWatcher = K8SUtil.kubernetesClient().inAnyNamespace().namespaces().watch(new MetaDataWatcher<Namespace>(metaRepo));
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
							deploymentsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().extensions().deployments().watch(new MetaDataWatcher<Deployment>(metaRepo));
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
							podsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().pods().watch(new MetaDataWatcher<Pod>(metaRepo) {
								protected void sendWebSocket() {
									try {
										Result result = commonService.getServicesWithNamespaces(null, true);
										if(result.isOK()) {
											Object object = result.getResult().get(IResult.SERVICEOVERVIEWS);
											if(object != null) {
												messageSender.convertAndSend("/services", result);
												
												List<ServiceOverview> overviews = (List<ServiceOverview>) object;
												for (ServiceOverview serviceOverview : overviews) {
													Result r = result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
													messageSender.convertAndSend("/service/"+serviceOverview.getServiceName(), r);
												}
											}
										}
//										
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							});
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
							replicaSetsWatcher = K8SUtil.kubernetesClient().inAnyNamespace().extensions().replicaSets().watch(new MetaDataWatcher<ReplicaSet>(metaRepo));
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