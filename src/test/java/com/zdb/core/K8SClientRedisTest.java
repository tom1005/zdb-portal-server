package com.zdb.core;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;

public class K8SClientRedisTest {

	@Test
	public void testPod() throws Exception {

		List<Pod> items = K8SUtil.kubernetesClient().inNamespace("zdb-redis").pods().list().getItems();

		for(Pod pod : items) {
			System.out.println(pod.getMetadata().getName() +"\t"+pod);
		}
		
		System.out.println();
		System.out.println();
	}
	
	@Test
	public void testReplicaSets() throws Exception {

		List<ReplicaSet> items = K8SUtil.kubernetesClient().inNamespace("zdb-redis").extensions().replicaSets().list().getItems();

		for (ReplicaSet rs : items) {
				System.out.println(rs.getMetadata().getName() +"\t"+rs);
		}
		
		System.out.println();
		System.out.println();
	}
	
	@Test
	public void testDeployments() throws Exception {

		List<Deployment> items = K8SUtil.kubernetesClient().inNamespace("zdb-redis").extensions().deployments().list().getItems();

		for (Deployment rs : items) {
				System.out.println(rs.getMetadata().getName() +"\t"+rs);
		}
		
		
		System.out.println();
		System.out.println();
	}
	
	@Test
	public void testServices() throws Exception {

		List<Service> items = K8SUtil.kubernetesClient().inNamespace("zdb-redis").services().list().getItems();

		for (Service rs : items) {
				System.out.println(rs.getMetadata().getName() +"\t"+rs);
		}
	}
	
	@Test
	public void testDeploymentList() throws Exception {

		List<Deployment> items = K8SUtil.getDeploymentListByReleaseName("zdb-redis", "new-redis-test2");
		assertEquals(items.size(), 2);
		for (Deployment rs : items) {
				System.out.println(rs.getMetadata().getName() +"\t"+rs);
		}
	}
	
	@Test
	public void testServiceList() throws Exception {

		List<Service> items = K8SUtil.getServices("zdb-redis", "new-redis-test2");
		assertEquals(items.size(), 3);
		for (Service rs : items) {
				System.out.println(rs.getMetadata().getName() +"\t"+rs);
		}
	}
	
	@Test
	public void testReplicaSetList() throws Exception {

		List<ReplicaSet> items = K8SUtil.getReplicaSets("zdb-redis", "new-redis-test2");
		assertEquals(items.size(), 2);
		for (ReplicaSet rs : items) {
				System.out.println(rs.getMetadata().getName() +"\t"+rs);
		}
	}
	
	@Test
	public void testPodList() throws Exception {

		List<Pod> items = K8SUtil.getPods("zdb-maria", "mariadb-test-mariadb");
		assertEquals(items.size(), 6);
		for (Pod rs : items) {
				System.out.println(rs.getMetadata().getName() +"\t"+rs);
		}
	}
}
