package com.zdb.core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreatePersistentVolumeClaim {
	public void createPersistentVolumeClaim(String namespace, String storageSize, String storageClassName, String pvcName, Map<String, String> labels) throws Exception {
		PersistentVolumeClaimSpec pvcSpec = new PersistentVolumeClaimSpec();

		ResourceRequirements rr = new ResourceRequirements();

		Map<String, Quantity> req = new HashMap<String, Quantity>();
		req.put("storage", new Quantity(storageSize));
		rr.setRequests(req);
		pvcSpec.setResources(rr);

		List<String> access = new ArrayList<String>();
		access.add("ReadWriteOnce");
		pvcSpec.setAccessModes(access);

		Map<String, String> annotations = new HashMap<>();
		annotations.put("volume.beta.kubernetes.io/storage-class", storageClassName);
		
		PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder()
				.withNewMetadata()
				.withName(pvcName)
				.withAnnotations(annotations)
				.withLabels(labels)
				.endMetadata()
				.withSpec(pvcSpec)
				.build();

		try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();) {
			final CountDownLatch latch = new CountDownLatch(1);
			
			final String _pvcName = pvcName;
			
			Watcher<PersistentVolumeClaim> watcher = new Watcher<PersistentVolumeClaim>() {

				@Override
				public void eventReceived(Action action, PersistentVolumeClaim resource) {
					String status = null;
					if (resource instanceof PersistentVolumeClaim) {
						PersistentVolumeClaim meta = (PersistentVolumeClaim) resource;
						if(_pvcName.equals(meta.getMetadata().getName()))
						status = meta.getStatus().getPhase();
						if("Bound".equalsIgnoreCase(status)) {
							System.out.println(_pvcName + " created.!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
							latch.countDown();
						}
					}
				}

				@Override
				public void onClose(KubernetesClientException cause) {
					if(cause != null) {
						log.error(cause.getMessage(), cause);
					} else {
						log.error("CreatePersistentVolumeClaim closed...........");
					}
				}
				
			};
			Watch watch = kubernetesClient.persistentVolumeClaims().inNamespace(namespace).watch(watcher);
			PersistentVolumeClaim pvc = kubernetesClient.persistentVolumeClaims().inNamespace(namespace).create(pvcCreating);
			
			latch.await();
			watch.close();
		} catch (Exception e) {
			throw e;
		}
	}
}