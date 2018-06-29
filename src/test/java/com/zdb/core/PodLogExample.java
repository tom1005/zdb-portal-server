package com.zdb.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

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
		try {
			String log = K8SUtil.kubernetesClient().pods().inNamespace(namespace).withName(podName).inContainer("lwk-namyu7-redis").tailingLines(100).getLog();
			System.out.println(log);
			System.out.println("----------------------------------------------------------------");

			String replaceAll = log.replaceAll(" \\[\\dm| \\[[\\d]{2}[;][\\d][;][\\d]m", "");
			System.out.println(replaceAll);

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
}