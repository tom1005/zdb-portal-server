package com.zdb.core;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;

public class PodLogExample {

	private static final Logger logger = LoggerFactory.getLogger(PodLogExample.class);

	public static void main(String[] args) {
		// if (args.length < 1) {
		// System.out.println("Usage: podName [master] [namespace]");
		// return;
		// }
		String podName = "lwk-namyu7-redis-master-0";
		String namespace = "lwk";

		System.out.println("Log of pod " + podName + " in " + namespace + " is:");
		System.out.println("----------------------------------------------------------------");
		
String pid = "575556e0-9c63-11e8-8d2b-8af0775b4da3";
		try {
//			String log = K8SUtil.kubernetesClient().pods().inNamespace(namespace).withName(podName).inContainer("lwk-namyu7-redis").tailingLines(100).getLog();
//			System.out.println(log);
//			System.out.println("----------------------------------------------------------------");
//
//			String replaceAll = log.replaceAll(" \\[\\dm| \\[[\\d]{2}[;][\\d][;][\\d]m", "");
//			System.out.println(replaceAll);
			
			List<PersistentVolumeClaim> items = K8SUtil.kubernetesClient().inAnyNamespace().persistentVolumeClaims().list().getItems();
			
			for (PersistentVolumeClaim pvc : items) {
				String uid = pvc.getMetadata().getUid();
				String name = pvc.getMetadata().getName();
				
				
				
				System.out.println(uid + " \t"+ pvc.getMetadata().getNamespace() +" /"+ name);
				if(name.indexOf("57573a22-9c63-11e8-8d2b-8af0775b4da3") > -1) {
					
					System.err.println(pvc.getMetadata().getNamespace() +" /"+ name);
				}
			}

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
}