package com.zdb.core;

import java.io.IOException;
import java.util.List;

import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.domain.ZDBType;
import com.zdb.core.util.K8SUtil;

import hapi.release.ReleaseOuterClass.Release;
import hapi.release.StatusOuterClass.Status.Code;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class HelmList {
	private static final Logger logger = LoggerFactory.getLogger(HelmList.class);

	public static void main(String[] args) throws IOException, InterruptedException, Exception {

		boolean contains = ZDBType.contains("mariadb");
		
		System.out.println(contains);
		
//		Code[] c = new Code[] {Code.DELETED, Code.DEPLOYED, Code.FAILED, Code.PENDING_INSTALL, Code.PENDING_UPGRADE, Code.PENDING_ROLLBACK};
//		List<Release> releaseList = K8SUtil.getReleaseList(Code.values());
		Code[] codes = new Code[] { Code.UNKNOWN, Code.DEPLOYED, Code.FAILED, Code.DELETED, Code.DELETING, Code.PENDING_INSTALL, Code.PENDING_UPGRADE, Code.PENDING_ROLLBACK };
		List<Release> releaseList = K8SUtil.getReleaseList();
		
		for (Release release : releaseList) {
			
			
			System.out.println(release.getName() +" / "+ release.getInfo().getStatus().getCode().toString());
		}

		final DefaultKubernetesClient client = K8SUtil.kubernetesClient();

		if (logger.isDebugEnabled()) {
			logger.debug("master url: {}", client.getMasterUrl());
			logger.debug("kubernetes api version: {}", client.getApiVersion());
		}

		final Tiller tiller = new Tiller(client);
		final ReleaseManager releaseManager = new ReleaseManager(tiller);
		
		
//
//		final ListReleasesRequest.Builder requestBuilder = ListReleasesRequest.newBuilder();
//
////    case 0: return UNKNOWN;
////    case 1: return DEPLOYED;
////    case 2: return DELETED;
////    case 3: return SUPERSEDED;
////    case 4: return FAILED;
////    case 5: return DELETING;
////    case 6: return PENDING_INSTALL;
////    case 7: return PENDING_UPGRADE;
////    case 8: return PENDING_ROLLBACK;
//		requestBuilder.addStatusCodes(Code.DELETED);
//		requestBuilder.addStatusCodes(Code.DEPLOYED);
//		requestBuilder.addStatusCodes(Code.FAILED);
//		
////		requestBuilder.setStatusCodes(0, Status.Code.DELETED);
////		requestBuilder.setStatusCodes(1, Status.Code.DEPLOYED);
//		
//		
//		List<Code> statusCodesList = requestBuilder.getStatusCodesList();
//		for (Code code : statusCodesList) {
//			System.out.println(code);
//		}
//		final Iterator<ListReleasesResponse> releaseFuture = releaseManager.list(requestBuilder.build());
//		assert releaseFuture != null;
//			
//		while (releaseFuture.hasNext()) {
//			ListReleasesResponse ent = releaseFuture.next();
//			System.out.println(ent.getReleasesCount());
//			System.out.println(ent.getTotal());
//			List<Release> releaseList = ent.getReleasesList();
//
//			for(Release release : releaseList) {
//				logger.info("======================");
//				logger.info("{}", release.getName());
//				logger.info(". CHART_NAME: {}", release.getChart().getMetadata().getName());
//				logger.info(". DEP_CHART_VERSION: {}", release.getChart().getMetadata().getVersion());
//				logger.info(". DEP_NAMESPACE: {}", release.getNamespace());
//				logger.info(". DEP_STATUS: {}", release.getInfo().getStatus().getCode().toString());
//				logger.info(". FirstDeployed: {}", new Date(release.getInfo().getFirstDeployed().getSeconds()*1000L).toString());
//				logger.info(". LastDeployed: {}", new Date(release.getInfo().getLastDeployed().getSeconds()*1000L).toString());
//				logger.info("======================");
//			}
//		}
//		
//		releaseManager.close();
//		tiller.close();
	}
}