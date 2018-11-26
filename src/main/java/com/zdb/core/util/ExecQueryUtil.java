package com.zdb.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecQueryUtil {

	public static void main(String[] args) {

		StringBuffer sb = new StringBuffer();
		sb.append("set global read_only=0;flush privileges;");
		sb.append("show variables like 'read_only'\\G");
		
		String command = " mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"" +sb.toString()+"\"";
		
		System.out.println(">>>"+execQuery("zdb-test2", "zdb-test2-qq-mariadb-slave-0", command)+"<<<");
		
		
	}
	
	public synchronized static String execQuery(String namespace, String podName, String sql) {
		List<String> result = new ArrayList<>();
		try {
			final CountDownLatch countDown = new CountDownLatch(1);
			DefaultKubernetesClient c = K8SUtil.kubernetesClient();
			PodResource<Pod, DoneablePod> podResource = c.pods().inNamespace(namespace).withName(podName);

			if (podResource == null) {
				log.error(podName + " 가 존재하지 않습니다.");
				return null;
			}

			Pod pod = podResource.get();
			if (pod == null) {
				log.error(podName + " 가 존재하지 않습니다.");
				return null;
			}

			// String releaseName = pod.getMetadata().getLabels().get("release");

			Boolean ready = podResource.isReady();
			if (!ready) {
				return null;
			}

			final String cmd = sql + "\n";
			log.info(cmd);

			ExecutorService executorService = Executors.newSingleThreadExecutor();
			try (KubernetesClient client = c;
					ExecWatch watch = client.pods().inNamespace(namespace).withName(podName).inContainer("mariadb").redirectingInput().redirectingOutput().redirectingError().exec();

					BlockingInputStreamPumper pump = new BlockingInputStreamPumper(watch.getOutput(), new Callback<byte[]>() {

						@Override
						public void call(byte[] data) {
							String temp = new String(data);
							log.debug(">>>" + temp + "<<<");
							if (temp != null && !temp.trim().isEmpty()) {
								String[] newline = temp.split("\n");

								for (String r : newline) {
									if (r.trim().isEmpty()) {
										continue;
									}
									result.add(r+"\n");
								}
							}

							countDown.countDown();
						}
					})) {

				executorService.submit(pump);
				watch.getInput().write(cmd.getBytes());
				countDown.await(5, TimeUnit.SECONDS);
				
				StringBuffer sb = new StringBuffer();
				for (String str : result) {
					sb.append(str);
				}

				return sb.toString();
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			} finally {
				executorService.shutdownNow();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

}
