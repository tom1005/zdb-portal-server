package com.zdb.core.job;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.PodManager;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StartServiceJob extends JobAdapter {

	public StartServiceJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		return "Start service";
	}

	@Override
	public JobKind getKind() {
		return JobKind.START_POD;
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String stsName = param.getStatefulsetName();
		String targetPvc = param.getTargetPvc();
		
		try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();){
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = 
					kubernetesClient.inNamespace(namespace).apps().statefulSets();
			
			StatefulSet statefulSet = statefulSets.withName(stsName).get();
			
			Volume dataVolume = new Volume();
			dataVolume.setName("data");
			dataVolume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(targetPvc, null));
			
			List<Volume> volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
			for (Iterator<Volume> iterator = volumes.iterator(); iterator.hasNext();) {
				Volume v = iterator.next();
				
				if(!v.getName().equals("config")) {
					iterator.remove();
				}
			}
			
			int mariadbContaineerIndex = -1;
			List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
			for (Container container : containers) {
				if(container.getName().equals("mariadb")) {
					mariadbContaineerIndex++;
					break;
				}
			}
			
			List<VolumeMount> volumeMounts = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(mariadbContaineerIndex).getVolumeMounts();
			for (Iterator<VolumeMount> iterator = volumeMounts.iterator(); iterator.hasNext();) {
				VolumeMount v = iterator.next();
				
				if(v.getName().equals("config") || v.getName().equals("data")) {
					continue;
				}
				iterator.remove();
			}
			
			if (statefulSet.getSpec().getVolumeClaimTemplates().size() > 0) {
				statefulSet.getSpec().getVolumeClaimTemplates().remove(0);
			}
			
			StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
			
			StatefulSet newSts = stsBuilder
					.editSpec().withReplicas(1).endSpec()
					.editSpec().editTemplate().editSpec().addToVolumes(dataVolume).endSpec().endTemplate().endSpec()
					.editMetadata().withResourceVersion(null).withUid(null).withCreationTimestamp(null).endMetadata()
					
					.build();
			
			statefulSets.withName(statefulSet.getMetadata().getName()).delete();
//			statefulSets.createOrReplace(newSts);
			
			final CountDownLatch latch = new CountDownLatch(1);
			
			Watcher<Pod> watcher = new Watcher<Pod>() {
				
				@Override
				public void eventReceived(Action action, Pod resource) {
					try {
						String podName = resource.getMetadata().getName();
						
						if (action == Action.MODIFIED) {
							String stsRelease = statefulSet.getMetadata().getLabels().get("release");
							if (resource.getMetadata().getLabels() != null) {
								String podRelease = resource.getMetadata().getLabels().get("release");

								if (podName.startsWith(stsName) && stsRelease.equals(podRelease) && PodManager.isReady(resource)) {
									progress(resource.getMetadata().getName() + " started.");
									latch.countDown();
								}
							}
						}

					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
				
				@Override
				public void onClose(KubernetesClientException cause) {
					if(cause != null) {
						log.error(cause.getMessage(), cause);
					} else {
						log.error("StartServiceJob closed...........");
					}
				}
				
			};
			Watch watch = kubernetesClient.pods().inNamespace(namespace).watch(watcher);
			statefulSets.createOrReplace(newSts);
			
			latch.await();
			watch.close();
			
			done(JobResult.OK, stsName + " 스토리지 (" + targetPvc+ ") 마운트 및 서비스 시작 완료", null);
			
		} catch (Exception e) {
			done(JobResult.ERROR, "", e);
		}
		
	}


}
