package com.zdb.mariadb;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.crsh.console.jline.internal.Log;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;
import okhttp3.Response;

public class MariaDBShutDownUtil {
	
	public static MariaDBShutDownUtil instance;
	
	private MariaDBShutDownUtil() {
	}
	
	public static MariaDBShutDownUtil getInstance() {
		if(instance == null) {
			instance = new MariaDBShutDownUtil();
		}
		return instance;
	}

	public static void main(String[] args) {
		try {
			MariaDBShutDownUtil.getInstance().doShutdownAndDeleteAllPods("zdb-maria","zdb-maria-pns");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public  void doShutdownAndDeleteAllPods(String namespace, String serviceName) throws Exception {
		doShutdownAndDeletePod(namespace, serviceName, null);
	}
	
	public  void doShutdownAndDeletePod(String namespace, String serviceName, String podName) throws Exception {
		ScheduledExecutorService executorService = Executors.newScheduledThreadPool(20);

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			ExecWatch watch = null;
			BlockingInputStreamPumper pump = null;
			
			String password = null;
			List<Secret> items = client.inNamespace(namespace).secrets().withLabel("release", serviceName).list().getItems();
			if( items != null && !items.isEmpty()) {
				password = items.get(0).getData().get("mariadb-root-password");
			}
			
			if(password == null || password.isEmpty()) {
				Log.error(serviceName +" 의 Secret Data [mariadb-root-password]를 조회 할 수 없습니다.");
				return;
			} else {
				password = new String(Base64.getDecoder().decode(password));
			}
			
			String[] commands = new String[] { "/bin/sh", "-c", "/opt/bitnami/mariadb/bin/mysqladmin -u root -p"+password+" -h 127.0.0.1 shutdown"};
			
			for (int i = 0; i < commands.length; i++) {
				commands[i] = URLEncoder.encode(commands[i], "UTF-8");
			}
			
			List<Pod> pods = client.inNamespace(namespace).pods().withLabel("release", serviceName).list().getItems();
			
			List<String> podNameList = new ArrayList<>();
			
			for (Pod pod : pods) {
				String name = pod.getMetadata().getName();
				
				if (podName != null) {
					if(!name.equals(podName)) {
						continue;
					}
				}
				
				boolean isReady = K8SUtil.IsReady(pod);
				if (isReady) {
					podNameList.add(name);
				} else {
					Log.error(name + " is NotReady.");
				}
			}
			
			final CountDownLatch latch = new CountDownLatch(podNameList.size());
			for (String name : podNameList) {
				Execable<String, ExecWatch> usingListener = client.inNamespace(namespace).pods().withName(name).inContainer("mariadb").redirectingOutput()
						.usingListener(new ExecListener() {
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

				latch.await(5, TimeUnit.SECONDS);
				watch.close();

				Log.info(name +" is shutdown. ->"+callback.getResult());
				
				Thread.sleep(2000);
			}
			
			for (Pod pod : pods) {
				String name = pod.getMetadata().getName();
				
				if (podName != null) {
					if(!name.equals(podName)) {
						continue;
					}
				}
				
				PodResource<Pod, DoneablePod> podResource = client.inNamespace(namespace).pods().withName(name);
				if (podResource != null) {
					Log.info(name +" deleting.");
					podResource.delete();
				}
			}
			
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
