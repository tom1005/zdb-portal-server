package com.zdb.snippet;

import java.util.Map;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class DownloadMycnf {

	public static void main(String[] args) throws Exception {
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		ConfigMap configMap = client.inNamespace("fsk-db").configMaps().withName("fsk-db-316-mariadb-master").get();
		
		Map<String, String> data = configMap.getData();
		
		String mycnf = data.get("my.cnf");

		System.out.println(mycnf);
	}
	

}
