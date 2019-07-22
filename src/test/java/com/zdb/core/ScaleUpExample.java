package com.zdb.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

public class ScaleUpExample {

	private static final Logger logger = LoggerFactory.getLogger(ScaleUpExample.class);

	public static void main(String[] args) throws InterruptedException {
		
		String token = "";

		Config config = new ConfigBuilder()
				.withMasterUrl("https://169.56.69.242:26239")
				.withTrustCerts(true)
				.withOauthToken(token)
				.withNamespace("default")
				.build();

		try (final DefaultKubernetesClient client = new DefaultKubernetesClient(config)) {
			String namespace = "zdb-test2";
			String releaseName = "zdb-test2-ns2";
			
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = client.inNamespace(namespace).apps().statefulSets();
			
			List<StatefulSet> items = client.inNamespace(namespace).apps().statefulSets().withLabel("release", releaseName).list().getItems();
			
			for (StatefulSet sts : items) {
				
				StatefulSetBuilder stsBuilder = new StatefulSetBuilder(sts);
				
				Map<String, Quantity> requests = new HashMap<>();
				Quantity cpuQuantity = new Quantity("400m");
				requests.put("cpu", cpuQuantity);
				Quantity memoryQuantity = new Quantity("900Mi");
				requests.put("memory", memoryQuantity);
				
				
				StatefulSet updateSts = stsBuilder
						.editSpec().editTemplate().editSpec().editFirstContainer().editResources()
						.withRequests(requests)
						.withLimits(requests)
						.endResources().endContainer().endSpec().endTemplate().endSpec()
						.build();
				
				statefulSets.createOrReplace(updateSts);
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage(), e);

			Throwable[] suppressed = e.getSuppressed();
			if (suppressed != null) {
				for (Throwable t : suppressed) {
					logger.error(t.getMessage(), t);
				}
			}
		}
	}

	private static void log(String action, Object obj) {
		logger.info("{}: {}", action, obj);
	}

	private static void log(String action) {
		logger.info(action);
	}
}