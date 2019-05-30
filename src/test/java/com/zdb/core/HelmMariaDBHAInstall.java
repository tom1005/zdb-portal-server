package com.zdb.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import hapi.chart.ChartOuterClass.Chart;
import hapi.chart.ConfigOuterClass.Config.Builder;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.InstallReleaseRequest;
import hapi.services.tiller.Tiller.InstallReleaseResponse;
import hapi.services.tiller.Tiller.ListReleasesRequest;
import hapi.services.tiller.Tiller.ListReleasesResponse;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class HelmMariaDBHAInstall {
	private static final Logger logger = LoggerFactory.getLogger(HelmMariaDBHAInstall.class);

	public static void main(String[] args) throws Exception {

		final DefaultKubernetesClient client = K8SUtil.kubernetesClient();

		if (logger.isDebugEnabled()) {
			logger.debug("master url: {}", client.getMasterUrl());
			logger.debug("kubernetes api version: {}", client.getApiVersion());
		}

		final Tiller tiller = new Tiller(client);
		final ReleaseManager releaseManager = new ReleaseManager(tiller);
		
		
		
		hapi.services.tiller.Tiller.ListReleasesRequest.Builder newBuilder = ListReleasesRequest.newBuilder();
		
		Iterator<ListReleasesResponse> list = releaseManager.list(newBuilder.build());
		
		while(list.hasNext()) {
			ListReleasesResponse next = list.next();
			List<Release> releasesList = next.getReleasesList();
			
			for(Release r : releasesList) {
				
				System.out.println(r.getNamespace() +" / "+ r.getName());
			}
			
		}
		

		String serviceName = "zdb-maria-ha666";

		try {
			final URI uri = URI.create("http://169.56.70.222:31770/mariadb-4.1.2.tgz");
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}
			
			String raw = chart.getValues().getRaw();

			String chartName = chart.getMetadata().getName();
			
			final InstallReleaseRequest.Builder requestBuilder = InstallReleaseRequest.newBuilder();
			if (requestBuilder != null) {
				requestBuilder.setTimeout(300L);
				requestBuilder.setName(serviceName);
				requestBuilder.setNamespace("zdb-maria");
				

				requestBuilder.setWait(true);

				Builder valuesBuilder = requestBuilder.getValuesBuilder();
				
				String inputJson = readFile("/Users/namyounguk/git/zdb.rest.api/src/test/java/com/zdb/core/input_mariaha.json");
				
//				Gson gson = new Gson();
//				HashMap fromJson = gson.fromJson(inputJson, HashMap.class);
//				((Map)((Map)((Map)((Map)fromJson.get("master")).get("resources"))).get("requests")).put("cpu","300m");
//				
//				System.out.println(	((Map)((Map)((Map)((Map)fromJson.get("master")).get("resources"))).get("requests")).get("cpu"));
//				
//				gson.toJson(fromJson);
				
//				String inputJson = readFile("/home/nexcore/git2/zdb.rest.api/src/main/resources/values_template.json");
//				
//				inputJson = inputJson.replace("${rootUser.password}", ""); // configmap
//				inputJson = inputJson.replace("${db.user}", "zdbadmin");// configmap
//				inputJson = inputJson.replace("${db.password}", ""); // configmap
//				inputJson = inputJson.replace("${db.name}", "zdbdev");// input
//				inputJson = inputJson.replace("${master.persistence.storageClass}", "ibmc-block-silver");// configmap
//				inputJson = inputJson.replace("${master.persistence.size}", "10Gi");// input , configmap
//				inputJson = inputJson.replace("${master.resources.requests.cpu}", "100m");// input , configmap
//				inputJson = inputJson.replace("${master.resources.requests.memory}", "256Mi");// input
//				inputJson = inputJson.replace("${slave.persistence.storageClass}", "ibmc-block-silver");// configmap
//				inputJson = inputJson.replace("${slave.persistence.size}", "10Gi"); // input
//				inputJson = inputJson.replace("${slave.resources.requests.cpu}", "100m");// input
//				inputJson = inputJson.replace("${slave.resources.requests.memory}", "256Mi");// input
				
				valuesBuilder.setRaw(inputJson);

				System.out.println(serviceName + " install start.");

				final Future<InstallReleaseResponse> releaseFuture = releaseManager.install(requestBuilder, chart);
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
	
	public static void main3(String[] a) throws Exception {
		String inputJson = readFile("/home/nexcore/git2/zdb.rest.api/src/main/resources/values_template.json");
		
		inputJson = inputJson.replace("${rootUser.password}", ""); // configmap
		inputJson = inputJson.replace("${db.user}", "zdbadmin");// configmap
		inputJson = inputJson.replace("${db.password}", ""); // configmap
		inputJson = inputJson.replace("${db.name}", "zdbdev");// input
		inputJson = inputJson.replace("${master.persistence.storageClass}", "ibmc-file-silver");// configmap
		inputJson = inputJson.replace("${master.persistence.size}", "10Gi");// input , configmap
		inputJson = inputJson.replace("${master.resources.requests.cpu}", "100m");// input , configmap
		inputJson = inputJson.replace("${master.resources.requests.memory}", "256Mi");// input
		inputJson = inputJson.replace("${slave.persistence.storageClass}", "ibmc-file-silver");// configmap
		inputJson = inputJson.replace("${slave.persistence.size}", "10Gi"); // input
		inputJson = inputJson.replace("${slave.resources.requests.cpu}", "100m");// input
		inputJson = inputJson.replace("${slave.resources.requests.memory}", "256Mi");// input
		
		System.out.println(inputJson);
		
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