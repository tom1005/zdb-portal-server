package com.zdb.core;

import java.net.URI;
import java.util.Map;

import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;

public class ServiceStatus {

	public static void main(String[] args) {
		
		long s = System.currentTimeMillis();
		
		while (true) {

			try {
				Thread.sleep(1000 * 5);
				RestTemplate restTemplate = new RestTemplate();
				URI uri = URI.create("http://127.0.0.1:8080/api/v1/zdb-maria/mariadb/service/services/zdb-310");
				Result r = restTemplate.getForObject(uri, Result.class);

				Map<String, Object> result = r.getResult();
//				System.out.println(result);

				Gson gson = new Gson();
				String rrr = gson.toJson(result.get("serviceoverview"));
				if(rrr.length() <= 2) {
					continue;
				}
				ServiceOverview jsonTree = gson.fromJson(rrr, ServiceOverview.class);

				if (jsonTree != null) {
					System.out.println(jsonTree.getStatusMessage());
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {

			}

			if ((System.currentTimeMillis() - s) > 5 * 60 * 1000) {
				break;
			}

		}
		
	}

}
