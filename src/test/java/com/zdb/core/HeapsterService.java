package com.zdb.core;

import java.util.List;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class HeapsterService {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub

		Service heapsterService = null;
		List<Service> services = K8SUtil.getServicesWithNamespace("kube-system");
		
		for (Service service : services) {
			if(service.getMetadata().getName().equals("heapster")) {
				heapsterService = service;
				break;
			}
		}
		
		Service service = heapsterService;

		String portStr = null;

		if("loadbalancer".equals(service.getSpec().getType().toLowerCase())) {
			List<ServicePort> ports = service.getSpec().getPorts();
			for (ServicePort port : ports) {
				portStr = Integer.toString(port.getPort());
				break;
			}
			
			if (portStr == null) {
				throw new Exception("unknown ServicePort");
			}
			
			List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
			if( ingress != null && ingress.size() > 0) {
				System.out.println(ingress.get(0).getIp() + ":" + portStr);
			} else {
				throw new Exception("unknown ServicePort");
			}
		} else if ("clusterip".equals(service.getSpec().getType().toLowerCase())) {
			List<ServicePort> ports = service.getSpec().getPorts();
			for(ServicePort port : ports) {
					portStr = Integer.toString(port.getPort());
					break;
			}
			if (portStr == null) {
				throw new Exception("unknown ServicePort");
			}
			
			System.out.println( service.getSpec().getClusterIP() + ":" + portStr);
		} else {
			log.warn("no cluster ip.");
			
		}
	}

}
