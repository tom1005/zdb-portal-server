package com.zdb.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.client.KubernetesClient;

public class WatchExample2 {

	private static final Logger logger = LoggerFactory.getLogger(WatchExample2.class);

	private static String pvcName = "maria-pvc-004";

	public static void main(String[] args) throws InterruptedException {
		// String master = "http://localhost:8080/";
		// if (args.length == 1) {
		// master = args[0];
		// }

		try (final KubernetesClient client = K8SUtil.kubernetesClient()) {

			// client.persistentVolumeClaims().inNamespace("zdb").withName("a").watch(new
			// Watcher<PersistentVolumeClaim>())
			PersistentVolumeClaimSpec pvcSpec = null;
			Map<String, String> labels = new HashMap<>();
			labels.put("billingType", "hourly");

			pvcSpec = new PersistentVolumeClaimSpec();

			ResourceRequirements rr = new ResourceRequirements();
			Map<String, Quantity> mp = new HashMap<String, Quantity>();
			mp.put("storage", new Quantity("20Gi"));
			// rr.setLimits(mp);
			Map<String, Quantity> req = new HashMap<String, Quantity>();
			req.put("storage", new Quantity("20Gi"));
			rr.setRequests(req);
			pvcSpec.setResources(rr);
			// pvcSpec.setVolumeName("maraiadb0003");
			List<String> access = new ArrayList<String>();
			access.add("ReadWriteMany");
			pvcSpec.setAccessModes(access);

			Map<String, String> annotations = new HashMap<>();
			annotations.put("volume.beta.kubernetes.io/storage-class", "ibmc-file-silver");
			PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder().withNewMetadata().withName(pvcName)
					.withAnnotations(annotations).withLabels(labels).endMetadata().withSpec(pvcSpec).build();

			PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace("zdb").create(pvcCreating);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Could not watch resources" + e.getMessage());
		}

//		final CountDownLatch closeLatch = new CountDownLatch(1);
//		// Config config = new
//		// ConfigBuilder().withMasterUrl(master).withWatchReconnectLimit(2).build();
//
//		try (final KubernetesClient client = K8SUtil.kubernetesClient()) {
//
//			// client.persistentVolumeClaims().inNamespace("zdb").withName("a").watch(new
//			// Watcher<PersistentVolumeClaim>())
//
//			try (Watch watch = client.persistentVolumeClaims().inNamespace("zdb").withName(pvcName)
//					.watch(new Watcher<PersistentVolumeClaim>() {
//						@Override
//						public void eventReceived(Action action, PersistentVolumeClaim resource) {
//							System.out.println("1.>>>>>>>>>>>>>>>>> "
//									+ String.format("%s: %s", action, resource.getMetadata().getResourceVersion()));
//						}
//
//						@Override
//						public void onClose(KubernetesClientException e) {
//							System.out.println("2.>>>>>>>>>>>>>>>>> " + e);
//							if (e != null) {
//								System.err.println(e.getMessage());
//								closeLatch.countDown();
//							}
//						}
//					})) {
//				closeLatch.await(100, TimeUnit.SECONDS);
//			} catch (KubernetesClientException | InterruptedException e) {
//				e.printStackTrace();
//				System.err.println("Could not watch resources" + e.getMessage());
//			} catch (Exception e) {
//				e.printStackTrace();
//				System.err.println("Could not watch resources" + e.getMessage());
//			}

			// try (Watch watch =
			// client.replicationControllers().inNamespace("default").watch(new
			// Watcher<ReplicationController>() {
			// @Override
			// public void eventReceived(Action action, ReplicationController
			// resource) {
			// System.out.println(String.format("%s: %s", action,
			// resource.getMetadata().getResourceVersion()));
			// }
			//
			// @Override
			// public void onClose(KubernetesClientException e) {
			// if (e != null) {
			// e.printStackTrace();
			// closeLatch.countDown();
			// }
			// }
			// })) {
			// closeLatch.await(10, TimeUnit.SECONDS);
			// } catch (KubernetesClientException | InterruptedException e) {
			// e.printStackTrace();
			// // System.err.println("Could not watch resources", e);
			// }
//		} catch (Exception e) {
//			e.printStackTrace();
//
//			Throwable[] suppressed = e.getSuppressed();
//			if (suppressed != null) {
//				for (Throwable t : suppressed) {
//					t.printStackTrace();
//				}
//			}
//		}
	}

}