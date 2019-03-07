package com.zdb.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
//import org.apache.http.client.HttpClient;
//import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
//import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
//import org.apache.http.impl.client.HttpClients;
//import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.Handler;
import io.fabric8.kubernetes.api.model.Lifecycle;

public class LabelUpdateExample {

	private static final Logger logger = LoggerFactory.getLogger(LabelUpdateExample.class);

	public static void main(String[] args) throws Exception {
		addlifecycle();
	}
	public static void main3(String[] args) {
		try {			
			// pod 재사작 발생함.
//			K8SUtil.kubernetesClient().inNamespace("zdb-test2").apps().statefulSets().withName("zdb-test2-mha-mariadb-master").edit()
//            .editMetadata()
//              .addToLabels("zdb-failover-enable", "true")
//            .endMetadata()
//            .done();
			
			
			// pod 재시작 없이 반영.
//			cat > patch.json <<EOF
//			[ 
//			 { 
//			 "op": "add", "path": "/metadata/labels/zdb-failover-enable", "value": "true" 
//			 } 
//			]
//			EOF
//			curl --request PATCH --insecure \
//			--data "$(cat patch.json)" \
//			--header "Authorization: Bearer $KUBE_TOKEN"  \
//			-H "Content-Type:application/json-patch+json" \
//			https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_PORT_443_TCP_PORT/apis/apps/v1/namespaces/zdb-test2/statefulsets/zdb-test2-ha-2-mariadb-master

			
//			StringBuffer sb = new StringBuffer();
//			
//			sb.append("[ ");
//			sb.append(" { ");
//			sb.append(" \"op\": \"add\", \"path\": \"/metadata/labels/zdb-failover-enable\", \"value\": \"false\" ");
//			sb.append(" } ");
//			sb.append("]");
//			sb.append("");
			
			RestTemplate rest = getRestTemplate();

			String idToken = System.getProperty("token");
			String masterUrl = System.getProperty("masterUrl");

			String namespace = "zdb-test2";
			String name = "zdb-test2-ha-2-mariadb-master";

			HttpHeaders headers = new HttpHeaders();
			List<MediaType> mediaTypeList = new ArrayList<MediaType>();
			mediaTypeList.add(MediaType.APPLICATION_JSON);
			headers.setAccept(mediaTypeList);
			headers.add("Authorization", "Bearer " + idToken);
			headers.set("Content-Type", "application/json-patch+json");
			
//			LabelVo label = new LabelVo("add", "/metadata/labels/zdb-failover-enable", "false");
//			Gson gson = new GsonBuilder().create();
//			String json = gson.toJson(new LabelVo[] {label});
			
			boolean enable = true;
			
			String data = "[{\"op\":\"add\",\"path\":\"/metadata/labels/zdb-failover-enable\",\"value\":\""+enable+"\"}]";
		    
			HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

			String endpoint = masterUrl + "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}";
			ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, name);

			if (response.getStatusCode() == HttpStatus.OK) {
				String body = response.getBody();
				System.out.println(body);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void addlifecycle() throws Exception {
		RestTemplate rest = K8SUtil.getRestTemplate();
		String idToken = "";
		String masterUrl = "";//K8SUtil.getMasterURL();

		HttpHeaders headers = new HttpHeaders();
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		headers.setAccept(mediaTypeList);
		headers.add("Authorization", "Bearer " + idToken);
		headers.set("Content-Type", "application/json-patch+json");
		
		
		
			
//		String data = "	\"lifecycle\": { "+
//              "\"postStart\": {"+
//              "  \"exec\": {"+
//              "    \"command\": ["+
//              "      \"/bin/sh\","+
//              "      \"-c\","+
//              "      \"/usr/bin/nohup /report_status.sh 1\u003e/tmp/report.log 2\u003e/dev/null \u0026\""+
//              "    ]"+
//              "  }"+
//              "}"+
//              "}";
		 
		String[] command = new String[] {
				"/bin/sh",
				"-c",
				"/usr/bin/nohup /report_status.sh 1>/tmp/report.log 2>/dev/null &"
		};
		
		
		Lifecycle lifecycle = new Lifecycle();
		
		Handler postStart = new Handler();
		ExecAction exec = new ExecAction();
		exec.setCommand(Arrays.asList(command));
		postStart.setExec(exec);
		lifecycle.setPostStart(postStart);
		
		Container con = new Container();
		con.setLifecycle(lifecycle);
		
		Gson gson = new Gson();
		String lc = gson.toJson(lifecycle, Lifecycle.class);
		
		String l = "\"lifecycle\": {\"postStart\":{\"exec\":{\"command\":[\"/bin/sh\",\"-c\",\"/usr/bin/nohup /report_status.sh 1\u003e/tmp/report.log 2\u003e/dev/null \u0026\"]}}}";
		
		String data = "[{\"op\":\"add\",\"path\":\"/spec/template/spec/containers[0]\",\"value\":\""+l+"\"}]";
		 
//		String data = "[{\"op\":\"add\",\"path\":\"/metadata/labels/zdb-failover-enable\",\"value\":\""+false+"\"}]";
		
		HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

		//PUT                           /apis/apps/v1/namespaces/{namespace}/statefulsets/{name}
		String endpoint = masterUrl + "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}";
		ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, "zdb-test", "zdb-test-pns-mariadb-master");

		
//		StatefulSet statefulSet = new StatefulSet();
//		Container container = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0);
		
		
//		String enableStr = enable ? "On" : "Off";
//		String message = "Auto Failover 설정 변경. ["+enableStr+"]";

		if (response.getStatusCode() == HttpStatus.OK) {
			System.out.println(response.getBody());
		} else {
		}
	}
	
	public static void curl() throws Exception {
//		curl --request GET --insecure \
//		--header "Authorization: Bearer $KUBE_TOKEN"  \
//		https://169.56.69.242:20292/apis/apps/v1/namespaces/zdb-test2/statefulsets/zdb-test2-ha-2-mariadb-master
		
		String idToken = System.getProperty("token");
		String masterUrl = System.getProperty("masterUrl");
		
		String namespace = "zdb-test2";
		String name = "zdb-test2-ha-2-mariadb-master";

		
		HttpHeaders requestHeaders = new HttpHeaders();
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(mediaTypeList);
		requestHeaders.add("Authorization", "Bearer " + idToken);
		requestHeaders.set("Content-Type", "application/json-patch+json");

		HttpEntity<?> requestEntity = new HttpEntity<Object>(requestHeaders);

		RestTemplate rest = getRestTemplate();
		
		String endpoint = "https://169.56.69.242:20292/apis/apps/v1/namespaces/zdb-test2/statefulsets/zdb-test2-ha-2-mariadb-master";
		ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class);

		if (response.getStatusCode() == HttpStatus.OK) {
			String body = response.getBody();
			System.out.println(body);
		}
	}

