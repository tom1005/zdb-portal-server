package com.zdb.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import com.google.gson.Gson;
import com.zdb.core.domain.Result;

import io.fabric8.kubernetes.api.model.extensions.Deployment;

public class ResultToObject {

	public static void main(String[] args) {
		try {
			String json = readFile("/home/nexcore/git2/zdb.rest.api/src/test/java/com/zdb/core/deployments.json");
			
			jsonToResultObject(json);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void jsonToResultObject(String json) {
		Gson gson = new Gson();
		Result fromJson = gson.fromJson(json, Result.class);
		Map<String, Object> result = fromJson.getResult();
		Object object = result.get("deployments");
		
		Gson deploymentGson = new Gson();
		Deployment[] fromJson2 = deploymentGson.fromJson(new Gson().toJson(object), Deployment[].class);
		
		for(Deployment deployment : fromJson2 ) {
			System.out.println(deployment.getMetadata().getName());
		}
	}

	private static String readFile(String file) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = null;
		StringBuilder stringBuilder = new StringBuilder();
		String ls = System.getProperty("line.separator");

		try {
			while ((line = reader.readLine()) != null) {
				stringBuilder.append(line);
				stringBuilder.append(ls);
			}

			return stringBuilder.toString();
		} finally {
			reader.close();
		}
	}
}
