package com.zdb.core;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;

public class CountDownLatchExample {
	static final int max = 10;

	/**
	 * 단일 쓰레드 테스트
	 */
	public static void testSingle() throws Exception {
		long start = System.currentTimeMillis();
		for (long i = 0; i < max; i++) {
			Thread.sleep(1000);
		}

		long elapsedTime = System.currentTimeMillis() - start;
		System.out.println("testSingle elapsed time -> " + elapsedTime);
	}

	/**
	 * CountDownLatch 테스트
	 */
	public static void testCountDownLatch() throws Exception {
		final CountDownLatch latch = new CountDownLatch(max);

//		latch.countDown();
//		latch.countDown();
//		latch.await(3, TimeUnit.SECONDS);
//		 
//		long count2 = latch.getCount();
//		count2 = latch.getCount();
//		count2 = latch.getCount();
//		latch.countDown();
//		count2 = latch.getCount();
//		
		long start = System.currentTimeMillis();
//		for (long i = 0; i < max; i++) {
//			new Thread(new Worker(latch)).start();
//		}

		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
				Watch eventsWatcher = client.events().watch(new Watcher<Event>(){

					@Override
					public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, Event resource) {
						System.out.println(resource);
						
					}

					@Override
					public void onClose(KubernetesClientException cause) {
						// TODO Auto-generated method stub
						
					}
					
				});
		
				Watch statefulSetsWatcher = client.apps().statefulSets().watch(new Watcher<StatefulSet>(){

					@Override
					public void eventReceived(io.fabric8.kubernetes.client.Watcher.Action action, StatefulSet resource) {
						System.out.println(resource);
						
					}

					@Override
					public void onClose(KubernetesClientException cause) {
						// TODO Auto-generated method stub
						
					}
					
				
					
				});
				
		latch.await();
		long elapsedTime = System.currentTimeMillis() - start;
		System.out.println("testCountDownLatch elapsed time -> " + elapsedTime);
	}

	static int count = 0;

	/**
	 * Job 쓰레드
	 */
	static class Worker implements Runnable {
		private CountDownLatch latch;

		public Worker(CountDownLatch latch) {
			this.latch = latch;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			} finally {
				if (this.latch == null)
					return;
				System.out.println(count++);
				latch.countDown();
			}
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		final CountDownLatch lacth = new CountDownLatch(1);

		new Thread(new Runnable() {

			@Override
			public void run() {
				long s = System.currentTimeMillis();
				
				try {
					while((System.currentTimeMillis() - s) < 10 * 60 * 1000) {
						Thread.sleep(3000);
						List<Pod> pods = K8SUtil.getPods("zdb-maria", "zdb-maria-pns666");
						boolean isAllReady = true;
						for(Pod pod : pods) {
							boolean isReady = Readiness.isReady(pod);
							System.out.println(">>>"+pod.getMetadata().getName()+" > "+isReady);
			
							isAllReady = isAllReady && isReady;
						}
						
						if(isAllReady) {
							lacth.countDown();
							System.out.println("------------------------------------------------- pod status ok ------------------------------------------------- ");
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		}).start();
		
		lacth.await(600, TimeUnit.SECONDS);
	}
}