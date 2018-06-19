package com.zdb.core;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class UpdateNamespace {

	public static void main(String[] args) {
		new UpdateNamespace().doUpdate();
	}

	public void doUpdate() {

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {

			//new PersistentVolumeClaimBuilder().editOrNewMetadata().withName("").s
			
			 
			Namespace ns = new NamespaceBuilder().withNewMetadata().withName("lwk").addToLabels("name", "zdb").endMetadata().build();
			client.namespaces().createOrReplace(ns);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		}
	}

	
}
