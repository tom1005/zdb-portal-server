package com.zdb.snippet;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CopyProgressChcker {

	public Map<String, DiskUsage> getDiskInfo(DefaultKubernetesClient c, String namespace, String podName) {

		final CountDownLatch countDown = new CountDownLatch(1);

		PodResource<Pod, DoneablePod> podResource = c.pods().inNamespace(namespace).withName(podName);

		Map<String, DiskUsage> resultMap = new TreeMap<>();
		Boolean ready = podResource.isReady();
		if (!ready) {
			return resultMap;
		}

		final String cmd = "df -P | grep /data | awk '{print  $2 \" \" $3 \" \"$4 \" \" $5 \" \" $6  }'\n";

		ExecutorService executorService = Executors.newSingleThreadExecutor();
		try (KubernetesClient client = c;
				ExecWatch watch = client.pods().inNamespace(namespace).withName(podName).redirectingInput().redirectingOutput().redirectingError().exec();

				BlockingInputStreamPumper pump = new BlockingInputStreamPumper(watch.getOutput(), new Callback<byte[]>() {

					@Override
					public void call(byte[] data) {
						String temp = new String(data);
						temp = temp.replace("/ # ", "");

						if (temp != null && !temp.trim().isEmpty()) {
							String[] newline = temp.split("\n");
							for (String t : newline) {
								DiskUsage diskUsage = new DiskUsage();

								t = t.trim();
								String[] split = t.split(" ");

								// 41153856 484532 40652940 1% /data2
								// 41153856 484528 40652944 1% /data1
								if (split.length == 5) {
									diskUsage.setSize(Double.parseDouble(split[0]));
									diskUsage.setUsed(Double.parseDouble(split[1]));
									diskUsage.setAvail(Double.parseDouble(split[2]));
									diskUsage.setUseRate(split[3].trim());
									diskUsage.setPath(split[4].trim());

									resultMap.put(split[4].trim(), diskUsage);
								}

							}
						}

						countDown.countDown();
					}
				})) {

			executorService.submit(pump);
			watch.getInput().write(cmd.getBytes());
			countDown.await(10, TimeUnit.SECONDS);

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw KubernetesClientException.launderThrowable(e);
		} finally {
			executorService.shutdownNow();
		}

		return resultMap;
	}

	public static void main(String[] args) {
		try {
			
			System.out.println("abc3\n\n\nabc2\n\nabc1\n1234444444444444444\n");
			// List<DiskUsage> diskUsage = DiskUsageChecker.getInstance().getAllDiskUsage();
			DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();

			Map<String, DiskUsage> diskInfo = new CopyProgressChcker().getDiskInfo(kubernetesClient, "zdb-test2", "data-copy-pod");

			if (diskInfo.containsKey("/data1") && diskInfo.containsKey("/data2")) {
				DiskUsage data1 = diskInfo.get("/data1");
				double used1 = data1.getUsed();
				DiskUsage data2 = diskInfo.get("/data2");
				double used2 = data2.getUsed();

				// 100 , 10

				System.out.println(used2 + " / " + used1 + "[" + Math.round((used2 / used1) * 100) + "%]");

			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
