package com.zdb.core;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

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

import io.fabric8.kubernetes.api.model.NodeSelector;
import io.fabric8.kubernetes.api.model.NodeSelectorRequirement;
import io.fabric8.kubernetes.api.model.NodeSelectorTerm;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class WorkerPoolMgmt {
	
	
	public static void main(String[] args) throws Exception {
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		
//		spec:
//			  template:
//			    spec:
//			      affinity:
//			        nodeAffinity:
//			          requiredDuringSchedulingIgnoredDuringExecution:
//			            nodeSelectorTerms:
//			            - matchExpressions:
//			              - key: worker-pool
//			                operator: In
//			                values:
//			                - prod
			            
		StatefulSet statefulSet = client.inNamespace("infra-test").apps().statefulSets().withName("infra-test-ksh-mariadb").get();
		NodeSelector requiredDuringSchedulingIgnoredDuringExecution = statefulSet.getSpec().getTemplate().getSpec().getAffinity().getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution();
		List<NodeSelectorTerm> nodeSelectorTerms = requiredDuringSchedulingIgnoredDuringExecution.getNodeSelectorTerms();
		for(NodeSelectorTerm nst : nodeSelectorTerms) {
			List<NodeSelectorRequirement> matchExpressions = nst.getMatchExpressions();
			
			for(NodeSelectorRequirement nsr : matchExpressions) {
				String key = nsr.getKey();
				List<String> values = nsr.getValues();
				
				for(String value : values) {
					System.out.println(key +" : "+ value);
				}
			}
		}
		String namespace = "infra-test";
		String sts_name = "infra-test-ksh-mariadb";
		update(namespace, sts_name, "dev");
	}
	
//	➜  ~ kubectl -n infra-test patch sts infra-test-ksh-mariadb --type merge --patch "$(cat ~/infra-test-nodeaffinity.yaml)" -v8
//	Config loaded from file /Users/a06919/.bluemix/plugins/container-service/clusters/zcp-cbt-admin/kube-config-seo01-zcp-cbt.yml
//	GET https://c4.seo01.containers.cloud.ibm.com:20292/apis/apps/v1/namespaces/infra-test/statefulsets/infra-test-ksh-mariadb
//	Request Headers:
//	    Accept: application/json
//	    User-Agent: kubectl/v1.14.3 (darwin/amd64) kubernetes/5e53fd6
//	Response Status: 200 OK in 195 milliseconds
//	Response Headers:
//	    Content-Type: application/json
//	    Date: Wed, 24 Jul 2019 05:45:53 GMT
//	Response Body: {"kind":"StatefulSet","apiVersion":"apps/v1","metadata":{"name":"infra-test-ksh-mariadb","namespace":"infra-test","selfLink":"/apis/apps/v1/namespaces/infra-test/statefulsets/infra-test-ksh-mariadb","uid":"abd86086-a39c-11e9-b4f4-8ea97ade1c35","resourceVersion":"65059041","generation":2,"creationTimestamp":"2019-07-11T05:28:10Z","labels":{"app":"mariadb","chart":"mariadb-6.5.2","component":"master","heritage":"Tiller","release":"infra-test-ksh"}},"spec":{"replicas":0,"selector":{"matchLabels":{"app":"mariadb","component":"master","release":"infra-test-ksh"}},"template":{"metadata":{"creationTimestamp":null,"labels":{"app":"mariadb","chart":"mariadb-6.5.2","component":"master","release":"infra-test-ksh"}},"spec":{"volumes":[{"name":"config","configMap":{"name":"infra-test-ksh-mariadb","defaultMode":420}}],"initContainers":[{"name":"init-volume","image":"registry.au-syd.bluemix.net/cloudzdb/mariadb:10.3.16","command":["sh","-c","mkdir -p /bitnami/mariadb/logs \u0026\u0026 chown -R 1001:1001 /bitnami/mariadb/log [truncated 3597 chars]
//	Request Body: {"spec":{"template":{"spec":{"affinity":{"nodeAffinity":{"requiredDuringSchedulingIgnoredDuringExecution":{"nodeSelectorTerms":[{"matchExpressions":[{"key":"worker-pool","operator":"In","values":["dev"]}]}]}}}}}}}
//	PATCH https://c4.seo01.containers.cloud.ibm.com:20292/apis/apps/v1/namespaces/infra-test/statefulsets/infra-test-ksh-mariadb
//	Request Headers:
//	    Accept: application/json
//	    Content-Type: application/merge-patch+json
//	    User-Agent: kubectl/v1.14.3 (darwin/amd64) kubernetes/5e53fd6
//	Response Status: 200 OK in 54 milliseconds
//	Response Headers:
//	    Content-Type: application/json
//	    Date: Wed, 24 Jul 2019 05:45:53 GMT
//	Response Body: {"kind":"StatefulSet","apiVersion":"apps/v1","metadata":{"name":"infra-test-ksh-mariadb","namespace":"infra-test","selfLink":"/apis/apps/v1/namespaces/infra-test/statefulsets/infra-test-ksh-mariadb","uid":"abd86086-a39c-11e9-b4f4-8ea97ade1c35","resourceVersion":"70539048","generation":3,"creationTimestamp":"2019-07-11T05:28:10Z","labels":{"app":"mariadb","chart":"mariadb-6.5.2","component":"master","heritage":"Tiller","release":"infra-test-ksh"}},"spec":{"replicas":0,"selector":{"matchLabels":{"app":"mariadb","component":"master","release":"infra-test-ksh"}},"template":{"metadata":{"creationTimestamp":null,"labels":{"app":"mariadb","chart":"mariadb-6.5.2","component":"master","release":"infra-test-ksh"}},"spec":{"volumes":[{"name":"config","configMap":{"name":"infra-test-ksh-mariadb","defaultMode":420}}],"initContainers":[{"name":"init-volume","image":"registry.au-syd.bluemix.net/cloudzdb/mariadb:10.3.16","command":["sh","-c","mkdir -p /bitnami/mariadb/logs \u0026\u0026 chown -R 1001:1001 /bitnami/mariadb/log [truncated 3596 chars]
//	statefulset.apps/infra-test-ksh-mariadb patched
	
	private static void update(String namespace, String sts_name, String wp) {
		try {
			RestTemplate rest = K8SUtil.getRestTemplate();
			String idToken = K8SUtil.getToken();
			String masterUrl = K8SUtil.getMasterURL();
			
			HttpHeaders headers = new HttpHeaders();
			List<MediaType> mediaTypeList = new ArrayList<MediaType>();
			mediaTypeList.add(MediaType.APPLICATION_JSON);
			headers.setAccept(mediaTypeList);
			headers.add("Authorization", "Bearer " + idToken);
			headers.set("Content-Type", "application/merge-patch+json");
			
			// {"spec":{"template":{"spec":{"affinity":{"nodeAffinity":{"requiredDuringSchedulingIgnoredDuringExecution":{"nodeSelectorTerms":[{"matchExpressions":[{"key":"worker-pool","operator":"In","values":["prod"]}]}]}}}}}}}
			String data = "{\"spec\":{\"template\":{\"spec\":{\"affinity\":{\"nodeAffinity\":{\"requiredDuringSchedulingIgnoredDuringExecution\":{\"nodeSelectorTerms\":[{\"matchExpressions\":[{\"key\":\"worker-pool\",\"operator\":\"In\",\"values\":[\""+wp+"\"]}]}]}}}}}}}";
			
			HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);
			

			
			String endpoint = masterUrl + "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}";
			ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, sts_name);
			
			if (response.getStatusCode() == HttpStatus.OK) {
				System.err.println(response.getBody());
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
