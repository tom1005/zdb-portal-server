package com.zdb.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.util.K8SUtil;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ConfigOuterClass.Config.Builder;
import hapi.chart.ConfigOuterClass.Value;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseResponse;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class HelmRedisInstall {
	private static final Logger logger = LoggerFactory.getLogger(HelmRedisInstall.class);

	public static void main(String[] args) throws Exception {

		final DefaultKubernetesClient client = K8SUtil.kubernetesClient();
  
		if (logger.isDebugEnabled()) {
			logger.debug("master url: {}", client.getMasterUrl());
			logger.debug("kubernetes api version: {}", client.getApiVersion());
		}

		final Tiller tiller = new Tiller(client);
		final ReleaseManager releaseManager = new ReleaseManager(tiller);

		String serviceName = "zdb-redis-common";

		try {
			final URI uri = URI.create("file:////Users/namyounguk/git/charts/stable/redis/redis-3.3.2.tgz");
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}
			
//			String raw = chart.getValues().getRaw();
//
//			String chartName = chart.getMetadata().getName();
			
			final InstallReleaseRequest.Builder requestBuilder = InstallReleaseRequest.newBuilder();
			if (requestBuilder != null) {
				requestBuilder.setTimeout(300L);
				requestBuilder.setName(serviceName);
				requestBuilder.setNamespace("zdb-redis");

				requestBuilder.setWait(true);

				Builder valuesBuilder = requestBuilder.getValuesBuilder();
				
			//	String inputJson = readFile("/Users/namyounguk/git/zdb.rest.api/src/test/java/com/zdb/core/input_redis.json");
				
				Map<String, Object> values = new LinkedHashMap<String, Object>();

				Map<String, Object> imageMap = new LinkedHashMap<String, Object>();
				imageMap.put("tag", "4.0.9");
				values.put("image", imageMap);

				Map<String, Object> clusterMap = new LinkedHashMap<String, Object>();
				clusterMap.put("enabled", true);
				clusterMap.put("slaveCount", 1);
				values.put("cluster", clusterMap);
				
				Map<String, Object> metricMap = new LinkedHashMap<String, Object>();
				Map<String, String> metricPodLabels = new LinkedHashMap<String, String>();
				Map<String, String> metricPodAnnotations = new LinkedHashMap<String, String>();
				metricMap.put("enabled", true);
				metricMap.put("podLabels", metricPodLabels);
				metricMap.put("podAnnotations", metricPodAnnotations);
				values.put("metrics", metricMap);

				Map<String, Object> networkPolicy = new LinkedHashMap<String, Object>();
				networkPolicy.put("enabled", false);
				networkPolicy.put("allowExternal", false);
				values.put("networkPolicy", networkPolicy);
				
				values.put("usePassword", true);
				
				Map<String, Object> master = new LinkedHashMap<String, Object>();
				master.put("port", 6379);

				Map<String, Object> masterArgs = new LinkedHashMap<String, Object>();
				master.put("args", masterArgs);

				Map<String, Object> masterExtraFlags = new LinkedHashMap<String, Object>();
				master.put("extraFlags", masterExtraFlags);

				master.put("disableCommands", "FLUSHDB, FLUSHALL");

				Map<String, Object> masterPodLabels = new LinkedHashMap<String, Object>();
				masterPodLabels.put("billingType", "hourly");
				master.put("podLabels", masterPodLabels);

				Map<String, Object> masterPodAnnotations = new LinkedHashMap<String, Object>();
				master.put("podAnnotations", masterPodAnnotations);
				
				Map<String, Object> masterService = new LinkedHashMap<String, Object>();
				masterService.put("type", "LoadBalancer");
				Map<String, Object> masterServiceAnnotations = new LinkedHashMap<String, Object>();
				masterServiceAnnotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", "private");
				masterService.put("annotations", masterServiceAnnotations);
				master.put("service", masterService);
				
				Map<String, Object> masterPersistence = new LinkedHashMap<String, Object>();
				masterPersistence.put("enabled", true);
				masterPersistence.put("storageClass", "ibmc-file-silver");
				List<String> accessModes = new ArrayList<String>();
				accessModes.add("ReadWriteOnce");
				masterPersistence.put("accessModes", accessModes);
				masterPersistence.put("path", "/bitnami/redis");
				masterPersistence.put("size", "20Gi");
				master.put("persistence", masterPersistence);

				Map<String, Object> masterResource = new LinkedHashMap<String, Object>();
				Map<String, Object> masterResourceRequests = new LinkedHashMap<String, Object>();
				masterResourceRequests.put("cpu", "100m");
				masterResourceRequests.put("memory", "256Mi");
				masterResource.put("request", masterResourceRequests);
				Map<String, Object> masterResourceLimits = new LinkedHashMap<String, Object>();
				masterResourceLimits.put("cpu", "200m");
				masterResourceLimits.put("memory", "400Mi");
				masterResource.put("limits", masterResourceLimits);
				master.put("resources", masterResource);
				
				Map<String, Object> mastersecurityContext = new LinkedHashMap<String, Object>();
				mastersecurityContext.put("enabled", true);
				mastersecurityContext.put("fsGroup", "1001");
				mastersecurityContext.put("runAsUser", "1001");
				master.put("securityContext", mastersecurityContext);
				
				values.put("master", master);
				
				
				Map<String, Object> slave = new LinkedHashMap<String, Object>();
				
				Map<String, Object> slaveService = new LinkedHashMap<String, Object>();
				slaveService.put("type", "LoadBalancer");
				Map<String, Object> slaveServiceAnnotations = new LinkedHashMap<String, Object>();
				slaveServiceAnnotations.put("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type", "private");
				slaveService.put("annotations", masterServiceAnnotations);
				slave.put("service", slaveService);
				
				Map<String, Object> slaveResource = new LinkedHashMap<String, Object>();
				Map<String, Object> slaveResourceRequests = new LinkedHashMap<String, Object>();
				slaveResourceRequests.put("cpu", "100m");
				slaveResourceRequests.put("memory", "256Mi");
				slaveResource.put("request", slaveResourceRequests);
				Map<String, Object> slaveResourceLimits = new LinkedHashMap<String, Object>();
				slaveResourceLimits.put("cpu", "200m");
				slaveResourceLimits.put("memory", "400Mi");
				slaveResource.put("limits", masterResourceLimits);
				slave.put("resources", slaveResource);
				Map<String, Object> slaveAffinity = new LinkedHashMap<String, Object>();
				slave.put("affinity", slaveAffinity);
				
				values.put("slave", slave);
				
				Gson chartValues = new GsonBuilder().setPrettyPrinting().create();
				
				System.out.println("*********** : " + chartValues.toJson(values));

				valuesBuilder.setRaw(chartValues.toJson(values));
				
//				valuesBuilder.setRaw(inputJson);

				System.out.println(serviceName + " install start.");

				final Future<InstallReleaseResponse> releaseFuture = releaseManager.install(requestBuilder, chart);

				System.out.println(serviceName + " get release info.");

				final Release release = releaseFuture.get().getRelease();

				System.out.println(serviceName + " install success!");

				if (release != null) {
					System.out.println(release.toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		releaseManager.close();
		tiller.close();
	}
	
	private static String readFile(String file) throws IOException {
	    BufferedReader reader = new BufferedReader(new FileReader (file));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();
	    String         ls = System.getProperty("line.separator");

	    try {
	        while((line = reader.readLine()) != null) {
	            stringBuilder.append(line);
	            stringBuilder.append(ls);
	        }

	        return stringBuilder.toString();
	    } finally {
	        reader.close(); 
	    }
	}
}