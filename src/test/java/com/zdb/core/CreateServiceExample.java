package com.zdb.core;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Service;

public class CreateServiceExample {

	private static final Logger logger = LoggerFactory.getLogger(CreateServiceExample.class);

//	private-crabb3cd296aad42418425707d856037ca-alb1   true      enabled   private   10.178.191.134
//	public-crabb3cd296aad42418425707d856037ca-alb1    true      enabled   public    169.56.70.222
	
	public static void main(String[] args) throws Exception {
		
//		Exchange exchange = new DefaultExchange();
//		
//		exchange.setProperty(KubernetesConstants.KUBERNETES_SERVICE_NAME, "mariadb89121234-test");
//		exchange.setProperty(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "kube-system");
//		exchange.setProperty("LOAD_BALANCER_IP", "169.56.70.222");
//		exchange.setProperty("TYPE", "public");
//		
//		
//		K8SUtil.doCreateService(exchange);
		//ns-zdb-01-single-pub
//		{
//			File f = new File("/Users/a06919/github/zdb-portal-server/src/main/resources/mariadb/create_public_svc.template");
//			String temp = readFile(f);
//
//			String serviceName = "ns-zdb-01-single-pub";
//			String chartVersion = "4.2.0";
//			String component = "master";
//			String namespace = "ns-zdb-01";
//
//			temp = temp.replace("${component}", component);
//			temp = temp.replace("${chartVersion}", chartVersion);
//			temp = temp.replace("${serviceName}", serviceName);
//			temp = temp.replace("${namespace}", namespace);
//
//			System.out.println(temp);
//
//			InputStream is = new ByteArrayInputStream(temp.getBytes(StandardCharsets.UTF_8));
//			Service ss = K8SUtil.kubernetesClient().services().inNamespace(namespace).load(is).get();
//			Service newService = K8SUtil.kubernetesClient().services().inNamespace(namespace).createOrReplace(ss);
//		}
		{
			File f = new File("/Users/a06919/github/zdb-portal-server/src/main/resources/redis/create_public_svc.template");
			String temp = readFile(f);

			String serviceName = "ns-zdb-01-ha-pub";
			String chartVersion = "3.6.5";
			String component = "master";
			String namespace = "ns-zdb-01";

			temp = temp.replace("${role}", component);
			temp = temp.replace("${chartVersion}", chartVersion);
			temp = temp.replace("${serviceName}", serviceName);

			System.out.println(temp);

			InputStream is = new ByteArrayInputStream(temp.getBytes(StandardCharsets.UTF_8));
			Service ss = K8SUtil.kubernetesClient().services().inNamespace(namespace).load(is).get();
			Service newService = K8SUtil.kubernetesClient().services().inNamespace(namespace).createOrReplace(ss);
		}
	}

	private static String readFile(File file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = null;
		StringBuilder stringBuilder = new StringBuilder();

		try {
			while ((line = reader.readLine()) != null) {


				stringBuilder.append(line).append("\n");
			}

			return stringBuilder.toString();
		} finally {
			reader.close();
		}
	}
}