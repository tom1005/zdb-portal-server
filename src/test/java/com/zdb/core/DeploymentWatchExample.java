package com.zdb.core;

import java.io.FileNotFoundException;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

public class DeploymentWatchExample {

	private static final Logger logger = LoggerFactory.getLogger(DeploymentWatchExample.class);

	private static String pvcName = "maria-pvc-004";

	public static void main(String[] args) throws Exception {
//		int max = 1;
		CountDownLatch closeLatch = new CountDownLatch(1);
//		
//		for (long i = 0; i < max; i++) {
//			System.out.println("idx : " + i);
//            new Thread(new Worker(closeLatch)).start();
//        }
//		
//		
//		System.out.println("---------------------------------end ");
		
		
		try (final KubernetesClient client = K8SUtil.kubernetesClient()) {
			
			client.extensions().deployments().inNamespace("zdb").withName("mariadb2-test-mariadb").watch(new Watcher<Deployment>() {

				@Override
				public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Deployment resource) {
					System.out.println("deployments >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> eventReceived : " +action  +"\t"+ resource);
				}

				@Override
				public void onClose(KubernetesClientException cause) {
					System.out.println("deployments >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> onClose : " + cause);
				}
				
			});
			
			client.pods().inNamespace("zdb").withName("mariadb2-test-mariadb-75c6844b88-8m9hz").watch(new Watcher<Pod>() {

				@Override
				public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Pod pod) {
					System.out.println("pods >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> eventReceived : " +action +"\t"+ pod);
				}

				@Override
				public void onClose(KubernetesClientException cause) {
					System.out.println("pods >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> onClose : " + cause);
				}
				
			});
			
			closeLatch.await();
			
		} catch (KubernetesClientException  e) {
			e.printStackTrace();
			System.err.println("Could not watch resources" + e.getMessage());
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	static int countdown = 0;
	
	static class Worker implements Runnable {
		private CountDownLatch latch;

		public Worker(CountDownLatch latch) {
			this.latch = latch;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			} finally {
				if (this.latch == null)
					return;
				System.out.println(countdown++);
				
				latch.countDown();
			}
		}
	}
}