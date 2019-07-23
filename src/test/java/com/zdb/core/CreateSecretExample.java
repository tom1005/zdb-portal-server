package com.zdb.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

public class CreateSecretExample {

	private static final Logger logger = LoggerFactory.getLogger(CreateSecretExample.class);

	public static void main(String[] args) throws InterruptedException {
		
		String token = "";

		Config config = new ConfigBuilder()
				.withMasterUrl("https://169.56.69.242:26239")
				.withTrustCerts(true)
				.withOauthToken(token)
				.withNamespace("default")
				.build();

		try (final DefaultKubernetesClient client = new DefaultKubernetesClient(config)) {
			String namespace = "zdb-test";
			String secretName = "zdb-system-secret";
			
			Secret s = client.inNamespace(namespace).secrets().withName(secretName).get();
			if(s == null) {
				Secret secret = client.inNamespace("zdb-system").secrets().withName(secretName).get();
				
				ObjectMeta metadata = new ObjectMeta();
				metadata.setNamespace(namespace);
				metadata.setName(secretName);
				secret.setMetadata(metadata);
				
				client.inNamespace(namespace).secrets().create(secret);
				
			} else {
				System.out.println("exist secret." + namespace);
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