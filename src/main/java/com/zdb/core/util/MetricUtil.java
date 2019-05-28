package com.zdb.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.crsh.console.jline.internal.Log;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.vo.PodMetrics;

import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;

/**
 * 에러로그, slowlog 조회 및 다운로드 
 * @author a06919
 *
 */
@Slf4j
public class MetricUtil {
	
	public static void main(String[] args) throws Exception {
		// curl GET http://heapster.kube-system/api/v1/model/namespaces/zdb-system/pod-list/zdb-system-zdb-mariadb-master-0/metrics/memory-usage

		Object result = new MetricUtil().getMemoryUsage("zdb-test", "zdb-test-qq-mariadb-0");

		System.out.println(result);
		
		result = new MetricUtil().getCPUUsage("zdb-test", "zdb-test-qq-mariadb-0");

		System.out.println(result);
		
	}

	StringBuffer sb = new StringBuffer();
	
	public  Object getMemoryUsage(String namespace, String podName) throws InterruptedException, IOException, Exception {
		try {
			String result = getMetric(namespace, podName, "memory-usage");

			Gson gson = new GsonBuilder().create();
			java.util.Map<String, Object> object = gson.fromJson(result, java.util.Map.class);
			return ((Map) ((List) object.get("items")).get(0)).get("metrics");
		} catch (Exception e) {
		}
		
		return null;
	}
	
	public Object getCPUUsage(String namespace, String podName) throws InterruptedException, IOException, Exception {
		try {
			String result = getMetric(namespace, podName, "cpu-usage");

			Gson gson = new GsonBuilder().create();
			java.util.Map<String, Object> object = gson.fromJson(result, java.util.Map.class);
			return ((Map) ((List) object.get("items")).get(0)).get("metrics");
		} catch (Exception e) {
			
		}
		
		return null;
	}
	
	public synchronized String getMetric(String namespace, String podName, String metric) throws InterruptedException, IOException, Exception {
		String[] commands = new String[] { "sh", "-c", "curl GET http://heapster.kube-system/api/v1/model/namespaces/"+namespace+"/pod-list/"+podName+"/metrics/" + metric };

		BufferedReader input = null;
		StringBuffer result = new StringBuffer();
		try{
			ProcessBuilder builder = new ProcessBuilder(commands);
			Process p = builder.start();

			input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String l = "";
			while ((l = input.readLine()) != null) {
				result.append(l).append("\n");
			}
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		} finally {
			if(input != null) {
				input.close();
			}
		}
		
		if (System.getProperty("token") != null) {
			Pod pod = K8SUtil.kubernetesClient().inNamespace("zdb-system").pods().withLabel("app", "zdb-portal-ui").list().getItems().get(0);
			result.append(exec(pod.getMetadata().getNamespace(), pod.getMetadata().getName(), commands));
		}
		
		//{"items":[{"metrics":[{"timestamp":"2019-03-19T11:26:00Z","value":2},{"timestamp":"2019-03-19T11:27:00Z","value":2},{"timestamp":"2019-03-19T11:28:00Z","value":2},{"timestamp":"2019-03-19T11:29:00Z","value":2},{"timestamp":"2019-03-19T11:30:00Z","value":2},{"timestamp":"2019-03-19T11:31:00Z","value":2},{"timestamp":"2019-03-19T11:32:00Z","value":2},{"timestamp":"2019-03-19T11:33:00Z","value":2},{"timestamp":"2019-03-19T11:34:00Z","value":2},{"timestamp":"2019-03-19T11:35:00Z","value":2},{"timestamp":"2019-03-19T11:36:00Z","value":2},{"timestamp":"2019-03-19T11:37:00Z","value":2},{"timestamp":"2019-03-19T11:38:00Z","value":2},{"timestamp":"2019-03-19T11:39:00Z","value":3},{"timestamp":"2019-03-19T11:40:00Z","value":2}],"latestTimestamp":"2019-03-19T11:40:00Z"}]}
		return result.toString().trim();
	}
	
	public synchronized String exec(String namespace, String podName, String[] commands) throws InterruptedException, IOException, Exception {
		return new ExecUtil().exec(K8SUtil.kubernetesClient(), namespace, podName, "zdb-portal-ui", commands[2]);
	}
	
	public PodMetrics getMetricFromMetricServer(String namespace, String podname) throws Exception {
		RestTemplate rest = K8SUtil.getRestTemplate();
		
		String idToken = K8SUtil.getToken();
		String masterUrl = K8SUtil.getMasterURL();
		
		HttpHeaders headers = new HttpHeaders();
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		headers.setAccept(mediaTypeList);
		headers.add("Authorization", "Bearer " + idToken);
		headers.set("Content-Type", "application/json-patch+json");

		HttpEntity<String> requestEntity = new HttpEntity<>(headers);

		String endpoint = masterUrl + "/apis/metrics.k8s.io/v1beta1/namespaces/{namespace}/pods/{name}";

		try {
			ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.GET, requestEntity, String.class, namespace, podname);
			if(response.getStatusCode().value() >= 200 && response.getStatusCode().value() < 400) {
				String body = response.getBody();
				if(body != null) {
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					PodMetrics podMetrics = gson.fromJson(body, PodMetrics.class);
					
					return podMetrics;
				}
			}
		} catch (HttpClientErrorException e) {
			log.warn("{} : {} > {} 리소스 사용량 조회 실패", e.getMessage(), namespace, podname);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return null;
	}
	
	public PodMetrics getMetricFromHeapster(String namespace, String podname) throws Exception {
		RestTemplate rest = K8SUtil.getRestTemplate();
		
		String idToken = K8SUtil.getToken();
		String masterUrl = K8SUtil.getMasterURL();
		
		HttpHeaders headers = new HttpHeaders();
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		headers.setAccept(mediaTypeList);
		headers.add("Authorization", "Bearer " + idToken);
		headers.set("Content-Type", "application/json-patch+json");
		
		HttpEntity<String> requestEntity = new HttpEntity<>(headers);
		String endpoint = masterUrl + "/api/v1/namespaces/kube-system/services/http:heapster:/proxy/apis/metrics/v1alpha1/namespaces/{namespace}/pods/{name}?labelSelector=";
		
		try {
			ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.GET, requestEntity, String.class, namespace, podname);
			if(response.getStatusCode().value() >= 200 && response.getStatusCode().value() < 400) {
				String body = response.getBody();
				if(body != null) {
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					PodMetrics podMetrics = gson.fromJson(body, PodMetrics.class);
					
					return podMetrics;
				}
			}
		} catch (HttpClientErrorException e) {
			log.warn("{} : {} > {} 리소스 사용량 조회 실패", e.getMessage(), namespace, podname);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return null;
	}
}
