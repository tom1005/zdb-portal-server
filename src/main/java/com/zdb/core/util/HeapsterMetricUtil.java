package com.zdb.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorable;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;
import okhttp3.Response;

/**
 * 에러로그, slowlog 조회 및 다운로드 
 * @author a06919
 *
 */
public class HeapsterMetricUtil implements Callback<byte[]> {
	
	public static void main(String[] args) throws Exception {
		// curl GET http://heapster.kube-system/api/v1/model/namespaces/zdb-system/pod-list/zdb-system-zdb-mariadb-master-0/metrics/memory-usage

		Object result = new HeapsterMetricUtil().getMemoryUsage("ns-zdb-02", "ns-zdb-02-demodb-mariadb-master-0");

		System.out.println(result);
		
		result = new HeapsterMetricUtil().getCPUUsage("ns-zdb-02", "ns-zdb-02-demodb-mariadb-master-0");

		System.out.println(result);
		
	}

	StringBuffer sb = new StringBuffer();
	
	public  Object getMemoryUsage(String namespace, String podName) throws InterruptedException, IOException, Exception {
		String result = getMetric(namespace, podName, "memory-usage");

		Gson gson = new GsonBuilder().create();
		java.util.Map<String, Object> object = gson.fromJson(result, java.util.Map.class);
		return ((Map) ((List) object.get("items")).get(0)).get("metrics");
	}
	
	public Object getCPUUsage(String namespace, String podName) throws InterruptedException, IOException, Exception {
		String result = getMetric(namespace, podName, "cpu-usage");

		Gson gson = new GsonBuilder().create();
		java.util.Map<String, Object> object = gson.fromJson(result, java.util.Map.class);
		return ((Map) ((List) object.get("items")).get(0)).get("metrics");
	}
	
	public synchronized String getMetric(String namespace, String podName, String metric) throws InterruptedException, IOException, Exception {
		String[] commands = new String[] { "/bin/bash", "-c", "curl GET http://heapster.kube-system/api/v1/model/namespaces/"+namespace+"/pod-list/"+podName+"/metrics/" + metric };
		String result = exec(namespace, podName, commands);
		return result;
	}
	
	public synchronized String exec(String namespace, String podName, String[] commands) throws InterruptedException, IOException, Exception {
		sb = new StringBuffer();
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			ExecWatch watch = null;
			BlockingInputStreamPumper pump = null;
			

			final CountDownLatch latch = new CountDownLatch(1);
			
			Pod pod = client.inNamespace("zdb-system").pods().withLabel("app", "zdb-portal-server").list().getItems().get(0);
			
			// "gdi-iam-prod-db-mariadb-master-0"
			ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch> inContainer = 
					client.inNamespace("zdb-system").pods().withName(pod.getMetadata().getName()).inContainer("zdb-portal-server");
			
			TtyExecErrorable<String, OutputStream, PipedInputStream, ExecWatch> redirectingOutput = inContainer.redirectingOutput();
			
			Execable<String, ExecWatch> usingListener = redirectingOutput.usingListener(new ExecListener() {
				@Override
				public void onOpen(Response response) {
//					System.out.println(response.toString());
				}

				@Override
				public void onFailure(Throwable t, Response response) {
					latch.countDown();
					System.err.println(response.toString());
				}

				@Override
				public void onClose(int code, String reason) {
					latch.countDown();
//					System.out.println("onClose - code:"+code+",reason : "+reason);
				}
			});
			
			for (int i = 0; i < commands.length; i++) {
				commands[i] = URLEncoder.encode(commands[i], "UTF-8");
			}
			watch = usingListener.exec(commands);
			
			pump = new BlockingInputStreamPumper(watch.getOutput(), this);
			executorService.submit(pump);
			Future<String> outPumpFuture = executorService.submit(pump, "Done");
			executorService.scheduleAtFixedRate(new FutureChecker("Pump", outPumpFuture), 0, 30, TimeUnit.SECONDS);

			latch.await(30, TimeUnit.SECONDS);
			watch.close();
		}
		executorService.shutdown();
		
		return sb.toString();
	}

	@Override
	public void call(byte[] input) {
		try {
			String x = new String(input, "UTF-8");
//			System.out.println(x);
			sb.append(x).append("\n");
		} catch (Exception e) {
			e.printStackTrace();
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
			if(!future.isDone()) {
//				System.out.println("Future:[" + name + "] is not done yet");
			}
		}
	}
}
