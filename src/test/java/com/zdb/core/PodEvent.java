package com.zdb.core;

import java.util.List;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Event;

public class PodEvent {

	public static void main(String[] args) {
		try {
			//getPodResources("zdb-maria", "mariadb", "maria-test009");
			List<Event> podEvent = K8SUtil.getPodEvent("zdb-dev-test2", "zdb-206-mariadb-master-0");
			
			for (Event event : podEvent) {
				System.out.println(event.getFirstTimestamp() +" | "+ event.getLastTimestamp()+" | "+ event.getSource().getComponent() +"/"+ event.getSource().getHost() +" | "+event.getMessage());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