	public static void curl_status() throws Exception {
//		curl --request GET --insecure \
//		--header "Authorization: Bearer $KUBE_TOKEN"  \
//		https://169.56.69.242:20292/apis/apps/v1/namespaces/zdb-test2/statefulsets/zdb-test2-ha-2-mariadb-master
		
		long s = System.currentTimeMillis();
		
		String idToken = System.getProperty("token");
		String masterUrl = System.getProperty("masterUrl");
		
		String namespace = "zdb-ha";
		String name = "zdb-ha-failover-mariadb-master-0";
		
		HttpHeaders requestHeaders = new HttpHeaders();
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(mediaTypeList);
		requestHeaders.add("Authorization", "Bearer " + idToken);
		requestHeaders.set("Content-Type", "application/json-patch+json");
		
		HttpEntity<?> requestEntity = new HttpEntity<Object>(requestHeaders);
		
		RestTemplate rest = getRestTemplate();
		///api/v1/namespaces/{namespace}/pods/{name}/status
		String endpoint = "https://169.56.69.242:20292/api/v1/namespaces/{namespace}/pods/{name}/status";
		ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.GET, requestEntity, String.class, namespace, name);

		if (response.getStatusCode() == HttpStatus.OK) {
			String body = response.getBody();
			System.out.println(body);
		}
		
		System.out.println((System.currentTimeMillis() -s));
	}
	
	
	public static RestTemplate getRestTemplate() throws Exception {
		TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}
		} };

		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(null, trustManagers, new SecureRandom());

		SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
		CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setHttpClient(httpClient);
		requestFactory.setConnectionRequestTimeout(1000 * 20);
		requestFactory.setConnectTimeout(1000 * 30);
		requestFactory.setReadTimeout(1000 * 30);

		RestTemplate restTemplate = new RestTemplate(requestFactory);

		return restTemplate;
	}
}