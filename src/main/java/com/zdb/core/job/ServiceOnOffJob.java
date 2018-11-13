package com.zdb.core.job;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceOnOffJob extends JobAdapter {

	public ServiceOnOffJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		int toggle = param.getToggle();
		if (toggle == 1) {
			return "Service On";
		} else {
			return "Service Off";
		}
	}

	@Override
	public JobKind getKind() {
		int toggle = param.getToggle();
		
		if (toggle == 1) {
			return JobKind.START_POD;
		} else {
			return JobKind.SHUTDOWN_POD;
		}
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String stsName = param.getStatefulsetName();
		
		int toggle = param.getToggle();
		
		try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();){
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = kubernetesClient.inNamespace(namespace).apps().statefulSets();
			
			StatefulSet statefulSet = statefulSets.withName(stsName).get();
			StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
			
			long replicas = statefulSet.getSpec().getReplicas();
			
			if(replicas == toggle) {
				
				if (toggle == 1) {
					log.info("skip : "+stsName + "서비스가 이미 동작 중입니다.");
					done(JobResult.ERROR, stsName + "서비스가 이미 동작 중입니다.", null);
				} else {
					log.info("skip : "+stsName + "서비스가 이미 종료 되었습니다.");
					done(JobResult.ERROR, stsName + "서비스가 이미 종료 되었습니다.", null);
				}
				
				return;
			} else {
				StatefulSet newSts = stsBuilder.editSpec().withReplicas(toggle).endSpec().build();
				
				statefulSets.createOrReplace(newSts);
				if (toggle == 1) {
					done(JobResult.OK, stsName +" 서비스를 시작 했습니다.", null);
				} else {
					done(JobResult.OK, stsName +" 서비스를 종료 했습니다.", null);
				}
			}
			
		} catch (Exception e) {
			done(JobResult.ERROR, "", e);
		}
		
	}
}
