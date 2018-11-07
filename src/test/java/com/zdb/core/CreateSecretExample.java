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
		
		String token = "eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJ6ZGItc3lzdGVtIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6InpkYi1zeXN0ZW0tYWNjb3VudC10b2tlbi1zZzZjMiIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJ6ZGItc3lzdGVtLWFjY291bnQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiJkZGRlOGVmYy1kMTFiLTExZTgtYWJhOS0wYTY3ZWNkOWIzNWQiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6emRiLXN5c3RlbTp6ZGItc3lzdGVtLWFjY291bnQifQ.h1JwXHjXjixu76QlFlLDZsoFEEW49AIEEJswovGwNTTbr1F10aJ0p6xQ_lEokqHlHuwroMPUg9AsJsnnxhxu2u3XImpXopCmhjY2DFF1__MLdO9DhaeWMPfwIwGyobdH2d86tBa15qSE56ASVBLkZ3QX2cby082KyrwozURSu-1Oep2UX3EwAaBgOURyjkCT1Q45fZJIeLqUXZkKliAy0BW2RTzxN6q8haud4WEAOoHqnWUIoow2nUu0I729OL3Y-PA3uZTs0oYX-TSPS50R7ExIrOavvj8Uy_Ysaf0vkUgBHvqUZHUkB7dRID7a1mupqYyEc1JylsaTc6GjWn-ugs-Lg_SJ2D6phDwzHPGIPPu90_R7xXxRwUoeayNIeh6Eg7FPP9XVGtiVvrewin9bGn1d8HJR_6O2Mmz_x53zwHXjAPa7eG2u_wenaQllT4EvMlSdFpTKWpJbgd10T_vHbua2hDYWHMivaQBPambAQqhzX842AcXAlKKKAsEBqOgIiXHvi5wABfBnlwW3-CuXhh-jcPNrKeOnd2uTr-enzK6dcYAOebSrJn2ef2Q4SQgFUQTf9xke_FzX0VSHaR8dDkvP1wghLhxk8y-OWrkdIbM95bciW77i38FFSZ37lG9oX0vHC7YQYTIcOxTk9XjlgvbFZiL9fSAgl8Us0amIk58";

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