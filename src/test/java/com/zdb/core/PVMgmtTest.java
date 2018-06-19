package com.zdb.core;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class PVMgmtTest {
	DefaultKubernetesClient client = null;

	String fileName = ".bluemix/plugins/container-service/clusters/zdb-dev/kube-config-seo01-zdb-dev.yml";

	// assigning the values
	@Before
	public void setUp() throws Exception {

		try {
			File file = new File(System.getProperty("user.home"), fileName);
			if (!file.exists() || !file.isFile()) {
				fail("File does not exist: " + file.getAbsolutePath());
			}

			String idToken = null;
			String url = null;

			InputStream input = new FileInputStream(file);
			Yaml yaml = new Yaml();
			Map<String, Object> iccsConfig = (Map<String, Object>) yaml.load(input);
			if (iccsConfig != null) {
				ArrayList<Object> users = (ArrayList<Object>) iccsConfig.get("users");
				if (users != null) {
					Map<String, Object> user = (Map<String, Object>) users.get(0);
					if (user != null) {
						Map<String, Object> userInfo = (Map<String, Object>) user.get("user");
						if (userInfo != null) {
							Map<String, Object> authProvider = (Map<String, Object>) userInfo.get("auth-provider");
							if (authProvider != null) {
								Map<String, Object> authProviderConfig = (Map<String, Object>) authProvider.get("config");
								if (authProviderConfig != null) {
									idToken = (String) authProviderConfig.get("id-token");
									// System.out.printf("idToken: %s\n", idToken);
								}
							}
						}
					}
				}

				ArrayList<Object> clusters = (ArrayList<Object>) iccsConfig.get("clusters");
				if (clusters != null) {
					Map<String, Object> cluster_0 = (Map<String, Object>) clusters.get(0);
					if (cluster_0 != null) {
						Map<String, Object> cluster = (Map<String, Object>) cluster_0.get("cluster");
						if (cluster != null) {
							url = (String) cluster.get("server");
						}
					}
				}
			}

			// assert idToken.isEmpty() || url.isEmpty();

			// System.out.printf("id-token from config file: %s\n", idToken);
			// System.out.printf("url from config file: %s\n", url);

			ConfigBuilder builder = new ConfigBuilder();

			builder.withMasterUrl(url);
			builder.withOauthToken(idToken);
			builder.withTrustCerts(true);

			Config config = builder.build();

			client = new DefaultKubernetesClient(config);

			// System.out.printf("master url: %s\n", client.getMasterUrl());
			// System.out.printf("kubernetes api version: %s\n", client.getApiVersion());
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

	}
	
	@Test
	public void doCreatePersistentVolumeClaim() {
		// PersistentVolumeClaim pvc = null;
		// String pvcName = "";//exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, String.class);
		// String namespaceName = "";//exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
		// PersistentVolumeClaimSpec pvcSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC, PersistentVolumeClaimSpec.class);
		// if (ObjectHelper.isEmpty(pvcName)) {
		// System.out.println("Create a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
		// throw new IllegalArgumentException("Create a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
		// }
		// if (ObjectHelper.isEmpty(namespaceName)) {
		// System.out.println("Create a specific Persistent Volume Claim require specify a namespace name");
		// throw new IllegalArgumentException("Create a specific Persistent Volume Claim require specify a namespace name");
		// }
		// if (ObjectHelper.isEmpty(pvcSpec)) {
		// System.out.println("Create a specific Persistent Volume Claim require specify a Persistent Volume Claim spec bean");
		// throw new IllegalArgumentException("Create a specific Persistent Volume Claim require specify a Persistent Volume Claim spec bean");
		// }
		// Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS, Map.class);
		// EditablePersistentVolumeClaim pvcCreating = new
		// PersistentVolumeClaimBuilder().withNewMetadata().withName(pvcName).withLabels(labels).endMetadata().withSpec(pvcSpec).build();
		// pvc = getEndpoint().getKubernetesClient().persistentVolumeClaims().inNamespace(namespaceName).create(pvcCreating);
		// exchange.getOut().setBody(pvc);

		// PersistentVolumeClaimSpec(accessModes=[ReadWriteMany], resources=ResourceRequirements(limits=null,
		// requests={storage=Quantity(amount=20Gi, format=null, additionalProperties={})},
		// additionalProperties={}),
		// selector=null, storageClassName=null, volumeName=pvc-4ded5088-265c-11e8-a06f-0eaa8201fa31, additionalProperties={})
		PersistentVolumeClaimSpec pvcSpec = null;
		Map<String, String> labels = new HashMap<>();
		labels.put("billingType", "hourly");

		pvcSpec = new PersistentVolumeClaimSpec();

		ResourceRequirements rr = new ResourceRequirements();
		Map<String, Quantity> mp = new HashMap<String, Quantity>();
		mp.put("storage", new Quantity("20Gi"));
//		rr.setLimits(mp);
		Map<String, Quantity> req = new HashMap<String, Quantity>();
		req.put("storage", new Quantity("20Gi"));
		rr.setRequests(req);
		pvcSpec.setResources(rr);
//		pvcSpec.setVolumeName("maraiadb0003");
		List<String> access = new ArrayList<String>();
		access.add("ReadWriteMany");
		pvcSpec.setAccessModes(access);

		Map<String, String> annotations = new HashMap<>();
		annotations.put("volume.beta.kubernetes.io/storage-class", "ibmc-file-silver");
		PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder()
				.withNewMetadata()
				.withName("mariadb2-test-pvc")
				.withAnnotations(annotations)
				.withLabels(labels)
				.endMetadata()
				.withSpec(pvcSpec)
				.build();

		PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace("zdb").create(pvcCreating);

		System.out.println(pvc);
	}

	@Test
	public void deletePVC() {

	}

	@After
	public void tearDown() throws Exception {
		client.close();
	}
}
