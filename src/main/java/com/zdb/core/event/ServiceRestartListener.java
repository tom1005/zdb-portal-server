package com.zdb.core.event;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient ;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 */
@Slf4j
public class ServiceRestartListener extends ServiceEventAdapter {

	public ServiceRestartListener(ZDBRepository repo, String txId, String namespace, String servcieName) {
		super(repo, txId, namespace, servcieName);
	}

	@Override
	public void run() {

		try {
			startTime = System.currentTimeMillis();
			KubernetesClient  client = K8SUtil.kubernetesClient();

			String podName = null;
			String newPodName = null;
			Pod pod = null;
			Pod newPod = null;
			while (true) {

				if (System.currentTimeMillis() - startTime > expireTime) {
					done("Pod status : [" + podName + "] " +"시간 초과 [" + expireTime + "(초)]");
					break;
				}

				List<Pod> items = client.pods().list().getItems();

				List<String> podNameList = new ArrayList<String>();

				for (Pod p : items) {
					if (p.getMetadata().getName().startsWith(serviceName)) {
						podNameList.add(p.getMetadata().getName());
						if (podName == null) {
							podName = p.getMetadata().getName();
							pod = p;
						} else {
							if (!podName.equals(p.getMetadata().getName())) {
								newPodName = p.getMetadata().getName();
								newPod = p;
							}
						}
					}
				}

				if (!podNameList.contains(podName)) {
					done("Pod [" + podName + "] deleted.");
				}

				if (newPod != null) {
					String phase = newPod.getStatus().getPhase();

					running("New Pod [" + newPod.getMetadata().getName() + "] status : " + phase);
					log.debug(">>>>>>>>>>>>>>>>>>>>>>>>> retarting! : " + newPodName);
					
					if (!podNameList.contains(podName) && phase.equals("Running") && K8SUtil.isAvailableReplicas(namespace, serviceName)) {
						log.debug(">>>>>>>>>>>>>>>>>>>>>>>>> retart! : " + newPodName);
						done("new Pod [" + pod.getMetadata().getName() + "] status : " + phase + " retart ");
						break;
					}
				} else {
					String phase = pod.getStatus().getPhase();
					running("Old Pod [" + pod.getMetadata().getName() + "] status : " + phase);
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