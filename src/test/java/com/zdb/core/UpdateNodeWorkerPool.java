package com.zdb.core;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class UpdateNodeWorkerPool {

	public static void main(String[] args) {
		new UpdateNodeWorkerPool().doUpdate();
	}

	public void doUpdate() {

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {

			//new PersistentVolumeClaimBuilder().editOrNewMetadata().withName("").s
			Node node = client.nodes().withName("10.178.218.163").get();
			
			NodeBuilder nodeBuilder = new NodeBuilder(node);
			Node build = nodeBuilder.editMetadata().addToLabels("worker-pool", "dev").endMetadata().build();
			
			client.nodes().createOrReplace(build);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		}
	}

	
}
