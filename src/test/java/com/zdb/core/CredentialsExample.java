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
		
		String token = "eyJraWQiOiIyMDE3MTAzMC0wMDowMDowMCIsImFsZyI6IlJTMjU2In0.eyJpYW1faWQiOiJJQk1pZC01MDZKN0FONThNIiwiaXNzIjoiaHR0cHM6Ly9pYW0ubmcuYmx1ZW1peC5uZXQva3ViZXJuZXRlcyIsInN1YiI6InBuc0Bzay5jb20iLCJhdWQiOiJieCIsImV4cCI6MTUyMjM5MzU1OCwiaWF0IjoxNTIyMzg5OTU4fQ.biwW11KQuDVtHMqDnPqMjusuJJMPzl1MzzMocstKd9JZZ1R7EVTlIahowRSkJE3FOkdNivMsTQ7-0QbOYhuMqvwNRd2n7n3Olxms1yTFTNT7GaiBLtCjiH1WngD2dKNZbEMyXQcVztvYIgqQXBCXq2ZIcSwkCClXxxaxNz7jPK3UdFVVJU0qO5Mjv3tPX1b-Kmms7MOtv01J5hggRYnsmQGn6R5wbUWfXSRSqZInbljCkZXMYKuAy7XWqZfuf0SGBKqPee1AtIAR0-K2iYlHl9_0RjuuBGwV3F_Dcxm_nfMZkdg-kSS892vGhB4LhYNaCZSBS21I4PJvoQANSTzE3Q";

		Config config = new ConfigBuilder()
				.withMasterUrl("https://169.56.69.242:32254")
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