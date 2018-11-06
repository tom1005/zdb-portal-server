package com.zdb.snippet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.zdb.core.util.K8SUtil;
import com.zdb.snippet.JobHandler.JobKind;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreatePersistentVolumeClaimsJob extends JobAdapter {

	public CreatePersistentVolumeClaimsJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		return "Create new PersistentVolumeClaim";
	}

	@Override
	public JobKind getKind() {
		return JobKind.CREATE_PVC;
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String serviceName = param.getServiceName();
		String serviceType = param.getServiceType();
		String podName = param.getPodName();
		String storageSize = param.getSize();
		String billingType = param.getBillingType();
		String storageClassName = param.getStorageClass();
		String accessMode = param.getAccessMode();
		String targetPVCName = param.getTargetPvc();
		
		if(namespace == null || namespace.isEmpty()) {
			IllegalArgumentException e = new IllegalArgumentException("Namespace 입력하세요.");
			done(JobResult.ERROR, e.getMessage(), e);
			throw e;
		}
		
		if(serviceName == null || serviceName.isEmpty()) {
			IllegalArgumentException e = new IllegalArgumentException("Service Name 입력하세요.");
			done(JobResult.ERROR, e.getMessage(), e);
			throw e;
		}
		
		if(podName == null || podName.isEmpty()) {
			IllegalArgumentException e = new IllegalArgumentException("Pod Name 입력하세요.");
			done(JobResult.ERROR, e.getMessage(), e);
			throw e;
		}
		
		if(serviceType == null || serviceType.isEmpty()) {
			IllegalArgumentException e = new IllegalArgumentException("Service Type 정보를 입력하세요.");
			done(JobResult.ERROR, e.getMessage(), e);
			throw e;
		}
		
		String role = "";
		
		try {
			Pod pod = K8SUtil.getPodWithName(namespace, serviceName, podName);
				
			if (pod != null) {
				serviceType = pod.getMetadata().getLabels().get("app");
				if ("mariadb".equals(serviceType)) {
					role = pod.getMetadata().getLabels().get("component");
				} else if ("redis".equals(serviceType)) {
					role = pod.getMetadata().getLabels().get("role");
				}

				Iterator<Volume> iterator = pod.getSpec().getVolumes().iterator();
				while(iterator.hasNext()) {
					Volume v = iterator.next();
					String name = v.getName();
					if("data".equals(name)) {
						PersistentVolumeClaimVolumeSource persistentVolumeClaim = v.getPersistentVolumeClaim();
						if (persistentVolumeClaim != null) {
							param.setSourcePvc(persistentVolumeClaim.getClaimName());
							break;
						}
					} 
				}
				
			} else {
				IllegalArgumentException e = new IllegalArgumentException("실행중인 복제 대상 서비스가 존재하지 않습니다. [Pod : "+podName+"]");
				done(JobResult.ERROR, e.getMessage(), e);
				throw e;
			}

			List<PersistentVolumeClaim> pvcList = K8SUtil.getPersistentVolumeClaims(namespace, serviceName);
			for (PersistentVolumeClaim persistentVolumeClaim : pvcList) {
				String svcRole = "";
				if ("mariadb".equals(serviceType)) {
					svcRole = persistentVolumeClaim.getMetadata().getLabels().get("component");
				} else if ("redis".equals(serviceType)) {
					svcRole = persistentVolumeClaim.getMetadata().getLabels().get("role");
				}

				if (!role.equals(svcRole)) {
					continue;
				}

				if (storageClassName == null || storageClassName.isEmpty()) {
					storageClassName = persistentVolumeClaim.getSpec().getStorageClassName();
				}
				
				if (billingType == null || billingType.isEmpty()) {
					billingType = "hourly";
				}
				
				String pvcName = persistentVolumeClaim.getMetadata().getName();
				
				if (targetPVCName == null || targetPVCName.isEmpty()) {
					// data-ns-zdb-02-demodb-mariadb-slave-0
					String[] split = pvcName.split("-");
					String indexValue = split[split.length - 1];
					int index = Integer.parseInt(indexValue) + 1;

					targetPVCName = pvcName.substring(0, pvcName.length() - 1) + "" + index;
					
					while(K8SUtil.getPersistentVolumeClaim(namespace, targetPVCName) != null) {
						targetPVCName = pvcName.substring(0, pvcName.length() - 1) + "" + ++index;
					}
					
					param.setTargetPvc(targetPVCName);
				} else {
					break;
				}
			}
		
			Map<String, String> labels = new HashMap<>();
			labels.put("release", serviceName);
			labels.put("billingType", billingType);
			
			if("mariadb".equals(serviceType)) {
				labels.put("app", serviceType);
				labels.put("component", role);
			} else if("redis".equals(serviceType)) {
				labels.put("app", serviceType);
				labels.put("role", role);
			} else {
				log.error("{} 는 지원하지 않는 서비스 타입입니다.",serviceType);
				String msg = serviceType + " 는 지원하지 않는 서비스 타입입니다.";
				done(JobResult.ERROR, msg, new IllegalArgumentException(msg));
			}
		
			DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();
			PersistentVolumeClaimSpec pvcSpec = new PersistentVolumeClaimSpec();

			ResourceRequirements rr = new ResourceRequirements();

			Map<String, Quantity> req = new HashMap<String, Quantity>();
			req.put("storage", new Quantity(storageSize));
			rr.setRequests(req);
			pvcSpec.setResources(rr);

			List<String> access = new ArrayList<String>();
			access.add(accessMode);
			pvcSpec.setAccessModes(access);

			Map<String, String> annotations = new HashMap<>();
			annotations.put("volume.beta.kubernetes.io/storage-class", storageClassName);

			PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder().withNewMetadata().withName(targetPVCName).withAnnotations(annotations).withLabels(labels).endMetadata().withSpec(pvcSpec).build();

			final CountDownLatch latch = new CountDownLatch(1);

			final String _pvcName = targetPVCName;

			Watcher<PersistentVolumeClaim> watcher = new Watcher<PersistentVolumeClaim>() {

				@Override
				public void eventReceived(Action action, PersistentVolumeClaim resource) {
					if (_pvcName.equals(resource.getMetadata().getName())) {
						String status = resource.getStatus().getPhase();

						if (Action.MODIFIED == action) {
							if ("Bound".equalsIgnoreCase(status)) {
								latch.countDown();
							}
						}
						
						progress(resource.getMetadata().getName()+" - "+status);
					}
				}

				@Override
				public void onClose(KubernetesClientException cause) {

				}

			};
			Watch watch = kubernetesClient.persistentVolumeClaims().inNamespace(namespace).watch(watcher);
			PersistentVolumeClaim pvc = kubernetesClient.persistentVolumeClaims().inNamespace(namespace).create(pvcCreating);

			latch.await();
			watch.close();

			done(JobResult.OK, _pvcName, null);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			done(JobResult.ERROR, param.getTargetPvc(), e);
		}
		
		//////////////////////////////////////////////////////////////////////////////
	}
}
