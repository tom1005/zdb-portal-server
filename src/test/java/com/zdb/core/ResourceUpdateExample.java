package com.zdb.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.core.io.ClassPathResource;

import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.util.K8SUtil;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceUpdateExample {

	public static void main(String[] args) {
		
		String serviceName = "zdb-106";

		String chartUrl = "http://169.56.71.107/mariadb-4.2.0.tgz";
		
		ReleaseManager releaseManager = null;
		try {
			final URI uri = URI.create(chartUrl);
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}

			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);
			
			final UpdateReleaseRequest.Builder requestBuilder = UpdateReleaseRequest.newBuilder();
			requestBuilder.setTimeout(300L);
			requestBuilder.setName(serviceName);
			requestBuilder.setWait(true);
			
			requestBuilder.setReuseValues(true);
			
//			requestBuilder.setRecreate(true);

			hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();

			InputStream is = new ClassPathResource("update_values_template.json").getInputStream();
			
			String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());
			
			inputJson = inputJson.replace("${master.resources.requests.cpu}", "150m");// input , *******   필수값  
			inputJson = inputJson.replace("${master.resources.requests.memory}", "256Mi");// input *******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.cpu}", "150m");// input*******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.memory}", "256Mi");// input *******   필수값 
			
			valuesBuilder.setRaw(inputJson);

			log.info(serviceName + " resource update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();
			
			if (release != null) {
				log.info(release.getConfig().getRaw());
			}

			log.info(serviceName + " resource update success!");
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			if (releaseManager != null) {
				try {
					releaseManager.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}
}
