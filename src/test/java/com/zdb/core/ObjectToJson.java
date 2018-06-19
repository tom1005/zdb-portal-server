package com.zdb.core;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.MariaDBConfig;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.RedisConfig;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.ServiceSpec;
import com.zdb.core.domain.ZDBEntity;

public class ObjectToJson {

	public static void main(String[] args) {
		
String d = "2018-06-14T12:11:05Z";
d = d.replace("T", " ");
d = d.replace("Z", "");
SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
try {
	Date parse = sd.parse(d);
	System.out.println(parse.toString());
} catch (Exception e) {
	e.printStackTrace();
}

//		genRedis();
//		genMaria();
	}
	 
	public static void genRedis() {
		ZDBEntity zdbEntity = new ZDBEntity();
		
		zdbEntity.setVersion("4.0.9");
		zdbEntity.setServiceType("redis");
		zdbEntity.setServiceName("zdb-redis-namyu");
		zdbEntity.setNamespace("zdb-redis"); 
		zdbEntity.setClusterEnabled(true); 
		zdbEntity.setClusterSlaveCount(1);
		zdbEntity.setMetricEnabled(true);
		
		ResourceSpec resourceSpec = new ResourceSpec();
		resourceSpec.setResourceType("requests");//Request / Limit

		// maria : Memory: 256Mi, CPU: 250m
		// redis :  
		resourceSpec.setCpu("100m");
		resourceSpec.setMemory("256Mi");
		
		ResourceSpec limitResourceSpec = new ResourceSpec();
		limitResourceSpec.setResourceType("limits");//Request / Limit

		// maria : Memory: 256Mi, CPU: 250m
		// redis : 
		limitResourceSpec.setCpu("200m");
		limitResourceSpec.setMemory("400Mi");

		PodSpec masterPodSpec = new PodSpec();
		masterPodSpec.setPodType("master");
		masterPodSpec.setResourceSpec(new ResourceSpec[]{resourceSpec, limitResourceSpec});
		
		Map<String, String> labels = new HashMap<>(); 
		labels.put("billingType", "hourly");		
		
//		masterPodSpec.setAnnotations();
		masterPodSpec.setLabels(labels);
		
		PodSpec slavePodSpec = new PodSpec();
		slavePodSpec.setPodType("slave");
		slavePodSpec.setResourceSpec(new ResourceSpec[]{resourceSpec, limitResourceSpec});
//		slavePodSpec.setAnnotations();
//		slavePodSpec.setLabels();
		
		zdbEntity.setPodSpec(new PodSpec[]{masterPodSpec, slavePodSpec});

		Map<String, String> annotations = new HashMap<>();
//		annotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", "private");			
		
		ServiceSpec masterServiceSpec = new ServiceSpec();
		masterServiceSpec.setPodType("master"); 				// Master / Slave / Metric / ALL 로 구분하여 Spec을 지정
		masterServiceSpec.setServiceType("LoadBalancer"); 	// NodePort / ClusterIP / LoadBalancer
		masterServiceSpec.setLoadBalancerType("public");
		masterServiceSpec.setServicePort(6379); 				// 각 DB의 Expose 서비스 포트
		masterServiceSpec.setAnnotations(annotations);
//		masterServiceSpec.setLoadBalancerIP("169.56.70.222"); // ALB Public/Private Port
		
		ServiceSpec slaveServiceSpec = new ServiceSpec();
		slaveServiceSpec.setPodType("slave"); 				// Master / Slave / Metric / ALL 로 구분하여 Spec을 지정
		slaveServiceSpec.setServiceType("LoadBalancer"); 	// NodePort / ClusterIP / LoadBalancer
		slaveServiceSpec.setLoadBalancerType("public");
		slaveServiceSpec.setServicePort(6379); 				// 각 DB의 Expose 서비스 포트
		slaveServiceSpec.setAnnotations(annotations);
//		slaveServiceSpec.setLoadBalancerIP("169.56.70.222"); // ALB Public/Private Port
		
		zdbEntity.setServiceSpec(new ServiceSpec[]{masterServiceSpec, slaveServiceSpec});

		PersistenceSpec masterPersistenceSpec = new PersistenceSpec();
		masterPersistenceSpec.setPodType("master");
		masterPersistenceSpec.setEnabled(true);
		masterPersistenceSpec.setStorageClass("ibmc-block-silver");
		masterPersistenceSpec.setAccessMode("ReadWriteOnce");
		masterPersistenceSpec.setBillingType("hourly");
		masterPersistenceSpec.setSize("20");

		zdbEntity.setPersistenceSpec(new PersistenceSpec[]{masterPersistenceSpec});
		
		zdbEntity.setUsePassword(true);
//		zdbEntity.setExistingSecret("existingSecret");
//		zdbEntity.setPassword("password");
				
		RedisConfig masterConfig = new RedisConfig();
		{
			masterConfig.setPodType("master");
			masterConfig.setArgs("");
			masterConfig.setDisableCommands("FLUSHDB, FLUSHALL");
			masterConfig.setExtraFlags("");
		}
		
		RedisConfig slaveConfig = new RedisConfig();
		{
			slaveConfig.setPodType("slave");
			slaveConfig.setArgs("");
			slaveConfig.setDisableCommands("FLUSHDB, FLUSHALL");
			slaveConfig.setExtraFlags("");
		}
		zdbEntity.setRedisConfig(new RedisConfig[] { masterConfig, slaveConfig });
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(zdbEntity);
		
		System.out.println(json);
	}
	
