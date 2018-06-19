package com.zdb.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;

public class PodLogExample {

	private static final Logger logger = LoggerFactory.getLogger(PodLogExample.class);

	public static void main(String[] args) {
		// if (args.length < 1) {
		// System.out.println("Usage: podName [master] [namespace]");
		// return;
		// }
		String podName = "microbean-helm-example-mariadb-675d57f95d-s9ndl";
		String namespace = "default";
		String master = "https://localhost:8443/";

		if (args.length > 1) {
			master = args[1];
		}
		if (args.length > 2) {
			namespace = args[2];
		}

		System.out.println("Log of pod " + podName + " in " + namespace + " is:");
		System.out.println("----------------------------------------------------------------");

		Config config = new ConfigBuilder().withMasterUrl(master).build();
		try (KubernetesClient client = new DefaultKubernetesClient(config); LogWatch watch = client.pods().inNamespace(namespace).withName(podName).tailingLines(10).watchLog(System.out)) {
			Thread.sleep(5 * 1000);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
}