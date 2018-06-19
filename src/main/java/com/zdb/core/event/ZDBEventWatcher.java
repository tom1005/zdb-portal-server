package com.zdb.core.event;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.google.gson.Gson;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.KubernetesOperations;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 * @param <T>
 */
@Slf4j
public class ZDBEventWatcher<T> implements Watcher<T> {
	ZDBRepository zdbRepository;

	CountDownLatch closeLatch;
	
	String operation;
	
	Exchange exchange;
	
	String watchName;
	
	String namespace;
	
	String txId;
	
	String serviceName;
	
	public ZDBEventWatcher(String operation, CountDownLatch closeLatch, Exchange exchange) {
		this.operation = operation;
		this.closeLatch = closeLatch;
		this.exchange = exchange;
		
		this.zdbRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);
		
		this.watchName = exchange.getProperty(Exchange.SERVICE_NAME, String.class);
		this.namespace = exchange.getProperty(Exchange.NAMESPACE, String.class);
		this.txId 	   = exchange.getProperty(Exchange.TXID, String.class);
		this.serviceName = exchange.getProperty(Exchange.SERVICE_NAME, String.class);
	}

	@Override
	public void eventReceived(Action action, Object resource) {
		String resourceLos = new Gson().toJson(resource);
		log.info("Action : {}\nOperation : {}\nNamespace : {}\nServiceName : {}\nResourceLog : {}", action.name(), operation, namespace, serviceName, resourceLos);
		
		if (resource instanceof PersistentVolumeClaim) {
			PersistentVolumeClaim pvc = (PersistentVolumeClaim) resource;
			

			if(KubernetesOperations.CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION.equals(operation)) {
				createPersistentVolumeClaimOperation(pvc);
			} else if(KubernetesOperations.DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION.equals(operation)) {
				deletePersistentVolumeClaimOperation(action, pvc);
			}
		} else if (resource instanceof Namespace) {
			Namespace ns = (Namespace) resource;
			
			if(KubernetesOperations.CREATE_NAMESPACE_OPERATION.equals(operation)) {
				createNamespaceOperation(ns);				
			}
		} else if (resource instanceof Pod) {
			Pod pod = (Pod) resource;

			if (KubernetesOperations.DELETE_POD_OPERATION.equals(operation)) {
				deletePodOperation(action, pod);
			} else if (KubernetesOperations.CREATE_POD_OPERATION.equals(operation)) {
				createPodOperation(pod);
			}
		} else if (resource instanceof Deployment) {

		} else if (resource instanceof Service) {

		}

	}
	
	private void createPodOperation(Pod pod) {
		String resourceLos = new Gson().toJson(pod);

		PodStatus status = pod.getStatus();
		String phase = status.getPhase();
		
		String reason = status.getReason();
		String message = status.getMessage();
		
		boolean isInitialized = false;
		boolean isReady = false;
		boolean isPodScheduled = false;
		
		List<PodCondition> conditions = status.getConditions();
		for (PodCondition condition : conditions) {
			String podConditionMessage = condition.getMessage();
			String podConditionReason = condition.getReason();
			
			if ("Initialized".equals(condition.getType())) {
				isInitialized = Boolean.valueOf(condition.getStatus());
			}

			if ("Ready".equals(condition.getType())) {
				isReady = Boolean.valueOf(condition.getStatus());
			}

			if ("PodScheduled".equals(condition.getType())) {
				isPodScheduled = Boolean.valueOf(condition.getStatus());
			}
		}
		
		List<ContainerStatus> containerStatuses = status.getContainerStatuses();
		
		boolean isContainerReady = false;
		for (ContainerStatus containerStatus : containerStatuses) {
			Boolean ready = containerStatus.getReady();
			if(!ready.booleanValue()) {
				isContainerReady =false;
				break;
			} else {
				isContainerReady = true;
			}
		}

		boolean isSuccess = false;
		if (isInitialized && isReady && isPodScheduled && isContainerReady) {
			isSuccess = true;
		} 
		
		String releaseName = pod.getMetadata().getLabels().get("release");

		RequestEvent requestEvent = new RequestEvent();
		requestEvent.setTxId(txId);
		requestEvent.setNamespace(pod.getMetadata().getNamespace());
		requestEvent.setServiceName(serviceName);
		requestEvent.setOpertaion(operation);
		requestEvent.setResourceLog(resourceLos);

		if ("Running".equals(phase) && isSuccess && watchName.equals(releaseName)) {

			requestEvent.setStatus(closeLatch.getCount() > 1 ? IResult.RUNNING : IResult.OK);
			requestEvent.setEndTIme(new Date(System.currentTimeMillis()));
			requestEvent.setResultMessage(watchName + " create success.");
			requestEvent.setStatusMessage("Pod 생성 성공");

			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, requestEvent);

			if (closeLatch != null) {
				closeLatch.countDown();
			}
		} else {
			requestEvent.setStatus(IResult.RUNNING);
			
			StringBuffer sb = new StringBuffer();
			sb.append("Pod status : %s;");
			sb.append("Pod reason : %s;");
			sb.append("Pod message : %s;");
			sb.append("PodCondition Initialized : %s;");
			sb.append("PodCondition Ready : %s;");
			sb.append("PodCondition PodScheduled : %s;");
			sb.append("ContainerStatus Ready : %s;");
			
			requestEvent.setResultMessage(String.format(sb.toString(), phase, reason, message, isInitialized, isReady, isPodScheduled, isContainerReady));
			requestEvent.setStatusMessage("Pod 생성 중.");

			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, requestEvent);
		}
	}

	private void createPersistentVolumeClaimOperation(PersistentVolumeClaim pvc) {
		String resourceLos = new Gson().toJson(pvc);

		String phase = pvc.getStatus().getPhase();

		if ("Bound".equals(phase) && namespace.equals(pvc.getMetadata().getNamespace()) && watchName.equals(pvc.getMetadata().getName())) {
			RequestEvent requestEvent = new RequestEvent();

			requestEvent.setTxId(txId);
			requestEvent.setNamespace(namespace);
			requestEvent.setServiceName(serviceName);
			requestEvent.setOpertaion(operation);
			requestEvent.setResourceLog(resourceLos);
			requestEvent.setStatus(IResult.OK);
			requestEvent.setEndTIme(new Date(System.currentTimeMillis()));
			requestEvent.setResultMessage(watchName + " create success.");
			requestEvent.setStatusMessage("PVC 생성 성공");

			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, requestEvent);

			log.info("!!!" + new Gson().toJson(requestEvent));

			if (closeLatch != null) {
				closeLatch.countDown();
			}
		}
	}

	private void deletePersistentVolumeClaimOperation(Action action, PersistentVolumeClaim pvc) {
		String resourceLos = new Gson().toJson(pvc);
		
		RequestEvent requestEvent = new RequestEvent();
		
		requestEvent.setTxId(txId);
		requestEvent.setNamespace(namespace);
		requestEvent.setServiceName(serviceName);
		requestEvent.setOpertaion(operation);
		requestEvent.setResourceLog(resourceLos);

		if (action == Action.DELETED && namespace.equals(pvc.getMetadata().getNamespace()) && watchName.equals(pvc.getMetadata().getName())) {
			requestEvent.setStatus(IResult.OK);
			requestEvent.setStatusMessage("PVC 삭제 성공");
			requestEvent.setResultMessage(watchName + " delete success.");
			requestEvent.setEndTIme(new Date(System.currentTimeMillis()));

			log.info("!!!" + new Gson().toJson(requestEvent));

			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, requestEvent);

			if (closeLatch != null) {
				closeLatch.countDown();
			}
		}
	}

	private void deletePodOperation(Action action, Pod pod) {
		String resourceLos = new Gson().toJson(pod);
		
		RequestEvent requestEvent = new RequestEvent();
		
		requestEvent.setTxId(txId);
		requestEvent.setNamespace(namespace);
		requestEvent.setServiceName(serviceName);
		requestEvent.setOpertaion(operation);
		requestEvent.setResourceLog(resourceLos);

		if (namespace.equals(pod.getMetadata().getNamespace()) && watchName.equals(pod.getMetadata().getName())) {

			if (action == Action.DELETED) {
				requestEvent.setStatus(IResult.OK);
				requestEvent.setStatusMessage("Pod 삭제 성공");
				requestEvent.setResultMessage("Pod delete success. [" + watchName + "]");
				requestEvent.setEndTIme(new Date(System.currentTimeMillis()));

				closeLatch.countDown();
			} else {
				requestEvent.setStatus(IResult.RUNNING);
				requestEvent.setUpdateTime(new Date(System.currentTimeMillis()));
				requestEvent.setResultMessage("Pod delete. [" + watchName + "]");
			}

			log.info("!!!" + new Gson().toJson(requestEvent));

			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, requestEvent);

		}
	}

	private void createNamespaceOperation(Namespace ns) {
		String resourceLos = new Gson().toJson(ns);
		
		RequestEvent requestEvent = new RequestEvent();
		
		requestEvent.setTxId(txId);
		requestEvent.setNamespace(namespace);
		requestEvent.setServiceName(serviceName);
		requestEvent.setOpertaion(operation);
		requestEvent.setResourceLog(resourceLos);

		String status = ns.getStatus().getPhase();
		if ("Active".equals(status) && ns.getMetadata().getName().equals(namespace)) {

			log.info("!!!" + new Gson().toJson(requestEvent));
			requestEvent.setStatus(IResult.OK);
			requestEvent.setStatusMessage("Namespace 생성 완료");
			requestEvent.setResultMessage(watchName + "create namespace success.");
			requestEvent.setEndTIme(new Date(System.currentTimeMillis()));
			
			ZDBRepositoryUtil.saveRequestEvent(zdbRepository, requestEvent);

			if (closeLatch != null) {
				closeLatch.countDown();
			}
		}
	}

	@Override
	public void onClose(KubernetesClientException cause) {
		RequestEvent requestEvent = new RequestEvent();

		requestEvent.setTxId(txId);
		requestEvent.setNamespace(namespace);
		requestEvent.setServiceName(serviceName);
		requestEvent.setOpertaion(operation);
		requestEvent.setStatusMessage("watcher onClose");
		requestEvent.setEndTIme(new Date(System.currentTimeMillis()));

		if (cause == null) {
			requestEvent.setStatus(IResult.OK);
		} else {
			requestEvent.setResultMessage(cause.getMessage());
			requestEvent.setStatus(IResult.ERROR);
		}

		log.info("!!!" + new Gson().toJson(requestEvent));

		ZDBRepositoryUtil.saveRequestEvent(zdbRepository, requestEvent);

		if (closeLatch != null) {
			closeLatch.countDown();
		}
	}

	
}