package com.zdb.core;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.microbean.helm.chart.URLChartLoader;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hapi.chart.ChartOuterClass.Chart;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;

public class RedisChartTest {

	public static void subMap(int depth, Map iccsConfig) {
		
		Deployment.class.getName(); // O 1 updated, 1 total, 1 available, 0 unavailable  DeploymentCondition
		StatefulSet.class.getName();// O
		Pod.class.getName();// O Podstatus.PodCondition
		Namespace.class.getName();// O
		Service.class.getName();// O
		ReplicaSet.class.getName();// O
		PersistentVolumeClaim.class.getName(); // O
		Event.class.getName(); //  X
		Secret.class.getName(); //X
		ConfigMap.class.getName(); // X
		
		Set<String> keySet = iccsConfig.keySet();

		for (String f : keySet) {
			// System.out.println(key +" / "+ f.getDefaultValue());

			Object object = iccsConfig.get(f);
			if (object instanceof HashMap) {
				System.out.println(tabString(depth) + f+ ": ");
				subMap(++depth, (Map) object);
			} else {
				System.out.println(tabString(depth) + f + ": " + object);
			}
		}
	}
	
	public static String tabString(int depth) {
		String tab = "";
		for(int i =0 ; i < depth; i++) {
			 tab += "\t";
		}
		
		return tab;
	}
	
	public static void main(String[] args) throws Exception {
		//final URI uri = URI.create("http://169.56.70.222:31770/redis-3.2.1.tgz");
		final URI uri = URI.create("file:////Users/namyounguk/git/charts/stable/redis-3.3.0.tgz");

		final URL url = uri.toURL();

		Chart.Builder chart = null;

		try (final URLChartLoader chartLoader = new URLChartLoader()) {
			chart = chartLoader.load(url);
		}
		
		String raw = chart.getValues().getRaw();
		
		
//		spec:
//			  template:
//			    metadata:
//			      labels:
//			        billingType: "hourly"    

//		List<Builder> templatesBuilderList = chart.getTemplatesBuilderList();
//		for (Builder temp : templatesBuilderList) {
//			String name = temp.getName();
//			if (name.endsWith("statefulset.yaml")) {
//				System.out.println(name);
//				Map<FieldDescriptor, Object> allFields = temp.getAllFields();
//				
//				Set<FieldDescriptor> keySet = allFields.keySet();
//				for(FieldDescriptor f : keySet) {
//					ByteString data = temp.getData();
//					
//					System.out.println(data.toStringUtf8());
//					
//				}
//			}
//		}
//		
//		List<Template> templatesList = chart.getTemplatesList();
//		for(Template temp : templatesList) {
//			String name = temp.getName();
//			
//			
//			if(name.endsWith("statefulset.yaml")) {
//				Map<FieldDescriptor, Object> allFields = temp.getAllFields();
//				Parser<Template> parser = temp.parser();
//				Set<FieldDescriptor> keySet = allFields.keySet();
//				for(FieldDescriptor f : keySet) {
//					System.out.println(allFields.get(f));
//					
//				}
//			}
//		}
		
		
//		InputStream input = new FileInputStream(new File(fileName));
	    Yaml yaml = new Yaml();
	    Map<String, Object> iccsConfig = (Map<String, Object>)yaml.load(raw);
	    
	    Gson gson = new GsonBuilder().setPrettyPrinting().create();
	    System.out.println(gson.toJson(iccsConfig));
		
		Set<String> keySet = iccsConfig.keySet();
		
		
		for(String f : keySet) {
//			System.out.println(key +" / "+ f.getDefaultValue());
			
			Object object = iccsConfig.get(f);
			if(object instanceof HashMap) {
				System.out.println(f+ ": ");
				int depth = 0;
				subMap(++depth, (Map) object);
			} else {
				System.out.println(f +": "+ object);
			}
		}
		
//		String output = yaml.dumpAsMap(iccsConfig);
//		System.out.println(output);

//		chart.getValuesBuilder().build()
		
//		Metadata metadata = chart.getMetadata();
//		String chartName = metadata.getName();
//		String chartVersion = metadata.getVersion();
		
//		Config values = chart.getValues();
//		Map<FieldDescriptor, Object> allFields = values.getAllFields();
		
		
//		Set<FieldDescriptor> keySet = allFields.keySet();
//		
//		System.out.println("chartName : " + chartName);
//		System.out.println("chartVersion : " + chartVersion);
//		for(FieldDescriptor f : keySet) {
//			String key = f.getName();
////			System.out.println(key +" / "+ f.getDefaultValue());
//			
//			System.out.println(key +" / "+ allFields.get(f));
//		}
		
		
//		chart.getMetadata()
		
//		Map<String, Value> values2 = values.getValuesMap();
//		
//		Set<String> keySet = values2.keySet();
//		
//		for(String f : keySet) {
////			System.out.println(key +" / "+ f.getDefaultValue());
//			
//			System.out.println(f +" / "+ values2.get(f));
//		}
		
	}

	public static void writeConfigToYaml(Map configSetting) throws Exception {
		try {
			Yaml yaml = new Yaml();
			String output = yaml.dump(configSetting);
//			byte[] sourceByte = output.getBytes();
//			File file = new File("/home/nexcore");
//			if (!file.exists()) {
//				file.createNewFile();
//			}
//			FileOutputStream fileOutputStream = new FileOutputStream(file);
//			fileOutputStream.write(sourceByte);
//			fileOutputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
