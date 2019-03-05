package com.zdb.core;

import java.util.List;

import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.PodManager;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PodEvent {

	public static void main(String[] args) {
		try {
			//getPodResources("zdb-maria", "mariadb", "maria-test009");
			List<Event> podEvent = K8SUtil.getPodEvent("zdb-dev-test2", "zdb-206-mariadb-master-0");
			
			for (Pod pod : pods) {
				log.info("Pod : {} {}",pod.getMetadata().getName(), PodManager.isReady(pod));
			}
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
