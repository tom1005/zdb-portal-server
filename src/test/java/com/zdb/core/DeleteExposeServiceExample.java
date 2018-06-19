package com.zdb.core;


import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.domain.DefaultExchange;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.KubernetesConstants;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Service;

public class DeleteExposeServiceExample {

	private static final Logger logger = LoggerFactory.getLogger(DeleteExposeServiceExample.class);

//	private-crabb3cd296aad42418425707d856037ca-alb1   true      enabled   private   10.178.191.134
//	public-crabb3cd296aad42418425707d856037ca-alb1    true      enabled   public    169.56.70.222
	
	public static void main(String[] args) throws Exception {
		
		Exchange exchange = new DefaultExchange();
		
		exchange.setProperty(KubernetesConstants.KUBERNETES_SERVICE_NAME, "mariadb98-test");
		exchange.setProperty(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "zdb-maria");
		exchange.setProperty("LOAD_BALANCER_IP", "169.56.70.222");
		
		List<Service> serviceList = K8SUtil.getServices("zdb-maria", "mariadb98-test");
		for(Service svc : serviceList) {
			
			K8SUtil.doDeleteService(svc.getMetadata().getNamespace(), svc.getMetadata().getName());
		}
		
		
	}

}