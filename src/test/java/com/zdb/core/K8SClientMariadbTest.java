package com.zdb.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.gson.GsonBuilder;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Pod;

public class K8SClientMariadbTest {
	
//	@Test
//	public void testServiceOverview() throws Exception {
//
//		// config map
//		// persistent volume claims
//		// deployments
//		// pods
//		// secrets
//		// services
//		// stateful sets
//		
//		// release : zdb-maria-ha2
//		
//		long s = System.currentTimeMillis();
//		
//		
//		String serviceName = "zdb-maria-ha2";
//		
//		List<Object> overView = new ArrayList<Object>();
//		
//		List<ConfigMap> configMaps = K8SUtil.kubernetesClient().inNamespace("zdb-maria").configMaps().list().getItems();
//		for (ConfigMap obj : configMaps) {
//			Map<String, String> labels = obj.getMetadata().getLabels();
//			if (labels != null) {
//				String release = labels.get("release");
//
//				if (serviceName.equals(release)) {
//					overView.add(obj);
//				}
//			}
//		}
//		
//		List<PersistentVolumeClaim> persistentVolumeClaims = K8SUtil.kubernetesClient().inNamespace("zdb-maria").persistentVolumeClaims().list().getItems();
//		for(PersistentVolumeClaim obj : persistentVolumeClaims) {
//			Map<String, String> labels = obj.getMetadata().getLabels();
//			if (labels != null) {
//				String release = labels.get("release");
//
//				if (serviceName.equals(release)) {
//					overView.add(obj);
//				}
//			}
//		}
//		
//		List<Pod> pods = K8SUtil.kubernetesClient().inNamespace("zdb-maria").pods().list().getItems();
//		for(Pod obj : pods) {
//			Map<String, String> labels = obj.getMetadata().getLabels();
//			if (labels != null) {
//				String release = labels.get("release");
//
//				if (serviceName.equals(release)) {
//					overView.add(obj);
//				}
//			}
//		}
//		
//		List<Secret> secrets = K8SUtil.kubernetesClient().inNamespace("zdb-maria").secrets().list().getItems();
//		for(Secret obj : secrets) {
//			Map<String, String> labels = obj.getMetadata().getLabels();
//			if (labels != null) {
//				String release = labels.get("release");
//
//				if (serviceName.equals(release)) {
//					overView.add(obj);
//				}
//			}
//		}
//		
//		List<Service> services = K8SUtil.kubernetesClient().inNamespace("zdb-maria").services().list().getItems();
//		for(Service obj : services) {
//			Map<String, String> labels = obj.getMetadata().getLabels();
//			if (labels != null) {
//				String release = labels.get("release");
//
//				if (serviceName.equals(release)) {
//					overView.add(obj);
//				}
//			}
//		}
//		
//		List<StatefulSet> statefulSets = K8SUtil.kubernetesClient().inNamespace("zdb-maria").apps().statefulSets().list().getItems();
//		for(StatefulSet obj : statefulSets) {
//			Map<String, String> labels = obj.getMetadata().getLabels();
//			if (labels != null) {
//				String release = labels.get("release");
//
//				if (serviceName.equals(release)) {
//					overView.add(obj);
//				}
//			}
//		}
//		
//		List<Deployment> deployments = K8SUtil.kubernetesClient().inNamespace("zdb-maria").extensions().deployments().list().getItems();
//
//		for (Deployment obj : deployments) {
//			Map<String, String> labels = obj.getMetadata().getLabels();
//			if (labels != null) {
//				String release = labels.get("release");
//
//				if (serviceName.equals(release)) {
//					overView.add(obj);
//				}
//			}
//		}
//		
//		for (Object obj : overView) {
//			HasMetadata data = (HasMetadata) obj;
//			String kind = data.getKind();
//			String name = data.getMetadata().getName();
//			
//			System.out.println(String.format("%s %s", kind, name));
//		}
//		
//		System.out.println(">>> "+(System.currentTimeMillis() - s) );
//		System.out.println();
//		System.out.println();
//		
//	}

//	@Test
	public void testPod() throws Exception {

		List<Pod> items = K8SUtil.kubernetesClient().inAnyNamespace().pods().list().getItems();

		Map<String, String> releaseMap = new HashMap<>();
		
		List<String> apps = new ArrayList<>();
		ZDBType[] values = ZDBType.values();
		for(ZDBType type : values) {
			apps.add(type.getName().toLowerCase());
		}
		
		
		for (Pod pod : items) {
			// System.out.println(pod.getMetadata().getName() +"\t"+pod);
			String release = pod.getMetadata().getLabels().get("release");
			
			
			if (release != null) {
				String app = pod.getMetadata().getLabels().get("app");
				
				if (apps.contains(app.toLowerCase())) {
					releaseMap.put(release, app);
//					System.out.println(app + "\t" + pod.getMetadata().getName() + "\t" + release);
				}
			}

		}
		
		for (String release : releaseMap.keySet()) {
			System.out.println(release + "\t" + releaseMap.get(release));
		}

		System.out.println();
		System.out.println();

		// DefaultKubernetesClient client = kubernetesClient();
		//
		// List<Pod> podList = new ArrayList<>();
		//
		// List<Pod> items = client.inNamespace(namespace).pods().list().getItems();
		// for (Pod pod : items) {
		// if (serviceName.equals(pod.getMetadata().getLabels().get("release"))) {
		// podList.add(pod);
		// }
		// }

	}
//	
//	@Test
//	public void testStatefulSets() throws Exception {
//
//		List<StatefulSet> items = K8SUtil.kubernetesClient().apps().statefulSets().list().getItems();
//
//		for(StatefulSet event : items) {
//			System.out.println(event.getMetadata().getName() +"\t"+event);
//			
//			
//		}
//		
//		System.out.println();
//	}
//	
	
//	{
//		  "apiVersion": "v1",
//		  "count": 1,
//		  "firstTimestamp": "2018-05-28T11:59:57Z",
//		  "involvedObject": {
//		    "apiVersion": "v1",
//		    "kind": "Pod",
//		    "name": "maria-test777-mariadb-0",
//		    "namespace": "zdb-maria",
//		    "resourceVersion": "1358311",
//		    "uid": "5ad38c65-626e-11e8-bddb-ea6741069087",
//		    "additionalProperties": {}
//		  },
//		  "kind": "Event",
//		  "lastTimestamp": "2018-05-28T11:59:57Z",
//		  "message": "MountVolume.SetUp succeeded for volume \"config\" ",
//		  "metadata": {
//		    "creationTimestamp": "2018-05-28T11:59:57Z",
//		    "finalizers": [],
//		    "name": "maria-test777-mariadb-0.1532cd288fc4ce99",
//		    "namespace": "zdb-maria",
//		    "ownerReferences": [],
//		    "resourceVersion": "1358314",
//		    "selfLink": "/api/v1/namespaces/zdb-maria/events/maria-test777-mariadb-0.1532cd288fc4ce99",
//		    "uid": "a44dd773-626e-11e8-bddb-ea6741069087",
//		    "additionalProperties": {}
//		  },
//		  "reason": "SuccessfulMountVolume",
//		  "source": {
//		    "component": "kubelet",
//		    "host": "10.178.218.170",
//		    "additionalProperties": {}
//		  },
//		  "type": "Normal",
//		  "additionalProperties": {
//		    "reportingInstance": "",
//		    "reportingComponent": ""
//		  }
//		}
	@Test
	public void testEvent() throws Exception {

		List<Event> items = K8SUtil.kubernetesClient().events().list().getItems();

		for(Event event : items) {
			
//			System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(event));
			ObjectReference involvedObject = event.getInvolvedObject();
			
			System.out.println(involvedObject.getKind() );
//			System.out.println("=============================================================");
//			System.out.println();
			
			if(involvedObject.getKind().equals("Pod") && involvedObject.getName().equals("maria-test777-mariadb-0")) {
//				System.out.println(involvedObject.getKind() +"\t"+ event.getFirstTimestamp()+"\t"+ event.getLastTimestamp()+"\t"+ event.getMessage() +" \t "+involvedObject);
//				zdb-maria-ha2-mariadb-slave-0
			}
		}
		
		System.out.println();
	}
//	
//	@Test
//	public void testReplicaSets() throws Exception {
//
//		List<ReplicaSet> items = K8SUtil.kubernetesClient().inNamespace("zdb-maria").extensions().replicaSets().list().getItems();
//
//		for (ReplicaSet rs : items) {
//				System.out.println(rs.getMetadata().getName() +"\t"+rs);
//		}
//		
//		System.out.println();
//		System.out.println();
//	}
//	
//	@Test
//	public void testDeployments() throws Exception {
//
//		List<Deployment> items = K8SUtil.kubernetesClient().inNamespace("zdb-maria").extensions().deployments().list().getItems();
//
//		for (Deployment rs : items) {
//				System.out.println(rs.getMetadata().getName() +"\t"+rs);
//		}
//		
//		
//		System.out.println();
//		System.out.println();
//	}
//	
//	@Test
//	public void testServices() throws Exception {
//
//		List<Service> items = K8SUtil.kubernetesClient().inNamespace("zdb-maria").services().list().getItems();
//
//		for (Service rs : items) {
//				System.out.println(rs.getMetadata().getName() +"\t"+rs);
//		}
//	}
//	
//	@Test
//	public void testPersistentVolume() throws Exception {
//
//		 List<PersistentVolume> items = K8SUtil.kubernetesClient().persistentVolumes().list().getItems();
//
//		for (PersistentVolume pv : items) {
//			Gson gson = new GsonBuilder().setPrettyPrinting().create();
//				System.out.println(pv.getMetadata().getName() +"\t"+gson.toJson(pv));
//				
//				
//		}
//	}
//	
//	@Test
//	public void testActivePodList() throws Exception {
//
//		List<Pod> activePodList = K8SUtil.getActivePodList("zdb-maria", "mariadb89-test");
//
//		for (Pod pod : activePodList) {
//			Gson gson = new GsonBuilder().setPrettyPrinting().create();
//			System.out.println(pod.getMetadata().getName() + "\t" + gson.toJson(pod));
//		}
//	}
	

}
