package com.zdb.core;

import java.util.List;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class UpdateNamespace {

	public static void main(String[] args) {
		new UpdateNamespace().doUpdate();
	}

	public void doUpdate() {

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {

			//new PersistentVolumeClaimBuilder().editOrNewMetadata().withName("").s
			
			List<Pod> items = client.inNamespace("zdb-maria").pods().list().getItems();
			
			for (Pod pod : items) {
				System.out.println(pod.getMetadata().getName());
			}
			
//			Namespace ns = new NamespaceBuilder().withNewMetadata().withName("zdb-system").removeFromLabels("name").endMetadata().build();
//			Namespace ns = new NamespaceBuilder().withNewMetadata().withName("zdb-system").addToLabels("name", "").endMetadata().build();
//			client.namespaces().createOrReplace(ns);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		}
	}

	
}
