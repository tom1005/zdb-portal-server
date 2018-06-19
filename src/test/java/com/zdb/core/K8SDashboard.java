package com.zdb.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;

public class K8SDashboard {

	public static void main(String[] args) throws Exception {
		
		List<Pod> pods = K8SUtil.kubernetesClient().inAnyNamespace().pods().list().getItems();
		
		System.out.println("hostIP\tNamespace\tname\tPod IP\tStatus");
		
		Map<String, List<Pod>> map = new TreeMap<>();
		
		for(Pod pod : pods) {
			String name = pod.getMetadata().getName();
			String namespace = pod.getMetadata().getNamespace();
			String host = pod.getStatus().getHostIP();
			String podIP = pod.getStatus().getPodIP();
			String phase = pod.getStatus().getPhase();
			
			List<Pod> podList = new ArrayList<>();
			if(map.containsKey(host)) {
				podList = map.get(host);
			} else {
				podList = new ArrayList<>();
			}
			podList.add(pod);
			map.put(host, podList);
			
//			System.out.println(String.format("%s\t%s\t%s\t%s\t%s", host, namespace, name, podIP, phase));
		}
		
		for(String host : map.keySet()) {
			List<Pod> podList = map.get(host);
			for(Pod pod :  podList) {
				String name = pod.getMetadata().getName();
				String namespace = pod.getMetadata().getNamespace();
				String podIP = pod.getStatus().getPodIP();
				String phase = pod.getStatus().getPhase();
				
				System.out.println(String.format("%s\t%s\t%s\t%s\t%s", host, namespace, name, podIP, phase));
			}
			
		}
		
	}

}
