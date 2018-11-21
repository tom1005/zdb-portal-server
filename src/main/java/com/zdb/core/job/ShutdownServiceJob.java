package com.zdb.core.job;

import java.util.concurrent.CountDownLatch;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
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
public class ShutdownServiceJob extends JobAdapter {

	public ShutdownServiceJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		return "Shutdown service";
	}

	@Override
	public JobKind getKind() {
		return JobKind.SHUTDOWN_POD;
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String stsName = param.getStatefulsetName();
		
		try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();){
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = kubernetesClient.inNamespace(namespace).apps().statefulSets();
			StatefulSet statefulSet = statefulSets.withName(stsName).get();
			
			StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
			
			StatefulSet newSts = stsBuilder
					.editSpec().withReplicas(0).endSpec()
					.build();
			
			final CountDownLatch latch = new CountDownLatch(1);
			
			Watcher<Pod> watcher = new Watcher<Pod>() {
				
				@Override
				public void eventReceived(Action action, Pod resource) {
					try {
						String podName = resource.getMetadata().getName();
						
						if(action == Action.DELETED) {
							String podRelease = resource.getMetadata().getLabels().get("release");			
							String stsRelease = statefulSet.getMetadata().getLabels().get("release");
							
							if(podName.startsWith(stsName) && stsRelease.equals(podRelease)) {
								progress(podName + " | " + action);
								latch.countDown();
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
						log.error("ShutdownServiceJob closed...........");
					}
				}
				
			};
			Watch watch = kubernetesClient.pods().inNamespace(namespace).watch(watcher);
			statefulSets.createOrReplace(newSts);
			
			latch.await();
			watch.close();
			
			done(JobResult.OK, "서비스를 종료 합니다. (" + stsName +")", null);
			
		} catch (Exception e) {
			done(JobResult.ERROR, "", e);
		}
		
	}

	public static void main(String[] args) {
//		ShutdownServiceJob job = new ShutdownServiceJob("zdb-test2","zdb-test2-ns-mariadb");
//		job.run();
	}

}
