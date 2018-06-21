package com.zdb.core.util;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.zdb.core.domain.DiskUsage;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

@Slf4j
public class DiskUsageChecker {
	
	public static DiskUsageChecker instance;
	
	private DiskUsageChecker() {
//		if(instance == null) {
//			instance = new DiskUsageChecker();
//		}
	}
	
	public static DiskUsageChecker getInstance() {
		if(instance == null) {
			instance = new DiskUsageChecker();
		}
		
		return instance;
	}
	
	static String[] commands = new String[] { "/bin/sh", "-c", "df -P | grep bitnami | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }'" };


	public List<DiskUsage> getAllDiskUsage() throws Exception {
		long s = System.currentTimeMillis();

		List<DiskUsage> list = new ArrayList<>();

		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(100);

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {

			for (int i = 0; i < commands.length; i++) {
				commands[i] = URLEncoder.encode(commands[i], "UTF-8");
			}

			List<Pod> items = new ArrayList<>();
			List<Namespace> nsList = client.namespaces().withLabel("name", "zdb").list().getItems();
			for (Namespace namespace : nsList) {
				items.addAll(client.inNamespace(namespace.getMetadata().getName()).pods().list().getItems());
			}
			
			for (Pod pod : items) {

				String containerName = null;

				try {
					String app = pod.getMetadata().getLabels().get("app");

					if ("redis".equals(app)) {
						// redis 의 container 명은 release 명을 사용
						// mariadb 는 label 의 app 명을 container 명으로 사용한다.
						containerName = pod.getMetadata().getLabels().get("release");

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

				try {
					if (!K8SUtil.IsReady(pod)) {
						log.error("{} > {} > {} is not running.", namespace, releaseName, podName);
						continue;
					}

					List<Container> containers = client.inNamespace(namespace).pods().withName(pod.getMetadata().getName()).get().getSpec().getContainers();
					if (containers.size() > 0) {
						containerName = containers.get(0).getName();
					}
					final CountDownLatch latch = new CountDownLatch(1);

					ExecWatch watch = client.inNamespace(namespace).pods().withName(pod.getMetadata().getName()).inContainer(containerName).redirectingOutput().usingListener(new ExecListener() {
						@Override
						public void onOpen(Response response) {
						}

						@Override
						public void onFailure(Throwable t, Response response) {
							latch.countDown();
							log.error(t.getMessage(), t);
						}

						@Override
						public void onClose(int code, String reason) {
							latch.countDown();
						}
					}).exec(commands);

					CustomCallback callback = new CustomCallback();

					BlockingInputStreamPumper pump = new BlockingInputStreamPumper(watch.getOutput(), callback);
					executorService.submit(pump);
					Future<String> outPumpFuture = executorService.submit(pump, "Done");
					executorService.scheduleAtFixedRate(new FutureChecker("Pump", outPumpFuture), 0, 5, TimeUnit.SECONDS);

					latch.await(10, TimeUnit.SECONDS);
					watch.close();

					DiskUsage diskUsage = new DiskUsage();
					diskUsage.setNamespace(namespace);
					diskUsage.setReleaseName(releaseName);
					diskUsage.setPodName(podName);

					String temp = callback.getResult();
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
			System.out.println("---------------> Disk usage : " + (System.currentTimeMillis() - s));
		}

	}
	
	class CustomCallback implements Callback<byte[]> {
		String result = null;

		public void call(byte[] input) {
			try {
				result = new String(input, "UTF-8");
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}

		public String getResult() {
			return result;
		}
	}

	private static class FutureChecker implements Runnable {
		private final String name;
		private final Future<String> future;

		private FutureChecker(String name, Future<String> future) {
			this.name = name;
			this.future = future;
		}

		@Override
		public void run() {
			if (!future.isDone()) {
//				System.out.println("Future:[" + name + "] is not done yet");
			} else {
//				System.out.println("Future:[" + name + "] is done.");
			}
		}
	}
	
	public static void main(String[] args) {
		try {
			List<DiskUsage> diskUsage = DiskUsageChecker.getInstance().getAllDiskUsage();
//			List<DiskUsage> diskUsage = DiskUsageChecker.getInstance().getDiskUsage("zdb-dev-test", "mydb");
			
			for (DiskUsage d : diskUsage) {
				System.out.println(d.getPodName() +" > "+ d.getSize()+" > "+ d.getUsed()+" > "+ d.getAvail()+" > "+ d.getUseRate());
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
