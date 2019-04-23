package com.zdb.core.util;

import java.io.PipedInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.NonBlockingInputStreamPumper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecUtil {

	public static void main(String[] args) {
		
//		String cmd = "/opt/bitnami/mariadb/bin/mysqladmin -uroot -p$MARIADB_ROOT_PASSWORD status;";
		final String cmd = "/bin/df -P | grep bitnami | awk '{ print  $2 \" \" $3 \" \"4 \" \" $5 }'";
		String aaa;
		try {
			aaa = new ExecUtil().exec(K8SUtil.kubernetesClient(), "zdb-system", "zdb-portal-db-mariadb-0", "mariadb", cmd);
			System.out.println(aaa);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public synchronized String exec(DefaultKubernetesClient k8sClient,String namespace, String podName, String container, String cmd) throws Exception {
		final CountDownLatch countDown = new CountDownLatch(1);
		StringBuffer sb = new StringBuffer();
		
		Pod pod = k8sClient.pods().inNamespace(namespace).withName(podName).get();
		if(!PodManager.isReady(pod)) {
			throw new Exception(namespace +" > " + podName + " is not ready.");
		}
		
		ExecutorService executorService = Executors.newWorkStealingPool();
		try (KubernetesClient client = k8sClient;
				ExecWatch watch = client.pods().inNamespace(namespace).withName(podName)
						.inContainer(container)
						.redirectingInput()
						.readingOutput(new PipedInputStream(1024*1024))
						.exec();

				NonBlockingInputStreamPumper pump = new NonBlockingInputStreamPumper(watch.getOutput(), new Callback<byte[]>() {
					@Override
					public void call(byte[] data) {
						try {
							
							String temp = new String(data, "UTF-8");
//							System.out.println(">> "+temp);
							sb.append(temp);
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						} finally {
//							countDown.countDown();
						}
					}
				})) {

			executorService.submit(pump);
			if(!cmd.endsWith("\n")) {
				cmd = cmd + "\n";
			}
			watch.getInput().write(cmd.getBytes());
			
			countDown.await(3, TimeUnit.SECONDS);
			
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw KubernetesClientException.launderThrowable(e);
		} finally {
			executorService.shutdownNow();
		}
		
		return sb.toString();
	}
}
