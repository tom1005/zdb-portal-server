package com.zdb.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.zdb.core.util.ExecUtil;
import com.zdb.core.util.K8SUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessListExam {

	public static void main(String[] args) {
		
		String namespace = "zdb-system";
		String podName = "zdb-portal-db-mariadb-0";
		String container = "mariadb";
		String cmd = "mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"show processlist\\G\"";
//		String cmd = "mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"kill <PID>;\"";  // kill 
		
		try {
			ExecUtil execUtil = new ExecUtil();
			String result = execUtil.exec(K8SUtil.kubernetesClient(), namespace, podName, container, cmd);
			
			List<Map<String, String>> parseValue = parseValue(result ,":");
			
			for (Map map : parseValue) {
				for (Iterator<String> iterator = map.keySet().iterator(); iterator.hasNext();) {
					String k = iterator.next();
					String v = (String) map.get(k);
					
					System.out.println(k +" / "+ v);
				}
				
				System.out.println("--------------------------------------");
			}
			
//			System.out.println(result);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	static List<Map<String, String>> parseValue(String resultStr, String regex) {
		List<Map<String, String>> mapList = new ArrayList<>();
		
		if(resultStr != null && !resultStr.trim().isEmpty()) {
			Map<String, String> map = null;
			
			String[] lineSplit = resultStr.trim().split("\n");
			for (String line : lineSplit) {
				if(line.startsWith("***")) {
					if(map != null) {
						mapList.add(map);
					}
					map = new HashMap<String, String>();
					
				} else {
					String[] split = line.trim().split(regex);
					
					if(split.length >= 1) {
						String key = split[0].trim();
						String value = line.trim().substring(key.length()+regex.length()).trim();
						
						map.put(key, value);
					}
					
				}
			}
		}
		
		return mapList;
	}

}
