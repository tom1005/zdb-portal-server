package com.zdb.core.event;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zdb.core.domain.DefaultExchange;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.KubernetesOperations;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 */
@Slf4j
public class ServiceDeleteListener extends ServiceEventAdapter {

	public ServiceDeleteListener(ZDBRepository repo, String txId, String namespace, String servcieName) {
		super(repo, txId, namespace, servcieName);
	}
	
	@Override
	public void run() {
		Watch podWatch = null;
		
		try{
			
			Exchange exchange = new DefaultExchange();
			exchange.setProperty(Exchange.TXID, txId);
			exchange.setProperty(Exchange.NAMESPACE, namespace);
			exchange.setProperty(Exchange.SERVICE_NAME, serviceName);
			exchange.setProperty(Exchange.META_REPOSITORY, metaRepository);
			
			List<Pod> podList = K8SUtil.getPodList(namespace, serviceName);
			if( podList != null && !podList.isEmpty()) {
				for(Pod pod : podList) {
					String podName = pod.getMetadata().getName();
					log.info("Pod name : {}", podName);
					CountDownLatch closeLatch = new CountDownLatch(1);
					
//					ZDBEventWatcher<Pod> eventWatcher = new ZDBEventWatcher<Pod>(
//							zdbRepository, 
//							closeLatch, 
//							KubernetesOperations.DELETE_POD_OPERATION, 
//							txId, 
//							namespace, 
//							serviceName, 
//							podName);
					
					ZDBEventWatcher<Pod> eventWatcher = new ZDBEventWatcher<Pod>(
							KubernetesOperations.DELETE_POD_OPERATION, 
							closeLatch,
							exchange);
					
					podWatch = K8SUtil.kubernetesClient().inNamespace(namespace).pods().withName(podName).watch(eventWatcher);
					
					closeLatch.await(150, TimeUnit.SECONDS);
				}
			}
		} catch(Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			if( podWatch != null) {
				podWatch.close();
			}
		}

	}
}