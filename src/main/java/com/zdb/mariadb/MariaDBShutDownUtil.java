package com.zdb.mariadb;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.crsh.console.jline.internal.Log;

import com.zdb.core.util.ExecUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;

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
			
//			String[] commands = new String[] { "/bin/sh", "-c", "/opt/bitnami/mariadb/bin/mysqladmin -u root -p"+password+" -h 127.0.0.1 shutdown"};
//			
//			for (int i = 0; i < commands.length; i++) {
//				commands[i] = URLEncoder.encode(commands[i], "UTF-8");
//			}
			
			List<Pod> pods = client.inNamespace(namespace).pods().withLabel("release", serviceName).list().getItems();
			
			List<Pod> _pods = new ArrayList<>();
			for (Pod pod : pods) {
				_pods.add(pod);
			}
			
			if(_pods != null && _pods.size() > 1) {
				Collections.sort(_pods, new Comparator<Pod>() {
					@Override
					public int compare(Pod o1, Pod o2) {
						String app = o1.getMetadata().getLabels().get("app");
						
						if("mariadb".equals(app)) {
							String c1 = o1.getMetadata().getLabels().get("component");
							String c2 = o2.getMetadata().getLabels().get("component");
							
							return c2.compareTo(c1);
							
						} else if("redis".equals(app)) {
							String c1 = o1.getMetadata().getLabels().get("role");
							String c2 = o2.getMetadata().getLabels().get("role");
							return c2.compareTo(c1);
						}
						
						return 0;
					}
				});
			}
			
			List<String> podNameList = new ArrayList<>();
			
			for (Pod pod : _pods) {
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
			
			String cmd = "/opt/bitnami/mariadb/bin/mysqladmin -u root -p"+password+" -h 127.0.0.1 shutdown";
			
			for (String name : podNameList) {
				new ExecUtil().exec(K8SUtil.kubernetesClient(), namespace, name, "mariadb", cmd);
				Thread.sleep(2000);
			}
			
			for (Pod pod : _pods) {
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
