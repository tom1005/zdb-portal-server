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

import com.zdb.core.util.K8SUtil;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.release.StatusOuterClass.Status.Code;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigUpdateExample {

	public static void main(String[] args) {
		
		String serviceName = "zdb-106";
		
		

		String chartUrl = "http://169.56.71.107/mariadb-4.2.0.tgz";
		
		ReleaseManager releaseManager = null;
		try {
//			Status.Code.UNKNOWN
//			Status.Code.DEPLOYED
//			Status.Code.DELETED
//			Status.Code.SUPERSEDED
//			Status.Code.FAILED
//			Status.Code.DELETING
//			Status.Code.PENDING_INSTALL
//			Status.Code.PENDING_UPGRADE
//			Status.Code.PENDING_ROLLBACK
			Release release2 = K8SUtil.getRelease("zdb-dev-test2", serviceName, new Code[] {Code.UNKNOWN, Code.DEPLOYED, Code.FAILED});
			
			System.out.println(release2.getConfig().getRaw());
			
			
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
			
			requestBuilder.setRecreate(true);

			hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();

			
			InputStream is = new ClassPathResource("config_values_template.json").getInputStream();
			
			String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());
			
			valuesBuilder.setRaw(inputJson);

			log.info(serviceName + " update start.");

			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();
			
			if (release != null) {
				log.info(release.getConfig().getRaw());
			}

			log.info(serviceName + " config update success!");
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
