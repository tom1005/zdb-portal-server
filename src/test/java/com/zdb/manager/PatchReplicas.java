package com.zdb.manager;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.zdb.core.util.K8SUtil;

public class PatchReplicas {

	public static void main(String[] args) {
		try {
			
			RestTemplate rest = K8SUtil.getRestTemplate();
			String idToken = System.getProperty("token");
			String masterUrl = System.getProperty("masterUrl");
			
			String namespace = "zdb-ha";
			String name = "zdb-ha-test2-mariadb-master";
			
			
			HttpHeaders headers = new HttpHeaders();
			headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
			headers.set("Authorization", "Bearer " + idToken);
			headers.set("Content-Type", "application/json-patch+json");
			
//		    { "spec": { "replicas": 0 } }
			
			String data = "[{\"op\":\"add\",\"path\":\"/spec/replicas\",\"value\":1}]";
//			String data = "{\"spec\":{\"replicas\":1}";
		    
			HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

			String endpoint = masterUrl + "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}/scale";
			ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, name);
			
			if (response.getStatusCode() == HttpStatus.OK) {
				String body = response.getBody();
				System.out.println(body);
			} else {
				System.err.println("HttpStatus ; " + response.getStatusCode());
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
