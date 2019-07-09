package com.zdb.snippet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class ApplyMycnf {

	public static void main(String[] args) throws Exception {
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		
		ConfigMap cm = new ConfigMap();
		ObjectMeta metadata = new ObjectMeta();
		metadata.setName("fsk-db-316-mariadb-master");
		metadata.setNamespace("fsk-db");
		
		Map<String, String> map = new HashMap<>();
		map.put("app", "mariadb");
		map.put("chart", "mariadb-4.2.4");
		map.put("component", "master");
		map.put("release", "fsk-db-316");
		metadata.setLabels(map);
		cm.setMetadata(metadata);
		
		
		Map<String, String> data = new HashMap<>();
		
		URL systemResource = ClassLoader.getSystemResource("configmap.cm");
		
		String readFile = readFile(new File(systemResource.getFile()));
		
		data.put("my.cnf", readFile);
		
		cm.setData(data);
		

		client.inNamespace("fsk-db").configMaps().createOrReplace(cm);
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
