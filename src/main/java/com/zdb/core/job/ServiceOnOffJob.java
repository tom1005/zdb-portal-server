package com.zdb.core.job;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentList;
import io.fabric8.kubernetes.api.model.extensions.DoneableDeployment;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceOnOffJob extends JobAdapter {

	public ServiceOnOffJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		int toggle = param.getToggle();
		if (toggle == 1) {
			return "서비스 시작";
		} else {
			return "서비스 종료";
		}
	}

	@Override
	public JobKind getKind() {
		int toggle = param.getToggle();
		
		if (toggle == 1) {
			return JobKind.START_POD;
		} else {
			return JobKind.SHUTDOWN_POD;
		}
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String stsName = param.getStatefulsetName();
		
		int toggle = param.getToggle();
		
		if(stsName.endsWith("-master")) {
			try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();){
				MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = kubernetesClient.inNamespace(namespace).apps().statefulSets();
				
				StatefulSet statefulSet = statefulSets.withName(stsName).get();
				
				if(statefulSet == null) {
					log.error("StatefulSet : "+stsName + "는 등록된 서비스가 아닙니다.");
					done(JobResult.ERROR, stsName +" 서비스를 종료 오류.", null);
					return;
				}
				
				long replicas = statefulSet.getSpec().getReplicas();
				
				if(replicas == toggle) {
					
					if (toggle == 1) {
						log.info("skip : "+stsName + "서비스가 이미 동작 중입니다.");
						done(JobResult.ERROR, stsName + "서비스가 이미 동작 중입니다.", null);
					} else {
						log.info("skip : "+stsName + "서비스가 이미 종료 되었습니다.");
						done(JobResult.ERROR, stsName + "서비스가 이미 종료 되었습니다.", null);
					}
					
					return;
				} else {
//				StatefulSet newSts = stsBuilder.editSpec().withReplicas(toggle).endSpec().build();
//				statefulSets.createOrReplace(newSts);
					
					RestTemplate rest = K8SUtil.getRestTemplate();
					String idToken = K8SUtil.getToken();
					String masterUrl = K8SUtil.getMasterURL();
					
					HttpHeaders headers = new HttpHeaders();
					headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
					headers.set("Authorization", "Bearer " + idToken);
					headers.set("Content-Type", "application/json-patch+json");
					
					String data = "[{\"op\":\"add\",\"path\":\"/spec/replicas\",\"value\":"+toggle+"}]";
					
					HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);
					
					String endpoint = masterUrl + "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}/scale";
					ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, stsName);
					
					if (response.getStatusCode() == HttpStatus.OK) {
						if (toggle == 1) {
							done(JobResult.OK, stsName +" 서비스를 시작 했습니다.", null);
						} else {
							done(JobResult.OK, stsName +" 서비스를 종료 했습니다.", null);
						}
					} else {
						if (toggle == 1) {
							done(JobResult.ERROR, stsName +" 서비스를 시작 오류.", null);
						} else {
							done(JobResult.ERROR, stsName +" 서비스를 종료 오류.", null);
						}
						
						log.error(response.getBody());
						
					}
					
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				done(JobResult.ERROR, "", e);
			}
			
		} else if(stsName.endsWith("-slave")) {

			try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();){
				MixedOperation<Deployment, DeploymentList, DoneableDeployment, ScalableResource<Deployment, DoneableDeployment>> deployments = kubernetesClient.inNamespace(namespace).extensions().deployments();
				
				Deployment deploy = deployments.withName(stsName).get();
				
				if(deploy == null) {
					log.error("Deployment : "+stsName + "는 등록된 서비스가 아닙니다.");
					done(JobResult.ERROR, stsName +" 서비스를 종료 오류.", null);
					return;
				}
				
				long replicas = deploy.getSpec().getReplicas();
				
				if(replicas == toggle) {
					if (toggle == 1) {
						log.info("skip : "+stsName + "서비스가 이미 동작 중입니다.");
						done(JobResult.ERROR, stsName + "서비스가 이미 동작 중입니다.", null);
					} else {
						log.info("skip : "+stsName + "서비스가 이미 종료 되었습니다.");
						done(JobResult.ERROR, stsName + "서비스가 이미 종료 되었습니다.", null);
					}
					
					return;
				} else {
					
					RestTemplate rest = K8SUtil.getRestTemplate();
					String idToken = K8SUtil.getToken();
					String masterUrl = K8SUtil.getMasterURL();
					
					HttpHeaders headers = new HttpHeaders();
					headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
					headers.set("Authorization", "Bearer " + idToken);
					headers.set("Content-Type", "application/json-patch+json");
					
					String data = "[{\"op\":\"add\",\"path\":\"/spec/replicas\",\"value\":"+toggle+"}]";
					
					HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);
					
					String endpoint = masterUrl + "/apis/extensions/v1beta1/namespaces/{namespace}/deployments/{name}/scale";
					ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, stsName);
					
					if (response.getStatusCode() == HttpStatus.OK) {
						if (toggle == 1) {
							done(JobResult.OK, stsName +" 서비스를 시작 했습니다.", null);
						} else {
							done(JobResult.OK, stsName +" 서비스를 종료 했습니다.", null);
						}
					} else {
						if (toggle == 1) {
							done(JobResult.ERROR, stsName +" 서비스를 시작 오류.", null);
						} else {
							done(JobResult.ERROR, stsName +" 서비스를 종료 오류.", null);
						}
						
						log.error(response.getBody());
						
					}
					
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				done(JobResult.ERROR, "", e);
			}
			
		
		}
		
		
	}
}
