package com.zdb.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLEncoder;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.zdb.core.util.DateUtil;
import com.zdb.core.util.K8SUtil;

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

public class ZDBSlowQueryLog implements Callback<byte[]> {

	public static void main(String[] args) {
		try {
			String namespace = null;
			String appName = null;
			int line = 300;
			
			if(args.length != 3) {
				System.out.println("Input namespace, appName... ");
				return;
			}
			
			namespace = args[0];
			appName = args[1];
			line = Integer.parseInt(args[2]);
			
			
			if( namespace == null ) {
				namespace = "gdi-iam-prod";
			}
			
			if( appName == null ) {
				appName = "gdi-iam-prod-db-mariadb-master-0";
			}
			
			if( line == 0 ) {
				line = 300;
			}
			
			String tailLog = new ZDBSlowQueryLog().getTailLog(namespace, appName, line);
			
			System.out.println(tailLog);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	final StringBuffer sb = new StringBuffer();
	
	public  String getTailLog(String namespace, String appName, int line) throws InterruptedException, IOException, Exception {
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			ExecWatch watch = null;
			BlockingInputStreamPumper pump = null;
			
			String[] commands = new String[] { "/bin/sh", "-c", "tail -n "+line+" /bitnami/mariadb/logs/maria_slow.log" };

			final CountDownLatch latch = new CountDownLatch(1);
			
			// "gdi-iam-prod-db-mariadb-master-0"
			ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch> inContainer = 
					client.inNamespace(namespace).pods().withName(appName).inContainer("mariadb");
			
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
					System.out.println("onClose - code:"+code+",reason : "+reason);
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
				System.out.println("Future:[" + name + "] is not done yet");
			}
		}
	}
}
