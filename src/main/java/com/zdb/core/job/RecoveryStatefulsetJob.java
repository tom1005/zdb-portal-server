package com.zdb.core.job;

import java.util.Iterator;
import java.util.List;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RecoveryStatefulsetJob extends JobAdapter {

	public RecoveryStatefulsetJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		return "Recovery Statefulset";
	}

	@Override
	public JobKind getKind() {
		return JobKind.RECOVERY_STS;
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String stsName = param.getStatefulsetName();
		
		String orgPvc = param.getSourcePvc();
		String newPvc = param.getTargetPvc();
		
		try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();){
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = kubernetesClient.inNamespace(namespace).apps().statefulSets();
			
			if (newPvc != null && !newPvc.isEmpty()) {
				try {
					kubernetesClient.inNamespace(namespace).persistentVolumeClaims().withName(newPvc).delete();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
			
			if (orgPvc == null || orgPvc.isEmpty()) {
				StatefulSet statefulSet = statefulSets.withName(stsName).get();
				StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
				if(statefulSet.getSpec().getReplicas() > 0) {
					log.info("skip : "+stsName + " replicas : " + statefulSet.getSpec().getReplicas());
					done(JobResult.OK, stsName +" > replicas : 1", null);
					return;
				}
				StatefulSet newSts = stsBuilder.editSpec().withReplicas(1).endSpec().build();

				statefulSets.createOrReplace(newSts);
			} else {
				Volume dataVolume = new Volume();
				dataVolume.setName("data");
				// 원본 pvc 로 마운트. 
				dataVolume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(orgPvc, null));
				
				StatefulSet statefulSet = statefulSets.withName(stsName).get();
				
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

				statefulSets.createOrReplace(newSts);
				
			}
			
			if (orgPvc == null || orgPvc.isEmpty()) {
				done(JobResult.OK, "오류로 인한 복원 : " + stsName +"서비스는 재시작 합니다.", null);
			} else {
				done(JobResult.OK, "오류로 인한 복원 : " + stsName +"서비스는 재시작 합니다. PVC : " + orgPvc, null);
			}
			
		} catch (Exception e) {
			done(JobResult.ERROR, "", e);
		}
		
	}
}