	public static void genMaria() {
		ZDBEntity zdbEntity = new ZDBEntity();
		
		zdbEntity.setId("uuid");
		zdbEntity.setVersion("10.1.31");
		zdbEntity.setServiceType("maraiadb");
		zdbEntity.setServiceName("zdb-maria-test123");
		zdbEntity.setClusterEnabled(true);
		zdbEntity.setNamespace("zdb-maria");
		zdbEntity.setClusterSlaveCount(1);
		zdbEntity.setMetricEnabled(true);
		
		ResourceSpec resourceSpec = new ResourceSpec();
		resourceSpec.setResourceType("request");//Request / Limit

		// maria : Memory: 256Mi, CPU: 250m
		// redis : 
		resourceSpec.setCpu("100m");
		resourceSpec.setMemory("256Mi");
		
		ResourceSpec limitResourceSpec = new ResourceSpec();
		limitResourceSpec.setResourceType("limit");//Request / Limit

		// maria : Memory: 256Mi, CPU: 250m
		// redis : 
		limitResourceSpec.setCpu("100m");
		limitResourceSpec.setMemory("256Mi");

		PodSpec masterPodSpec = new PodSpec();
		masterPodSpec.setPodType("master");
		masterPodSpec.setResourceSpec(new ResourceSpec[]{resourceSpec, limitResourceSpec});
//		masterPodSpec.setAnnotations("annotations"); // option
//		masterPodSpec.setLabels("labels"); // option
		
		PodSpec slavePodSpec = new PodSpec();
		slavePodSpec.setPodType("slave");
		slavePodSpec.setResourceSpec(new ResourceSpec[]{resourceSpec, limitResourceSpec});
//		slavePodSpec.setAnnotations("annotations"); // option
//		slavePodSpec.setLabels("labels"); // option
		
		zdbEntity.setPodSpec(new PodSpec[]{masterPodSpec, slavePodSpec});

		ServiceSpec serviceSpec = new ServiceSpec();
		serviceSpec.setPodType("master"); // Master / Slave / Metric / ALL 로 구분하여 Spec을 지정
		serviceSpec.setServiceType("LoadBalancer"); // NodePort / ClusterIP / LoadBalancer
		serviceSpec.setServicePort(1234); // 각 DB의 Expose 서비스 포트
		serviceSpec.setLoadBalancerIP("169.56.70.222"); // ALB Public/Private Port
		
		zdbEntity.setServiceSpec(new ServiceSpec[]{serviceSpec});

		PersistenceSpec persistenceSpec = new PersistenceSpec();
//		persistenceSpec.setExistingClaim(zdbEntity.getServiceName()+"-data-pvc");
		persistenceSpec.setStorageClass("ibmc-file-silver");//"volume.beta.kubernetes.io/storage-class", "ibmc-file-silver"
//		persistenceSpec.setSubPath("/maria/subPath");
		persistenceSpec.setAccessMode("ReadWriteMany");
		persistenceSpec.setBillingType("hourly");
		persistenceSpec.setSize("20Gi");
		
		PersistenceSpec persistenceSpec2 = new PersistenceSpec();
//			persistenceSpec.setExistingClaim(zdbEntity.getServiceName()+"-data-pvc");
			persistenceSpec2.setStorageClass("ibmc-file-silver");//"volume.beta.kubernetes.io/storage-class", "ibmc-file-silver"
//			persistenceSpec.setSubPath("/maria/subPath");
			persistenceSpec2.setAccessMode("ReadWriteMany");
			persistenceSpec2.setBillingType("hourly");
			persistenceSpec2.setSize("20Gi");
		
		zdbEntity.setPersistenceSpec(new PersistenceSpec[]{persistenceSpec, persistenceSpec2});

//		zdbEntity.setPersistenceEnabled(false);
		zdbEntity.setUsePassword(true);
		zdbEntity.setExistingSecret("existingSecret"); // redis
		zdbEntity.setPassword("password"); 
		
		MariaDBConfig config = new MariaDBConfig();
		config.setMariadbUser("zdbadmin");
		config.setMariadbPassword("zdbadmin!@34");
		config.setMariadbDatabase("zdb-gdi");
		config.setConfig("my.conf");
		
//		MariaDBConfig slaveConfig = new MariaDBConfig();
//		slaveConfig.setMariadbUser("zdbadmin");
//		slaveConfig.setMariadbPassword("zdbadmin!@34");
//		slaveConfig.setMariadbDatabase("zdb-gdi");
//		slaveConfig.setConfig("my.conf");
		
		zdbEntity.setMariaDBConfig(config);
		
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(zdbEntity);
		
		System.out.println(json);
	}

}
