package com.zdb.core;

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

import com.zdb.core.util.K8SUtil;

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

public class ShutDownExec {

	public static void main(String[] args) {
		try {
			System.out.println("disk inof : "+new ShutDownExec().doBackupCommand());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public  String doBackupCommand() throws Exception {
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			ExecWatch watch = null;
			BlockingInputStreamPumper pump = null;
			
//			String[] commands = new String[] { "/bin/sh", "-c", "df -P | grep bitnami | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }'"};
			String[] commands = new String[] { "/bin/sh", "-c", "/opt/bitnami/mariadb/bin/mysqladmin -u root -pzdb12#$ -h 127.0.0.1 shutdown"};
			
			for (int i = 0; i < commands.length; i++) {
				commands[i] = URLEncoder.encode(commands[i], "UTF-8");
			}
			
			final CountDownLatch latch = new CountDownLatch(1);
			
			ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch> inContainer = 
					client.inNamespace("zdb-maria").pods().withName("zdb-maria-pns-mariadb-master-0").inContainer("mariadb");
			
			TtyExecErrorable<String, OutputStream, PipedInputStream, ExecWatch> redirectingOutput = inContainer.redirectingOutput();
			
			Execable<String, ExecWatch> usingListener = redirectingOutput.usingListener(new ExecListener() {
				@Override
				public void onOpen(Response response) {
				}

				@Override
				public void onFailure(Throwable t, Response response) {
					latch.countDown();
					System.err.println(response.toString());
				}

				@Override
				public void onClose(int code, String reason) {
					latch.countDown();
				}
			});
			watch = usingListener.exec(commands);
			
			CustomCallback callback = new CustomCallback();
			
			pump = new BlockingInputStreamPumper(watch.getOutput(), callback);
			executorService.submit(pump);
			Future<String> outPumpFuture = executorService.submit(pump, "Done");
			executorService.scheduleAtFixedRate(new FutureChecker("Pump", outPumpFuture), 0, 5, TimeUnit.SECONDS);

			latch.await(10, TimeUnit.SECONDS);
			watch.close();
			
			return callback.getResult();
		} finally {
			if (executorService != null) {
				executorService.shutdown();
			}
		}
	}
	
	class CustomCallback implements Callback<byte[]> {
		String result = null;
		
		public void call(byte[] input) {
			try {
				result = new String(input, "UTF-8");
			} catch (Exception e) {
				e.printStackTrace();
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
			if(!future.isDone()) {
				System.out.println("Future:[" + name + "] is not done yet");
			} else {
				System.out.println("Future:[" + name + "] is done.");
			}
		}
	}
}
