package com.zdb.core.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
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
public class ResourceScaleJob extends JobAdapter {

	public ResourceScaleJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		return "Resource Scale Up/Down service";
	}

	@Override
	public JobKind getKind() {
		return JobKind.SCALE_UP_DOWN;
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String releaseName = param.getServiceName();
		String cpu = param.getCpu();
		String memory = param.getMemory();
		
		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();){
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = client.inNamespace(namespace).apps().statefulSets();
			
			List<StatefulSet> items = client.inNamespace(namespace).apps().statefulSets().withLabel("release", releaseName).list().getItems();
			
			Watcher<StatefulSet> watcher = new Watcher<StatefulSet>() {
				
				@Override
				public void eventReceived(Action action, StatefulSet resource) {
					try {
						String stsName = resource.getMetadata().getName();
						
//						System.out.println("StS_Watcher > "+action + " / " +stsName+ " / " +resource.getStatus().getReadyReplicas());
						
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
				
				@Override
				public void onClose(KubernetesClientException cause) {
					
				}
				
			};
			Watch stsWatch = client.inNamespace(namespace).apps().statefulSets().watch(watcher);
			
			Watcher<Pod> podWatcher = new Watcher<Pod>() {
				
				@Override
				public void eventReceived(Action action, Pod resource) {
					try {
						String podName = resource.getMetadata().getName();
						
//						System.out.println("Pod_Watcher > "+action + " / " +podName + " / " +resource.getStatus().getPhase()+ " / " +PodManager.isReady(resource));
						
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
				
				@Override
				public void onClose(KubernetesClientException cause) {
					
				}
				
			};
			Watch podWatch = client.inNamespace(namespace).pods().watch(podWatcher);
			
			for (StatefulSet sts : items) {
				
				StatefulSetBuilder stsBuilder = new StatefulSetBuilder(sts);
				
				Map<String, Quantity> requests = new HashMap<>();
				Quantity cpuQuantity = new Quantity(cpu);
				requests.put("cpu", cpuQuantity);
				Quantity memoryQuantity = new Quantity(memory);
				requests.put("memory", memoryQuantity);
				
				StatefulSet updateSts = stsBuilder
						.editSpec().editTemplate().editSpec().editFirstContainer().editResources()
						.withRequests(requests)
						.withLimits(requests)
						.endResources().endContainer().endSpec().endTemplate().endSpec()
						.build();
				
				statefulSets.createOrReplace(updateSts);
			}
			
			stsWatch.close();
			podWatch.close();
			
			done(JobResult.OK, "리소스 설정 변경 완료 (" + releaseName +")", null);
			
		} catch (Exception e) {
			done(JobResult.ERROR, "", e);
		}
		
	}
}
