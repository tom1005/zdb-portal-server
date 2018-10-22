package com.zdb.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

public class ExecUtil implements Callback<byte[]> {

	final StringBuffer sb = new StringBuffer();
	
	public synchronized String exec(DefaultKubernetesClient client, String namespace, String podName, String command) throws InterruptedException, IOException, Exception {
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);
		try {
			ExecWatch watch = null;
			BlockingInputStreamPumper pump = null;
			
			String[] commands = new String[] {"sh", "-c", command};

			final CountDownLatch latch = new CountDownLatch(1);
			
//			Pod pod = client.inNamespace(namespace).pods().withLabel("release", podName).list().getItems().get(0);
			
			ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch> inContainer = 
					client.inNamespace(namespace).pods().withName(podName).inContainer("mariadb");
			
			TtyExecErrorable<String, OutputStream, PipedInputStream, ExecWatch> redirectingOutput = inContainer.redirectingOutput();
			
			Execable<String, ExecWatch> usingListener = redirectingOutput.usingListener(new ExecListener() {
				@Override
				public void onOpen(Response response) {
//					System.out.println(response.toString());
				}

				@Override
				public void onFailure(Throwable t, Response response) {
					latch.countDown();
//					System.err.println(response.toString());
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

			latch.await(300, TimeUnit.SECONDS);
			watch.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		executorService.shutdown();
		
		return sb.toString();
	}

	@Override
	public void call(byte[] input) {
		try {
			String x = new String(input, "UTF-8");
//			System.out.println(x);
			sb.append(x);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private class FutureChecker implements Runnable {
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
