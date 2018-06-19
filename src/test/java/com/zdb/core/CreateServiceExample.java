package com.zdb.core;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.domain.DefaultExchange;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.KubernetesConstants;
import com.zdb.core.util.K8SUtil;

public class CreateServiceExample {

	private static final Logger logger = LoggerFactory.getLogger(CreateServiceExample.class);

//	private-crabb3cd296aad42418425707d856037ca-alb1   true      enabled   private   10.178.191.134
//	public-crabb3cd296aad42418425707d856037ca-alb1    true      enabled   public    169.56.70.222
	
	public static void main(String[] args) throws Exception {
		
		Exchange exchange = new DefaultExchange();
		
		exchange.setProperty(KubernetesConstants.KUBERNETES_SERVICE_NAME, "mariadb89121234-test");
		exchange.setProperty(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "kube-system");
		exchange.setProperty("LOAD_BALANCER_IP", "169.56.70.222");
		exchange.setProperty("TYPE", "public");
		
		
		K8SUtil.doCreateService(exchange);
		
	}

}