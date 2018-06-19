package com.zdb.core;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;

public class K8SClientTest {
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

	/**
	 * https://github.com/fabric8io/kubernetes-client
	 */
	@Test
	public void testNamespaceList() {

		NamespaceList myNs = client.namespaces().list();
		assertTrue(myNs.getItems().size() > 0);

		for (Namespace ns : myNs.getItems()) {
			System.out.println("namespace : " + ns.getMetadata().getName());
		}

	}
	
	
	@Test
	public void testReplicaSets() {

		ReplicaSetList list = client.inNamespace("default").extensions().replicaSets().list();
		assertTrue(list.getItems().size() > 0);

		for (ReplicaSet rs : list.getItems()) {
			if(rs.getMetadata().getName().startsWith("mariadb2-test")) {
				System.out.println("ReplicaSet : " + rs.getMetadata().getName());
				
				
				Gson gson =  new GsonBuilder().setPrettyPrinting().create();
				
				System.out.println(gson.toJson(rs));
				
			}
		}
System.out.println("");
	}
	
	@Test
	public void testDeletePod() {

//		NonNamespaceOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> inNamespace = client.pods().inNamespace("default");
//		client.extensions().deployments().withName("serviceName").get().getMetadata().
//		client.pods().inNamespace("default").withName("maria-test3-mariadb-55d76f8786").readingInput(System.in)
//                .writingOutput(System.out)
//                .writingError(System.err)
//                .withTTY()
//                .usingListener(new SimpleListener());
		PodList list = client.pods().list();
		
		String podName = null;
		String namespaceName = null;
		for(Pod pod : list.getItems()) {
			if(pod.getMetadata().getName().startsWith("maria")) {
				podName = pod.getMetadata().getName();
				namespaceName = pod.getMetadata().getNamespace();
				break;
			}
		}
		
		Boolean delete = client.pods().inNamespace(namespaceName).withName(podName).delete();

		assert(delete);

	}

	/**
	 * https://github.com/fabric8io/kubernetes-client
	 */
	@Test
	public void testNamespace() {

		Namespace ns = client.namespaces().withName("default").get();
		assertNotNull(ns.getMetadata().getName());
		System.out.println("namespace : " + ns);

	}
	
	@Test
	public void testDeployment() {

		Deployment deployment = client.extensions().deployments().inNamespace("default").withName("mariadb2-test").get();
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		String string = deployment.getMetadata().getLabels().get("app");
		String json = gson.toJson(deployment);
		
		System.out.println("deployment : " + json);

	}

	/**
	 * https://github.com/fabric8io/kubernetes-client
	 */
	@Test
	public void testServiceList() {

		ServiceList myServices = client.services().list();
		assertTrue(myServices.getItems().size() > 0);

		for (Service service : myServices.getItems()) {
			System.out.println("service : " + service.getMetadata().getName());
		}
	}

	/**
	 * https://github.com/fabric8io/kubernetes-client
	 */
	@Test
	public void testService() {

		// ServiceList myNsServices = client.services().inNamespace("default").list();
		// Service myservice = client.services().inNamespace("default").withName("myservice").get();
		Service myservice = client.services().inNamespace("kube-system").withName("heapster").get();

		assertNotNull(myservice.getMetadata().getName());
		System.out.println("service : " + myservice);
	}

	/**
	 * https://github.com/fabric8io/kubernetes-client
	 */
	@Test
	public void testPod() {

		Pod pod = client.pods().inNamespace("default").withName("microbean-helm-example-mariadb-675d57f95d-s9ndl").get();

		assertNotNull(pod.getMetadata().getName());
		System.out.println("pod : " + pod.getMetadata());
	}

	@Test
	public void testWatchLogPod() {
		String podName = "zdb-rest-api-deployment-d8c9cb6b8-drkb6";
		String namespace = "zdb";

		System.out.println("Log of pod " + podName + " in " + namespace + " is:");
		System.out.println("----------------------------------------------------------------");
		
		// client.pods().inNamespace(namespace).withName(podName).inContainer(container).tailingLines(0).watchLog(System.out)
		try (LogWatch watch = client.pods().inNamespace(namespace).withName(podName).tailingLines(0).watchLog(System.out)) {
			Thread.sleep(1 * 1000 * 100);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@After
	public void tearDown() throws Exception {
		client.close();
	}
}
