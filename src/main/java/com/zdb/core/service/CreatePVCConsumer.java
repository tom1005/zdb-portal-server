//package com.zdb.core.service;
//
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
//import com.google.gson.Gson;
//import com.zdb.core.domain.Exchange;
//import com.zdb.core.domain.IResult;
//import com.zdb.core.domain.KubernetesOperations;
//import com.zdb.core.domain.PersistenceSpec;
//import com.zdb.core.domain.RequestEvent;
//import com.zdb.core.domain.Result;
//import com.zdb.core.event.ZDBEventWatcher;
//import com.zdb.core.repository.ZDBRepository;
//import com.zdb.core.repository.ZDBRepositoryUtil;
//import com.zdb.core.util.K8SUtil;
//
//import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
//import io.fabric8.kubernetes.client.Watch;
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//public class CreatePVCConsumer implements Runnable {
//		private BlockingQueue<Exchange> queue;
//		String workerId;
//
//		public CreatePVCConsumer(String id, BlockingQueue<Exchange> queue) {
//			this.queue = queue;
//			this.workerId = id;
//		}
//
//		@Override
//	public void run() {
//		while (true) {
//			try {
//				Thread.sleep(1000);
//				Exchange exchange = (Exchange) queue.take();
//
//				String txId = exchange.getProperty(Exchange.TXID, String.class);
//				String namespace = exchange.getProperty(Exchange.NAMESPACE, String.class);
//				String serviceName = exchange.getProperty(Exchange.SERVICE_NAME, String.class);
//				String chartUrl = exchange.getProperty(Exchange.CHART_URL, String.class);
//				ZDBRepository metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);
//				
//				PersistenceSpec persistenceSpec = exchange.getProperty(Exchange.PERSISTENCESPEC, PersistenceSpec.class);
//
//				if (persistenceSpec != null) {
//					final CountDownLatch closeLatch = new CountDownLatch(1);
//
//					ZDBEventWatcher<PersistentVolumeClaim> eventWatcher = new ZDBEventWatcher<PersistentVolumeClaim>(KubernetesOperations.CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION, closeLatch, exchange);
//
//					Watch watch = K8SUtil.kubernetesClient().inNamespace(namespace).persistentVolumeClaims().withName(persistenceSpec.getExistingClaim()).watch(eventWatcher);
//
//
//					// PVC 생성 처리.
//					Result result = K8SUtil.doCreatePersistentVolumeClaim(txId, namespace, persistenceSpec);
//
//					RequestEvent event = new RequestEvent();
//					event.setTxId(txId);
//					event.setResultMessage(result.getMessage());
//
//					if( result.isOK() ) {
//						event.setStatus(IResult.RUNNING);
//						event.setStatusMessage("PVC 생성중. ");
//						
//						ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//						closeLatch.await(150, TimeUnit.SECONDS);
//					} else {
//						event.setStatus(IResult.ERROR);
//						event.setStatusMessage("PVC 생성오류");
//						
//						ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//					}
//					
//					
//					if( closeLatch.getCount() > 0) {
//						event.setStatus(IResult.ERROR);
//						event.setResultMessage("PVC 생성...timeout");
//					
//						ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
//					} 
//					watch.close();
//					
//				} else {
//					log.error("비정상 요청...");
//				}
//
//			} catch (Exception e) {
//				log.error(e.getMessage(), e);
//			}
//		}
//	}
//	}