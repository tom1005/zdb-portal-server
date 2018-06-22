package com.zdb.core;

import java.util.ArrayList;
import java.util.List;

import javax.mail.Folder;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class DeleteLB {

	public static void main(String[] args) {
		new DeleteLB().doUpdate();
	}

	public void doUpdate() {

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			
			List<String> usedIp = new ArrayList<>();

			List<Service> items = client.inAnyNamespace().services().withLabel("app","mariadb").list().getItems();
			for (Service service : items) {
				if("loadbalancer".equals(service.getSpec().getType().toLowerCase())) {
					List<ServicePort> ports = service.getSpec().getPorts();
//					for(ServicePort port : ports) {
//						if("mysql".equals(port.getName())){
//							System.out.println(Integer.toString(port.getPort()));
//							break;
//						}
//					}
					
					List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
					if( ingress != null && ingress.size() > 0) {
						usedIp.add("ibm-cloud-provider-ip-"+ingress.get(0).getIp().toString().replace(".", "-"));
//						System.out.println(ingress.get(0).getIp() );
					} else {
						throw new Exception("unknown ServicePort");
					}
				}
			}
			
			for (String ip : usedIp) {
				System.out.println(ip);
			}
			 
			List<Deployment> items2 = client.inNamespace("ibm-system").extensions().deployments().list().getItems();
			
			for (Deployment deployment : items2) {
				String name = deployment.getMetadata().getName();
				if(usedIp.contains(name)) {
					continue;
				}
				System.err.println(name);
				
				client.inNamespace("ibm-system").extensions().deployments().withName(name).delete();
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		}
	}

	
}
