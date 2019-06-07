package com.zdb.core.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class StatusUtil implements Callback<byte[]>{
	
	final StringBuffer sb = new StringBuffer();
	
	@Override
	public void call(byte[] input) {
		try {
			String x = new String(input, "UTF-8");
			// System.out.println(x);
			sb.append(x);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
	
	public Map<String, String> slaveStatus(DefaultKubernetesClient client, String namespace, String podName) throws Exception {
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
		long s = System.currentTimeMillis();
		StringBuffer sb = new StringBuffer();
		sb.append("exec mysql -uroot -p$MARIADB_ROOT_PASSWORD -e").append(" ");
		sb.append("\"show slave status\\G\"").append(" ");;
		sb.append("| grep -E \"");
		sb.append("Read_Master_Log_Pos").append("|");
		sb.append("Exec_Master_Log_Pos").append("|");
		sb.append("Slave_IO_Running").append("|");
		sb.append("Slave_SQL_Running").append("|");
		sb.append("Last_Errno").append("|");
		sb.append("Last_Error").append("|");
		sb.append("Last_IO_Error|Last_IO_Errno").append("|");
		sb.append("Seconds_Behind_Master");
		sb.append("\"");
		
		//System.out.println("exec command : "+sb.toString());
		
//		String result = new ExecUtil_Old().exec(client, namespace, podName, sb.toString());
		String result = "";
		try {
			result = new ExecUtil().exec(client, namespace, podName, "mariadb",sb.toString());
		} catch (IOException e) {
			Thread.sleep(1000);
			throw e;
		}
//		System.out.println(result);
		Map<String, String> statusValueMap = parseValue(null, result, ":");
		
//		System.out.println("Slave status - " + (System.currentTimeMillis() - s) + " " + podName);
		
		
		return statusValueMap;
	}
	
	public boolean replicationStatus(DefaultKubernetesClient client, String namespace, String podName) throws Exception {
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
		
		Map<String, String> statusValueMap = slaveStatus(client, namespace, podName);
		
	    if(statusValueMap == null || statusValueMap.isEmpty()) {
	    	throw new Exception("unknown slave replication status");
	    }
		
		String Read_Master_Log_Pos = statusValueMap.get("Read_Master_Log_Pos");
		String Exec_Master_Log_Pos = statusValueMap.get("Exec_Master_Log_Pos");
		String Slave_IO_Running = statusValueMap.get("Slave_IO_Running");
		String Slave_SQL_Running = statusValueMap.get("Slave_SQL_Running");
		String Last_Errno = statusValueMap.get("Last_Errno");
		String Last_Error = statusValueMap.get("Last_Error");
		String Last_IO_Errno = statusValueMap.get("Last_IO_Errno");
		String Last_IO_Error = statusValueMap.get("Last_IO_Error");
		String Seconds_Behind_Master = statusValueMap.get("Seconds_Behind_Master");
		
		if(!Read_Master_Log_Pos.equals(Exec_Master_Log_Pos)) {
			Gson gson = new Gson();
			String status = gson.toJson(statusValueMap);
			throw new Exception("Read_Master_Log_Pos != Exec_Master_Log_Pos\n" + status);
		}
		
		if(!"Yes".equals(Slave_IO_Running) || !"Yes".equals(Slave_SQL_Running) ) {
			Gson gson = new Gson();
			String status = gson.toJson(statusValueMap);
			throw new Exception("Slave_IO_Running : " + Slave_IO_Running+"\nSlave_SQL_Running : " + Slave_SQL_Running+"\n" + status);
		}
		
		if(!"0".equals(Last_Errno) || null != Last_Error) {
			Gson gson = new Gson();
			String status = gson.toJson(statusValueMap);
			throw new Exception("Last_Errno : " + Last_Errno+"\nLast_Error : " + Last_Error+"\n" + status);
		}

		if(!"0".equals(Last_IO_Errno) || null != Last_IO_Error) {
			Gson gson = new Gson();
			String status = gson.toJson(statusValueMap);
			throw new Exception("Last_IO_Errno : " + Last_Errno+"\nLast_IO_Error: " + Last_Error+"\n" + status);
		}
		
		if(!"0".equals(Seconds_Behind_Master)) {
			Gson gson = new Gson();
			String status = gson.toJson(statusValueMap);
			throw new Exception("Seconds_Behind_Master : " + Seconds_Behind_Master+"\n" + status);
		}
		
		return true;
	}

	
	/**
	 * Master 노드 장애로 슬레이브의 상태를 조회(복제 딜레이 상태 체크)
	 * 
	 * @param client
	 * @param namespace
	 * @param podName
	 * @return
	 * @throws Exception
	 */
	public boolean failoverReplicationStatus(DefaultKubernetesClient client, String namespace, String podName) throws Exception {
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
		
		Map<String, String> statusValueMap = slaveStatus(client, namespace, podName);
		
		if(statusValueMap == null || statusValueMap.isEmpty()) {
			throw new Exception("unknown slave replication status");
		}
		
		String Read_Master_Log_Pos = statusValueMap.get("Read_Master_Log_Pos");
		String Exec_Master_Log_Pos = statusValueMap.get("Exec_Master_Log_Pos");
		String Slave_IO_Running = statusValueMap.get("Slave_IO_Running");
		String Slave_SQL_Running = statusValueMap.get("Slave_SQL_Running");
		String Last_Errno = statusValueMap.get("Last_Errno");
		String Last_Error = statusValueMap.get("Last_Error");
		String Last_IO_Errno = statusValueMap.get("Last_IO_Errno");
		String Last_IO_Error = statusValueMap.get("Last_IO_Error");
		String Seconds_Behind_Master = statusValueMap.get("Seconds_Behind_Master");
		
		if(!Read_Master_Log_Pos.equals(Exec_Master_Log_Pos)) {
			Gson gson = new Gson();
			String status = gson.toJson(statusValueMap);
			System.out.println(status);
			throw new Exception("Read_Master_Log_Pos != Exec_Master_Log_Pos\n" + status);
		}
		
//		if(!"Yes".equals(Slave_IO_Running) || !"Yes".equals(Slave_SQL_Running) ) {
//			Gson gson = new Gson();
//			String status = gson.toJson(statusValueMap);
//			throw new Exception("Slave_IO_Running : " + Slave_IO_Running+"\nSlave_SQL_Running : " + Slave_SQL_Running+"\n" + status);
//		}
		
		if(!"0".equals(Last_Errno) || null != Last_Error) {
			Gson gson = new Gson();
			String status = gson.toJson(statusValueMap);
			throw new Exception("Last_Errno : " + Last_Errno+"\nLast_Error : " + Last_Error+"\n" + status);
		}
		
//		if(!"0".equals(Last_IO_Errno) || null != Last_IO_Error) {
//			Gson gson = new Gson();
//			String status = gson.toJson(statusValueMap);
//			throw new Exception("Last_IO_Errno : " + Last_Errno+"\nLast_IO_Error: " + Last_Error+"\n" + status);
//		}
		
//		if(!"0".equals(Seconds_Behind_Master)) {
//			Gson gson = new Gson();
//			String status = gson.toJson(statusValueMap);
//			throw new Exception("Seconds_Behind_Master : " + Seconds_Behind_Master+"\n" + status);
//		}
		
		return true;
	}
}
