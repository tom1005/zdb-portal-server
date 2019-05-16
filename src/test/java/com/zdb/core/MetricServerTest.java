package com.zdb.core;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.vo.PodMetrics;

public class MetricServerTest {

	public static void main(String[] args) {
		try {
//			new MetricServerTest().doMetricServerTest();
			new MetricServerTest().getMetricFromHeapster();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public PodMetrics getMetricFromMetricServer() throws Exception {

		long s = System.currentTimeMillis();

		String namespace = "zcp-system";
		String podname = "alertmanager-5bb489cf4d-lm46h";

		RestTemplate rest = K8SUtil.getRestTemplate();
		String idToken = "";
		String masterUrl = "";

		HttpHeaders headers = new HttpHeaders();
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		headers.setAccept(mediaTypeList);
		headers.add("Authorization", "Bearer " + idToken);
		headers.set("Content-Type", "application/json-patch+json");

		HttpEntity<String> requestEntity = new HttpEntity<>(headers);

		String endpoint = masterUrl + "/apis/metrics.k8s.io/v1beta1/namespaces/{namespace}/pods/{name}";
		ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.GET, requestEntity, String.class, namespace, podname);

		String body = response.getBody();
		System.out.println(body);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		PodMetrics podMetrics = gson.fromJson(body, PodMetrics.class);

//		System.out.println(podMetrics.getContainers().get(0).getUsage().getCpu());
//		System.out.println(podMetrics.getContainers().get(0).getUsage().getMemory());
		
		return podMetrics;
	}
	
	public PodMetrics getMetricFromHeapster() throws Exception {
		
		long s = System.currentTimeMillis();
		
		String namespace = "zdb-system";
		String podname = "zdb-portal-db-mariadb-0";
//		String podname = "zdb-portal-server-deployment-67d8d6cd7d-jzsh8";
		
		RestTemplate rest = K8SUtil.getRestTemplate();
		String idToken = "";
		String masterUrl = "";
		
		HttpHeaders headers = new HttpHeaders();
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		headers.setAccept(mediaTypeList);
		headers.add("Authorization", "Bearer " + idToken);
		headers.set("Content-Type", "application/json-patch+json");
		
		HttpEntity<String> requestEntity = new HttpEntity<>(headers);
		String endpoint = masterUrl + "/api/v1/namespaces/kube-system/services/http:heapster:/proxy/apis/metrics/v1alpha1/namespaces/{namespace}/pods/{name}?labelSelector=";
		ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.GET, requestEntity, String.class, namespace, podname);
		
		String body = response.getBody();
		System.out.println(body);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		PodMetrics podMetrics = gson.fromJson(body, PodMetrics.class);
		
//		System.out.println(podMetrics.getContainers().get(0).getUsage().getCpu());
//		System.out.println(podMetrics.getContainers().get(0).getUsage().getMemory());
//		System.out.println(podMetrics.getContainers().get(1).getUsage().getCpu());
//		System.out.println(podMetrics.getContainers().get(1).getUsage().getMemory());
		
		return podMetrics;
	}
}
