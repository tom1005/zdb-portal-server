package com.zdb.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Future;

import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;

import com.zdb.core.util.K8SUtil;

import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.GetHistoryRequest;
import hapi.services.tiller.Tiller.GetHistoryResponse;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HelmHistoryExample {

	public static void main(String[] args) {
		
//		String serviceName = "zdb-115";
		String serviceName = "zdb-307";

		String chartUrl = "http://169.56.71.107/mariadb-4.2.0.tgz";
		
		ReleaseManager releaseManager = null;
		try {
//			final URI uri = URI.create(chartUrl);
//			final URL url = uri.toURL();
//			Chart.Builder chart = null;
//			try (final URLChartLoader chartLoader = new URLChartLoader()) {
//				chart = chartLoader.load(url);
//			}

			DefaultKubernetesClient client = K8SUtil.kubernetesClient();

			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);
			
			GetHistoryRequest.Builder history = GetHistoryRequest.newBuilder();
			history.setName(serviceName);
			history.setMax(100);
			
			Future<GetHistoryResponse> releaseFuture = releaseManager.getHistory(history.build());
			GetHistoryResponse getHistoryResponse = releaseFuture.get();
			int releasesCount = getHistoryResponse.getReleasesCount();
			
			for (int i= 0; i < releasesCount; i++) {
				 Release release = getHistoryResponse.getReleases(i);
				
				 if (release != null) {
				
					 log.info(">>>>>>>>>>>> "+release.getName() +" / "+release.getInfo().getStatus().getCode() +" / "+new Date(release.getInfo().getFirstDeployed().getSeconds()*1000L).toString() +"/"+ new Date(release.getInfo().getLastDeployed().getSeconds()*1000L).toString());
				 log.error(release.getConfig().getRaw());
				 }
			}
			
			

//			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
//			final Release release = releaseFuture.get().getRelease();
//			
//			if (release != null) {
//				log.info(release.getConfig().getRaw());
//			}

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
