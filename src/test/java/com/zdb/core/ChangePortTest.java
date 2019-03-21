package com.zdb.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zdb.core.domain.Result;
import com.zdb.core.util.ExecUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChangePortTest {

	public static void main(String[] args) {
		ChangePortTest c = new ChangePortTest();
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			Map<String, String> masterDBPosition = c.getMasterDBPosition(K8SUtil.kubernetesClient(), "zdb-test", "zdb-test-change-port-mariadb-master-0");
			System.out.println(masterDBPosition);
			
			String stopSlave = c.stopSlave(K8SUtil.kubernetesClient(), "zdb-test", "zdb-test-change-port-mariadb-slave-0");
			System.out.println(stopSlave);
//			String showSlaveStatus = c.showSlaveStatus(K8SUtil.kubernetesClient(), "zdb-test", "zdb-test-change-port-mariadb-slave-0");
//			System.out.println(showSlaveStatus);
			
//			boolean slaveStatus = c.slaveStatus(K8SUtil.kubernetesClient(), "zdb-test", "zdb-test-change-port-mariadb-slave-0");
//			System.out.println(slaveStatus);
			
			String namespace = "zdb-test";
			String serviceName = "zdb-test-change-port";
			String slavePodName = "zdb-test-change-port-mariadb-slave-0";
			String masterServiceName = "zdb-test-change-port-mariadb";
			
			
			List<Service> serviceList = client.inNamespace("zdb-test").services().withLabel("release", "zdb-test-change-port").list().getItems();
			if (serviceList == null || serviceList.isEmpty()) {
				log.warn("Service is null. Service Name: {}", "zdb-test-change-port");
//				return new Result(txId, Result.ERROR, "서비스 정보 조회중 오류가 발생했습니다. ["+namespace+" > "+serviceName +"]");
			}
			
			String port = "12348";
			List<Service> targetServiceList = new ArrayList<>();
			
			for (Service svc : serviceList) {
				String svcName = svc.getMetadata().getName();
				String servicePort = c.getServicePort(svc);
				
				if(servicePort != null && !servicePort.isEmpty()) {
					if(port.equals(servicePort)) {
						log.error("이미 적용된 포트 입니다. [{}]", svcName);
						continue;
					} else {
						//
						targetServiceList.add(svc);
					}
				} else {
					log.error("서비스 포트 정보를 알 수 없습니다. [{}]", svcName);
					continue;
				}
			}
			
			for (Service service : targetServiceList) {
				c.chageServicePort(client, namespace, service, Integer.parseInt(port));
			}
			
			String binFile = masterDBPosition.get("File");
			String position = masterDBPosition.get("Position");
			
			
			c.changeMaster(client, namespace, slavePodName, masterServiceName, port, binFile, position);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String changeMaster(DefaultKubernetesClient client, String namespace, String slavePodName, String masterServiceName, String port, String binFile, String position) throws Exception {
//		CHANGE MASTER TO
//		          MASTER_HOST='zdb-test-change-port-mariadb',
//		          MASTER_USER='replicator',
//		          MASTER_PASSWORD='zdbadmin12#$',
//		          MASTER_PORT=39999,
//		          MASTER_LOG_FILE='mysql-bin.000002',
//		          MASTER_LOG_POS=7976,
//		          MASTER_CONNECT_RETRY=10;
//		start slave;
		
		StringBuffer sb = new StringBuffer();
		sb.append("mysql -uroot -p$MARIADB_ROOT_PASSWORD -e").append(" ");
		
		sb.append("\"CHANGE MASTER TO").append(" ");
		sb.append("MASTER_HOST='"+masterServiceName+"',").append(" ");
		sb.append("MASTER_USER='replicator',").append(" ");
		sb.append("MASTER_PASSWORD='zdbadmin12#$',").append(" ");
		sb.append("MASTER_PORT="+port+",").append(" ");
		sb.append("MASTER_LOG_FILE='"+binFile+"',").append(" ");
		sb.append("MASTER_LOG_POS="+position+",").append(" ");
		sb.append("MASTER_CONNECT_RETRY=10;\n").append(" ");

		sb.append("start slave;\n\"").append(" ");
		
		String result = new ExecUtil().exec(client, namespace, slavePodName, "mariadb",sb.toString());
//		System.out.println(result);
		return result;
	}
	
	private String getServicePort(Service service) throws Exception {

		Map<String, String> annotations = service.getMetadata().getAnnotations();
		String value = annotations.get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type");
		if (value != null && ("public".equals(value) || "private".equals(value))) {

			String portStr = null;
			if ("loadbalancer".equals(service.getSpec().getType().toLowerCase())) {
				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort _port : ports) {
					if ("mysql".equals(_port.getName())) {
						return Integer.toString(_port.getPort());
					}
				}

				if (portStr == null) {
					throw new Exception("Unknown Service Port");
				}
			} else if ("clusterip".equals(service.getSpec().getType().toLowerCase())) {
				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort _port : ports) {
					if ("mysql".equals(_port.getName())) {
						return Integer.toString(_port.getPort());
					}
				}
				if (portStr == null) {
					throw new Exception("unknown ServicePort");
				}

			} else {
				log.warn("no cluster ip.");
			}
		}

		return null;
	}
	
	public void chageServicePort(DefaultKubernetesClient client, String namespace, Service service, int port) {
		try {
			MixedOperation<Service, ServiceList, DoneableService, Resource<Service, DoneableService>> services = client.inNamespace(namespace).services();
			service.getMetadata().setUid(null);
			service.getMetadata().setCreationTimestamp(null);
			service.getMetadata().setSelfLink(null);
			service.getMetadata().setResourceVersion(null);
			service.setStatus(null);
			
			service.getSpec().getPorts().get(0).setPort(port);
			
			ServiceBuilder svcBuilder = new ServiceBuilder(service);
			Service newSvc = svcBuilder.build();
			
			
			System.out.println("1>>>>>>>>>>>>>>>>>> "+service.getMetadata().getName());
			
			services.createOrReplace(newSvc);
			System.out.println("2>>>>>>>>>>>>>>>>>> "+service.getMetadata().getName());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String stopSlave(DefaultKubernetesClient client, String namespace, String podName) throws Exception {
		StringBuffer sb = new StringBuffer();
		sb.append("mysql -uroot -p$MARIADB_ROOT_PASSWORD -e").append(" ");
		sb.append("\"commit;stop slave;\"").append(" ");;
		String result = new ExecUtil().exec(client, namespace, podName, "mariadb",sb.toString());
//		System.out.println(result);
		return result;
	}
	
	public Map<String, String> getMasterDBPosition(DefaultKubernetesClient client, String namespace, String podName) throws Exception {
		StringBuffer sb = new StringBuffer();
		sb.append("mysql -uroot -p$MARIADB_ROOT_PASSWORD -e").append(" ");
		sb.append("\"show master status\\G\"").append(" ");;
		String result = new ExecUtil().exec(client, namespace, podName, "mariadb", sb.toString());
		System.out.println(result);
		
		Map<String, String> masterStatus = parseValue(null, result, ":");
		
		String binFile = masterStatus.get("File");
		String position = masterStatus.get("Position");
		
		return masterStatus;
	}
	
	public String showSlaveStatus(DefaultKubernetesClient client, String namespace, String podName) throws Exception {
		StringBuffer sb = new StringBuffer();
		sb.append("mysql -uroot -p$MARIADB_ROOT_PASSWORD -e").append(" ");
		sb.append("\"show slave status\\G\"").append(" ");;
		String result = new ExecUtil().exec(client, namespace, podName, "mariadb",sb.toString());
		System.out.println(result);
		return result;
	}
	
	public boolean slaveStatus(DefaultKubernetesClient client, String namespace, String podName) throws Exception {
//		MariaDB [(none)]> show slave status\G
//		*************************** 1. row ***************************
//		...
//		          Read_Master_Log_Pos: 5914
//		          Exec_Master_Log_Pos: 5914          
//		             Slave_IO_Running: Yes
//		            Slave_SQL_Running: Yes
//		                   Last_Errno: 0
//		                   Last_Error:
//		        Seconds_Behind_Master: 0
		
		StringBuffer sb = new StringBuffer();
		sb.append("mysql -uroot -p$MARIADB_ROOT_PASSWORD -e").append(" ");
		sb.append("\"show slave status\\G\"").append(" ");;
		sb.append("| grep -E \"");
		sb.append("Read_Master_Log_Pos").append("|");
		sb.append("Exec_Master_Log_Pos").append("|");
		sb.append("Slave_IO_Running").append("|");
		sb.append("Slave_SQL_Running").append("|");
		sb.append("Last_Errno").append("|");
		sb.append("Last_Error").append("|");
		sb.append("Last_IO_Error").append("|");
		sb.append("Last_IO_Errno").append("|");
		sb.append("Seconds_Behind_Master");
		sb.append("\"");
		
		//System.out.println("exec command : "+sb.toString());
		
		String result = new ExecUtil().exec(client, namespace, podName, "mariadb", sb.toString());
		System.out.println(result);
		Map<String, String> statusValueMap = parseValue(null, result, ":");
		
		String read_Master_Log_Pos = statusValueMap.get("Read_Master_Log_Pos");
		String exec_Master_Log_Pos = statusValueMap.get("Exec_Master_Log_Pos");
		String slave_IO_Running = statusValueMap.get("Slave_IO_Running");
		String slave_SQL_Running = statusValueMap.get("Slave_SQL_Running");
		String last_Errno = statusValueMap.get("Last_Errno");
		String last_Error = statusValueMap.get("Last_Error");
		String last_IO_Errno = statusValueMap.get("Last_IO_Errno");//Last_IO_Errno
		String last_IO_Error = statusValueMap.get("Last_IO_Error");//Last_IO_Error
		String seconds_Behind_Master = statusValueMap.get("Seconds_Behind_Master");	
		
		boolean replicationStatus = true;
		
		if(!"0".equals(last_Errno) || null != last_Error) {
			replicationStatus = false;
		}
		
		if(!"0".equals(last_IO_Errno) || (null != last_IO_Error && !last_IO_Error.isEmpty())) {
			replicationStatus = false;
		}
		
		if(!"Yes".equals(slave_IO_Running)) {
			replicationStatus = false;
		}
		if(!"Yes".equals(slave_SQL_Running) ) {
			replicationStatus = false;
		}
		
		if(!"Yes".equals(slave_IO_Running) || !"Yes".equals(slave_SQL_Running) ) {
			replicationStatus = false;
		}
		
		if(!replicationStatus) {
			throw new Exception("Slave 복제 오류로 포트 변경이 불가 합니다.");
		}
		
		if(!"0".equals(seconds_Behind_Master)) {
			replicationStatus = false;
		}
		
		if(!replicationStatus) {
			throw new Exception("Slave 복제 지연으로 포트 변경이 불가 합니다. 잠시 후 다시 시도하세요.");
		}
		
		return replicationStatus;
	}
	
	Map<String, String> parseValue(Map<String, String> map, String resultStr, String regex) {
		if(map == null) {
			map = new HashMap<String, String>();
		}
		
		if(resultStr != null && !resultStr.trim().isEmpty()) {

			String[] lineSplit = resultStr.trim().split("\n");
			for (String line : lineSplit) {
				String[] split = line.trim().split(regex);
				
				if(split.length >= 2) {
					String key = split[0].trim();
					String value = line.trim().substring(key.length()+regex.length()).trim();
					
					map.put(key, value);
				}
			}
		}
		
		return map;
	}
}
