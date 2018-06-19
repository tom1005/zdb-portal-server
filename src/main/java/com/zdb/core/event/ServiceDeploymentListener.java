package com.zdb.core.event;

import java.util.concurrent.CountDownLatch;

import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 */
@Slf4j
public class ServiceDeploymentListener extends ServiceEventAdapter {

	public ServiceDeploymentListener(Exchange exchange) {
		super(exchange);
	}

	@Override
	public void run() {
		try {
			startTime = System.currentTimeMillis();
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			// client.inNamespace(namespace).pods().watch(watcher)

			ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);

			boolean haClusterEnabled = service.isClusterEnabled();
			int haClusterSlaveCount = service.getClusterSlaveCount();

			int countDown = 1;
			if (haClusterEnabled) {
				if (haClusterSlaveCount < 1) {
					// ha 일때 slave 는 최소 1
					haClusterSlaveCount = 1;
				}
				countDown += haClusterSlaveCount;
			}

			final CountDownLatch closeLatch = new CountDownLatch(countDown);

//			ZDBEventWatcher<Pod> eventWatcher = new ZDBEventWatcher<Pod>(KubernetesOperations.CREATE_POD_OPERATION, closeLatch, exchange);

			// while (true) {
			// if (System.currentTimeMillis() - startTime > expireTime) {
			// String msg = "시간 초과 [" + expireTime + "(초)]";
			// log.warn(msg);
			//
			// failure(new TimeoutException(msg), msg);
			// break;
			// }
			// try {
			//
			//// List<Pod> items = client.pods().list().getItems();
			// boolean createdPod = false;
			// boolean isRunning = false;
			//
			// List<Pod> activePod = K8SUtil.getActivePodList(namespace, serviceName);
			// if (activePod != null && !activePod.isEmpty()) {
			// createdPod = true;
			//
			// String message = activePod.get(0).getStatus().getPhase();
			// log.info(serviceName + " status : " + message);
			//
			// if ("Running".equals(message)) {
			// isRunning = true;
			//// break;
			// }
			// } else {
			// log.info("activePod is null. {},{}", namespace, serviceName);
			// }
			//// for (Pod pod : items) {
			//// boolean existPod = pod.getMetadata().getName().startsWith(serviceName);
			////
			//// if (existPod) {
			//// createdPod = true;
			////
			//// String message = pod.getStatus().getPhase();
			//// log.info(serviceName + " status : " + message);
			////
			//// if ("Running".equals(message)) {
			//// isRunning = true;
			//// break;
			//// }
			//// } else {
			//// continue;
			//// }
			//// }
			//
			// boolean createdDeployment = false;
			//
			// createdDeployment = K8SUtil.isAvailableReplicas(namespace, serviceName);
			//
			// if (createdPod && isRunning && createdDeployment) {
			// log.debug("Created Pod : {}, Status pod : {}, Created Deployment : {} - success!", createdPod, isRunning, createdDeployment);
			// done(String.format("Created Pod : %s, Status pod : %s, Created Deployment : %s - success!", createdPod, isRunning, createdDeployment));
			// break;
			// } else {
			// log.debug("Created Pod : {}, Status pod : {}, Created Deployment : {}", createdPod, isRunning, createdDeployment);
			//
			// running(String.format("Created Pod : %s, Status pod : %s, Created Deployment : %s", createdPod, isRunning, createdDeployment));
			// }
			// } catch (Exception e) {
			// failure(e, e.getMessage());
			// }
			// Thread.sleep(5000);
			// }

		} catch (KubernetesClientException e) {
			failure(e, e.getMessage());
		} catch (Exception e) {
			failure(e, e.getMessage());
		}
	}
}