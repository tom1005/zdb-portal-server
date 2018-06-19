package com.zdb.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;

public class ExecExample {

	public static void main(String[] args) throws Exception {
		String podName = "mydb-mariadb-master-0";
		String namespace = "zdb-dev-test";
		
		String[] commands = new String[] {
				"/bin/sh"
				, "-c"
				, "df -Ph | grep IBM | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END {print size\" \"used\" \"avail\" \"use}'"
		};

		for (int i = 0; i < commands.length; i++) {
			commands[i] = URLEncoder.encode(commands[i], "UTF-8");
		}

		try (final KubernetesClient client = K8SUtil.kubernetesClient()) {
			String a = "df -Ph | grep bitnami | awk '{size = $2} {used = $3} {avail=$4} {use=$5} END { print size \" \"used \" \" avail \" \" use }' \n";
//			String a = "df -Ph  \n";
			InputStream is = new ByteArrayInputStream(a.getBytes());
			final CountDownLatch latch = new CountDownLatch(1);
			
			ExecWatch watch = client.pods().inNamespace(namespace).withName(podName)
	                .readingInput(is)
	                .writingOutput(System.out)
	                .writingError(System.err)
	                .withTTY()
	                .usingListener(new SimpleListener(latch))
	                .exec();

//			Thread.sleep(5 * 1000);
			
			latch.await(5, TimeUnit.SECONDS);
		}
	}

	private static class SimpleListener implements ExecListener {
		CountDownLatch latch;
		
		SimpleListener(CountDownLatch c) {
			latch = c;
		}
		@Override
		public void onOpen(Response response) {
			System.out.println("The shell will remain open for 10 seconds.");
		}

		@Override
		public void onFailure(Throwable t, Response response) {
			System.err.println("shell barfed");
			
			latch.countDown();
		}

		@Override
		public void onClose(int code, String reason) {
			System.out.println("The shell will now close.");
			latch.countDown();
		}
	}

}