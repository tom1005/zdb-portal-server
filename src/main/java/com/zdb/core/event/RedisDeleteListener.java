package com.zdb.core.event;

import java.util.List;
import java.util.concurrent.TimeoutException;

import com.zdb.core.domain.Exchange;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 */
@Slf4j
public class RedisDeleteListener extends ServiceEventAdapter {

	public RedisDeleteListener(ZDBRepository repo, String txId, String namespace, String servcieName) {
		super(repo, txId, namespace, servcieName);
	}

	public RedisDeleteListener(Exchange exchange) {
		super(exchange);
	}
	
	@Override
	public void run() {
		try {
			startTime = System.currentTimeMillis();
			
//			Deployments (2)
//			Pods (6)
//			Replica Sets (2) - 개수 및 포함된 pod 의 상태로 확인
//			Services (3)

			while (true) {
				if (System.currentTimeMillis() - startTime > expireTime) {
					String msg = "시간 초과 [" + expireTime + "(초)]";
					log.warn(msg);

					failure(new TimeoutException(msg), msg);
					break;
				}
				
				try {
					
					List<Pod> podList = K8SUtil.getPodList(namespace, serviceName);
					List<Deployment> deploymentList = K8SUtil.getDeploymentListByReleaseName(namespace, serviceName);
					List<ReplicaSet> replicaSetList = K8SUtil.getReplicaSets(namespace, serviceName);
					List<Service> serviceList = K8SUtil.getServices(namespace, serviceName);
					
					if(podList.size() == 0 && deploymentList.size() == 0 && replicaSetList.size() == 0 && serviceList.size() == 0) {
							log.info("Deployment : {}, Pod : {} - success!", deploymentList.size(), podList.size());
							done(String.format("Deployment : %d, Pod : %d - success!", deploymentList.size(), podList.size()));
							break;
					} 
					
					log.info("Deployment : {}, Pod : {} - 삭제중...", deploymentList.size(), podList.size());
					running(String.format("Deployment : %d, Pod : %d - 삭제중!", deploymentList.size(), podList.size()));
					
				} catch (Exception e) {
					failure(e, e.getMessage());
				}
				Thread.sleep(5000);
			}

		} catch (KubernetesClientException e) {
			failure(e, e.getMessage());
		} catch (InterruptedException e) {
			failure(e, e.getMessage());
		} catch (Exception e) {
			failure(e, e.getMessage());
		}
	}
}