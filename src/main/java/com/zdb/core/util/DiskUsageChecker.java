package com.zdb.core.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.zdb.core.domain.CommonConstants;
import com.zdb.core.domain.DiskUsage;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskUsageChecker {
	
	public List<DiskUsage> getAllDiskUsage() throws Exception {
//		String[] commands = new String[] { "/bin/sh", "-c", "df -P | grep bitnami | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }'" };
		//String cmd = "/bin/df -P | grep bitnami | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }'";
		long s = System.currentTimeMillis();

		List<DiskUsage> list = new ArrayList<>();
		ExecutorService executorService = Executors.newCachedThreadPool();
//		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(100);

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {

//			for (int i = 0; i < commands.length; i++) {
//				commands[i] = URLEncoder.encode(commands[i], "UTF-8");
//			}

			List<Pod> items = new ArrayList<>();
			// zdb namespace label
			List<Namespace> nsList = client.inAnyNamespace().namespaces().withLabel(CommonConstants.ZDB_LABEL, "true").list().getItems();
			for (Namespace namespace : nsList) {
				items.addAll(client.inNamespace(namespace.getMetadata().getName()).pods().list().getItems());
			}
			String cmd = "/bin/df -P | grep bitnami | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }'";
			
			for (Pod pod : items) {

				String containerName = null;

				try {
					String app = pod.getMetadata().getLabels().get("app");

					if ("redis".equals(app)) {
						// redis 의 container 명은 release 명을 사용
						// mariadb 는 label 의 app 명을 container 명으로 사용한다.
						containerName = pod.getMetadata().getLabels().get("release");

						cmd = "/bin/df -P /bitnami/redis/data | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }'";
						String role = pod.getMetadata().getLabels().get("role");
						if (!"master".equals(role)) {
							continue;
						}

					} else if ("mariadb".equals(app)) {
						containerName = pod.getMetadata().getLabels().get("app");
					} else if ("postgresql".equals(app)) {
						// TODO 추가 대상...
					}

					if (containerName == null || containerName.isEmpty()) {
						continue;
					}
				} catch (Exception e1) {
					continue;
				}

				String podName = pod.getMetadata().getName();
				String releaseName = pod.getMetadata().getLabels().get("release");
				String namespace = pod.getMetadata().getNamespace();
				
				DefaultKubernetesClient k8sClient = K8SUtil.kubernetesClient();
				 

				try {
					if (!K8SUtil.IsReady(pod)) {
						log.error("{} > {} > {} is not running.", namespace, releaseName, podName);
						continue;
					}
					log.debug("{} > {} > {} disk usage ckeck.", namespace, releaseName, podName);
					List<Container> containers = client.inNamespace(namespace).pods().withName(pod.getMetadata().getName()).get().getSpec().getContainers();
					if (containers.size() > 0) {
						containerName = containers.get(0).getName();
					}
					
					String temp = new ExecUtil().exec(k8sClient, namespace, podName, containerName, cmd);

					DiskUsage diskUsage = new DiskUsage();
					diskUsage.setNamespace(namespace);
					diskUsage.setReleaseName(releaseName);
					diskUsage.setPodName(podName);

//					String temp = callback.getResult();
					if (temp != null && !temp.trim().isEmpty()) {
						String[] split = temp.split(" ");

						// Size Used Avail Use%
						// 20971520 257088 20714432 2%
						if (split.length == 4) {
							diskUsage.setSize(Integer.parseInt(split[0]));
							diskUsage.setUsed(Integer.parseInt(split[1]));
							diskUsage.setAvail(Integer.parseInt(split[2]));
							diskUsage.setUseRate(split[3].trim());
							diskUsage.setUpdateTime(new Date(System.currentTimeMillis()));
							list.add(diskUsage);
						}
					}

				} catch (Exception e) {
					log.error(e.getMessage(), e);
					continue;
				}

			}

			return list;
		} finally {
			if (executorService != null) {
				executorService.shutdown();
			}
			log.info("---------------> Disk usage update : " + (System.currentTimeMillis() - s));
		}

	}
}
