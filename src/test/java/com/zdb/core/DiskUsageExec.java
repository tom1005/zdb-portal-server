package com.zdb.core;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.DiskUsage;
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

public class DiskUsageExec implements Callback<byte[]> {

	public static void main(String[] args) {
		try {
			new DiskUsageExec().doBackupCommand();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void toJson() {
		DiskUsage diskUsage= new DiskUsage();
		diskUsage.setSize(12345);
		diskUsage.setUsed(67890);
		diskUsage.setAvail(121212);
		diskUsage.setUseRate("3%\n".trim());
		diskUsage.setUpdateTime(new Date(System.currentTimeMillis()));
		
		Gson gson = new GsonBuilder().setPrettyPrinting().setDateFormat("yyyyMMddHHmmss").create();
		String json = gson.toJson(diskUsage);
		
		System.out.println(json);
	}
	
	public  void doBackupCommand() throws InterruptedException, IOException, Exception {
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			ExecWatch watch = null;
			BlockingInputStreamPumper pump = null;
			
//			String[] commands = new String[] {
//					"/bin/sh"
//					, "-c"
//					, "df -Ph | grep IBM | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END {print size\" \"used\" \"avail\" \"use}'"
////					, "-u"
////					, "root"
////					, "-p"
////					, "zdb1234"
////					, "shutdown"
//			};
			String[] commands = new String[] { "/bin/sh", "-c", "df -P | grep bitnami | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }'" };

			final CountDownLatch latch = new CountDownLatch(1);
			
			ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch> inContainer = 
					client.inNamespace("zdb-redis").pods().withName("zdb-redis-namyu4-master-0").inContainer("zdb-redis-namyu4");
			
			TtyExecErrorable<String, OutputStream, PipedInputStream, ExecWatch> redirectingOutput = inContainer.redirectingOutput();
			
			Execable<String, ExecWatch> usingListener = redirectingOutput.usingListener(new ExecListener() {
				@Override
				public void onOpen(Response response) {
					System.out.println(response.toString());
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
	}

	@Override
	public void call(byte[] input) {
		try {
			System.out.println(new String(input, "UTF-8"));
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
