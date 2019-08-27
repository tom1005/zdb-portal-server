package com.zdb.core.job;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.zdb.core.domain.RequestEventCode;
import com.zdb.core.domain.Result;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.PodManager;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnableAutofailover extends JobAdapter {

	public EnableAutofailover(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		return RequestEventCode.AUTOFAILOVER_SET.getDesc();
	}

	@Override
	public JobKind getKind() {
		return JobKind.ENABLE_AUTO_FAILOVER;
	}

	@Override
	public void run() {
		String namespace = param.getNamespace();
		String stsName = param.getStatefulsetName();
		int enable = param.getToggle();
		
		try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();){
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = kubernetesClient.inNamespace(namespace).apps().statefulSets();
			
			StatefulSet sts = statefulSets.withName(stsName).get();
			
			if(sts == null) {
				log.error("StatefulSet : "+stsName + "는 등록된 서비스가 아닙니다.");
				done(JobResult.ERROR, stsName + "Auto Failover 설정 오류.", null);
				return;
			}
			
			
			String name = sts.getMetadata().getName();
			
			String enableStr = enable == 1 ? "On" : "Off";
			String message = "Auto Failover 설정 변경. ["+enableStr+"]";
			
			///////////////////////////////////////////////////////
			if(enable == 1) {
				Result addAutoFailover = addAutoFailover(kubernetesClient, sts);
				
				if(!addAutoFailover.isOK()) {
//					return addAutoFailover;
					done(JobResult.ERROR, stsName + " - Auto Failover 설정 변경 처리중 오류가 발생 했습니다.", null);
					return;
				}
			}
			
			long s = System.currentTimeMillis();
			
			log.info("Pod "+name+"-0 ready 상태 대기중..." );
			while(true) {
				Thread.sleep(1000);
				
				Pod pod = kubernetesClient.inNamespace(namespace).pods().withName(name+"-0").get();
				boolean ready = PodManager.isReady(pod);
				
				if(ready) {
					log.info("Pod "+name+"-0 ready ok..." );
					break;
				}
				
				if ((System.currentTimeMillis() - s) > 2 * 60 * 1000) {
					log.error("failover enable timeout");
					break;
				}
			}
			
			log.info(namespace +" > "+name + " add label : zdb-failover-enable=true");
			
			RestTemplate rest = K8SUtil.getRestTemplate();
			String idToken = K8SUtil.getToken();
			String masterUrl = K8SUtil.getMasterURL();

			HttpHeaders headers = new HttpHeaders();
			List<MediaType> mediaTypeList = new ArrayList<MediaType>();
			mediaTypeList.add(MediaType.APPLICATION_JSON);
			headers.setAccept(mediaTypeList);
			headers.add("Authorization", "Bearer " + idToken);
			headers.set("Content-Type", "application/json-patch+json");
			
			String value = enable == 1 ? "true" : "false";
			
			String data = "[{\"op\":\"add\",\"path\":\"/metadata/labels/zdb-failover-enable\",\"value\":\""+value+"\"}]";
		    
			HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

			String endpoint = masterUrl + "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}";
			ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, name);

			if (response.getStatusCode() == HttpStatus.OK) {
				log.info(namespace +" > "+name + " add label : zdb-failover-enable=true ok..." );
				done(JobResult.OK, stsName +" " + message, null);
			} else {
				done(JobResult.ERROR, stsName + " " + message +" 처리중 오류가 발생 했습니다.", null);
			}
		} catch (Exception e) {
			e.printStackTrace();
			done(JobResult.ERROR, "", e);
		}
		
	}
	
	/**
	 * Master/Slave 로 구성된 인스턴스
	 * StatefulSet master 에 edit
	 * 	- report_status.sh 을 실행 할 수 있도록 configmap 등록
	 * 	- spec>template>spec>containers>lifecycle : shell command 등록
	 * 	- spec>template>spec>containers>volumeMounts  :  report-status 추가 
	 * 	- spec>template>spec>volumes :  report-status 추가
	 * 	- label 추가 (zdb-failover-enable=true)
	 * 
	 */
	public Result addAutoFailover(DefaultKubernetesClient client, StatefulSet sts) throws Exception {
		
		
		Result result = null;
		StringBuffer resultMsg = new StringBuffer();
		boolean status = false;
		
		try{
			// * - report_status.sh 을 실행 할 수 있도록 configmap 등록
			String namespace = sts.getMetadata().getNamespace();
			String stsName = sts.getMetadata().getName();
			
			MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> configMaps = client.inNamespace(namespace).configMaps();

			String cmName = "report-status";
			ConfigMap configMap = configMaps.withName(cmName).get();

			if (configMap == null) {
				log.info("Create configmap : " + namespace +" > "+ cmName);
				
				InputStream is = new ClassPathResource("mariadb/report_status.template").getInputStream();

				String temp = IOUtils.toString(is, StandardCharsets.UTF_8.name());

				Map<String, String> data = new HashMap<>();
				data.put("report_status.sh", temp);

				Resource<ConfigMap, DoneableConfigMap> configMapResource = client.configMaps().inNamespace(namespace).withName(cmName);

				configMap = configMapResource.createOrReplace(new ConfigMapBuilder().withNewMetadata().withName(cmName).endMetadata().addToData(data).build());
			
				log.info("Created configmap : " + namespace +" > "+ cmName);
				
				status = true;
			} else {
				log.info("Exist configmap : " + namespace +" > "+ configMap.getMetadata().getName());
			}

//			 * 	- spec>template>spec>containers>lifecycle : shell command 등록
//			 * 	- spec>template>spec>containers>volumeMounts  :  report-status 추가 
//			 * 	- spec>template>spec>volumes :  report-status 추가
//			 * 	- label 추가 (zdb-failover-enable=true)
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = 
					client.inNamespace(namespace).apps().statefulSets();
			
			{				
//				Map<String, String> labels = sts.getMetadata().getLabels();
				
				log.info("Start update statefulset. " + namespace +" > "+ stsName);
				
				io.fabric8.kubernetes.api.model.PodSpec spec = sts.getSpec().getTemplate().getSpec();
				List<Container> containers = spec.getContainers();
				int mariadbIndex = -1;
				boolean existVolumeMount = false;
				boolean existVolume = false;
				boolean existCommand = false;
				
				for (int i = 0; i < containers.size(); i++) {
					Container container = containers.get(i);
					
					String name = container.getName();
					if("mariadb".equals(name)) {
						mariadbIndex = i;
						
						List<VolumeMount> volumeMounts = container.getVolumeMounts();
						for (VolumeMount vm : volumeMounts) {
							if("report-status".equals(vm.getName())) {
								existVolumeMount = true;
								break;
							}
						}
						
						try {
							int size = container.getLifecycle().getPostStart().getExec().getCommand().size();
							existCommand = size > 0 ? true : false;
						} catch (Exception e) {
							existCommand = false;
						}
						
						break;
					}
				}
				
				if(mariadbIndex == -1) {
					String msg = "mariadb container 가 존재하지 않습니다. ["+namespace+" > "+stsName+"]";
					log.error(msg);
					
					result = new Result("", Result.ERROR, msg);
					return result;
				}

				List<Volume> volumes = spec.getVolumes();
				for (Volume v : volumes) {
					if("report-status".equals(v.getName())) {
						existVolume = true;
						break;
					}
				}
				
				StatefulSetBuilder builder = new StatefulSetBuilder(sts);

				boolean buildFlag = false;
				
//				String labelKey = "zdb-failover-enable";
//				if(!labels.containsKey(labelKey) || !"true".equals(labels.get(labelKey))) {
//					labels.put(labelKey, "true");
//					
//					builder.editMetadata().withLabels(labels).endMetadata();
//					buildFlag = true;
//					
//				}
//				
//				log.info("withLabels : " + stsName);
				
				if(!existCommand) {
					String[] command = new String[] {
							"/bin/sh",
							"-c",
							"/usr/bin/nohup /report_status.sh 1>/tmp/report.log 2>/dev/null &"
					};
					
					builder.editSpec()
					.editTemplate().editSpec()
					.editContainer(mariadbIndex)
					.editOrNewLifecycle()
					.editOrNewPostStart()
					.editOrNewExec()
					.addToCommand(command)
					.endExec()
					.endPostStart()
					.endLifecycle()
					.endContainer()
					.endSpec()
					.endTemplate()
					.endSpec();

					buildFlag = true;
					log.info("addToCommand : " + stsName);
				} else {
					log.info("existCommand : " + namespace +" > "+ stsName);
				}
				
				if(!existVolumeMount) {
					VolumeMount vm = new VolumeMount();
					vm.setMountPath("/report_status.sh");
					vm.setName("report-status");
					vm.setSubPath("report_status.sh");
					
					builder
					.editSpec()
					.editTemplate()
					.editSpec()
					.editContainer(mariadbIndex)
					.addToVolumeMounts(vm)
					.endContainer()
					.endSpec()
					.endTemplate()
					.endSpec();
					
					buildFlag = true;
					log.info("addToVolumeMounts : " + stsName);
				} else {
					log.info("existVolumeMount : " + namespace +" > "+ stsName);
				}
				
				if(!existVolume) {
					Volume volume = new Volume();
					
					ConfigMapVolumeSource cmvs = new ConfigMapVolumeSource();
					cmvs.setDefaultMode(493); // 0755
					cmvs.setName("report-status");
					volume.setConfigMap(cmvs);
					volume.setName("report-status");
					
					builder
					.editSpec()
					.editTemplate()
					.editSpec()
					.addToVolumes(volume)
					.endSpec()
					.endTemplate()
					.endSpec();
					
					buildFlag = true;
					log.info("addToVolumes : " + stsName);
				} else {
					log.info("existVolume : " + namespace +" > "+ stsName);
				}

				if (buildFlag) {
					status = true;
					StatefulSet newSvc = builder.build();

					statefulSets.createOrReplace(newSvc);
					log.info("End statefulset update. " + namespace +" > "+ stsName);
					resultMsg.append("Update statefulset. " + namespace +" > "+ stsName).append("\n");
				} else {
					log.info("Skip update statefulset. " + namespace +" > "+ stsName);
				}
				
			}
			
			if(!status) {
				resultMsg.append("이미 설정된 서비스 입니다. " + namespace +" > "+ stsName);
			}
					
			result = new Result("", Result.OK , "Auto Failover 설정 등록 완료.");
			result.putValue(Result.HISTORY, resultMsg.toString());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result("", Result.ERROR , resultMsg.toString(), e);
		} 
		return result;
	
	}
}
