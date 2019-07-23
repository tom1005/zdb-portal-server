package com.zdb.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

public class CredentialsExample {

	private static final Logger logger = LoggerFactory.getLogger(CredentialsExample.class);

	public static void main(String[] args) throws InterruptedException {
		
		String token = "";

		Config config = new ConfigBuilder()
				.withMasterUrl("https://169.56.69.242:26239")
				.withTrustCerts(true)
				.withOauthToken(token)
				.withNamespace("default")
				.build();

		try (final KubernetesClient client = new AutoAdaptableKubernetesClient(config)) {
			
			log("Received pods", client.pods().list());

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