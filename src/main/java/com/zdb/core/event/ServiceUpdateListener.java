package com.zdb.core.event;

import java.io.FileNotFoundException;
import java.util.List;

import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient ;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 */
@Slf4j
public class ServiceUpdateListener extends ServiceEventAdapter {

	public ServiceUpdateListener(ZDBRepository repo, String txId, String namespace, String servcieName) {
		super(repo, txId, namespace, servcieName);
	}

	@Override
	public void run() {
		try {
			startTime = System.currentTimeMillis();
			KubernetesClient  client = K8SUtil.kubernetesClient();

			while (true) {
				if (System.currentTimeMillis() - startTime > expireTime) {
					String msg = "시간 초과 [" + expireTime + "(초)]";
					log.warn(msg);

					done(msg);
					break;
				}
				try {

					List<Pod> items = client.pods().list().getItems();
					boolean createdPod = false;
					boolean isRunning = false;

					for (Pod pod : items) {
						boolean existPod = pod.getMetadata().getName().startsWith(serviceName);

						if (existPod) {
							createdPod = true;

							String message = pod.getStatus().getPhase();
							log.info(serviceName + " status : " + message);

							if ("Running".equals(message)) {
								isRunning = true;
								break;
							}
						} else {
							continue;
						}
					}

					boolean createdDeployment = false;

					List<Deployment> deploymentList = client.extensions().deployments().list().getItems();

					for (Deployment deployment : deploymentList) {

						// deployment name = serviceName + '-{appName}'
						if (deployment.getMetadata().getName().startsWith(serviceName)) {
							DeploymentStatus status = deployment.getStatus();

							Integer availableReplicas = status.getAvailableReplicas();
							if (availableReplicas != null && availableReplicas.intValue() > 0) {
								createdDeployment = true;
							}

							log.debug("AvailableReplicas : " + availableReplicas);
							break;
						}
					}

					if (createdPod && isRunning && createdDeployment) {
						log.debug("Created Pod : {}, Status pod : {}, Created Deployment : {} - success!", createdPod, isRunning, createdDeployment);
						done(String.format("Created Pod : %s, Status pod : %s, Created Deployment : %s - success!", createdPod, isRunning, createdDeployment));
						break;
					} else {
						log.debug("Created Pod : {}, Status pod : {}, Created Deployment : {}", createdPod, isRunning, createdDeployment);

						running(String.format("Created Pod : %s, Status pod : %s, Created Deployment : %s", createdPod, isRunning, createdDeployment));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				Thread.sleep(5000);
			}

		} catch (FileNotFoundException e) {
			failure(e, e.getMessage());
		} catch (KubernetesClientException e) {
			failure(e, e.getMessage());
		} catch (InterruptedException e) {
			failure(e, e.getMessage());
		} catch (Exception e) {
			failure(e, e.getMessage());
		}
	}
}