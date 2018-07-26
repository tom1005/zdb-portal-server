package com.zdb.core;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;

public class K8SUtilTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void getDeploymentName() {
		try {
			List<Deployment> deploymentList = K8SUtil.getDeploymentListByReleaseName("zdb", "mariadb3-test");

			Deployment deployment = deploymentList.get(0);
					
			assertTrue(deployment.getMetadata().getName().equals("mariadb3-test-mariadb"));

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}

	@Test
	public void getChartName() {
		try {
			String result = K8SUtil.getChartName("zdb", "mariadb3-test");

			assertTrue(result.equals("mariadb"));

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}

	@Test
	public void getClusterIp() {
		try {
			String result = K8SUtil.getClusterIp("zdb", "mariadb3-test-mariadb");

			assertTrue(result != null);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}

	@Test
	public void getDeploymentList() {
		try {
			List<Deployment> result = K8SUtil.getDeploymentListByReleaseName("zdb", "mariadb3-test");

			assertTrue(result.size() > 0);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}

	@Test
	public void getExternalIPs() {
		try {
			List<String> result = K8SUtil.getExternalIPs("zdb", "mariadb3-test");

			assertTrue(result.size() > 0);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}

	@Test
	public void getNamespaces() {
		try {
			List<Namespace> result = K8SUtil.getNamespaces();

			assertTrue(result.size() > 0);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}


	@Test
	public void getPods() {
//		try {
//			List<?> result = K8SUtil.getPods("zdb");
//
//			assertTrue(result.size() > 0);
//
//		} catch (Exception e) {
//			e.printStackTrace();
//		} finally {
//
//		}

	}
	
	@Test
	public void getServices() {
		try {
			List<?> result = K8SUtil.getServicesWithNamespace("zdb");

			assertTrue(result.size() > 0);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}
	
	@Test
	public void getService() {
		try {
			List<Service> serviceList = K8SUtil.getServices("zdb", "mariadb3-test");
			
			Service service = serviceList.get(0);
			assertTrue(service.getMetadata().getName().equals("mariadb3-test-mariadb"));

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}
	
	
	@Test
	public void isAvailableReplicas() {
		try {
			boolean bool = K8SUtil.isAvailableReplicas("zdb","mariadb3-test");

			assertTrue(bool);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}
	
	@Test
	public void isNamespaceExist() {
		try {
			boolean bool = K8SUtil.isNamespaceExist("zdb");

			assertTrue(bool);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}
	
//	@Test
//	public void isServiceExist() {
//		try {
//			boolean bool = K8SUtil.isServiceExist("zdb", "mariadb3-test");
//
//			assertTrue(bool);
//
//		} catch (Exception e) {
//			e.printStackTrace();
//		} finally {
//
//		}
// 
//	}
	
	@Test
	public void updateSecret() {
		try {
			String result = K8SUtil.updateSecrets("zdb-redis", "zdb-redis-namyu3", "redis-password", "EmISiCdoGo");   
   
			assertTrue(result != null); 

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

		}

	}	
	
}
