package com.zdb.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

import com.zdb.core.domain.Result;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.Handler;
import io.fabric8.kubernetes.api.model.Lifecycle;
import io.fabric8.kubernetes.api.model.PodSpec;
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
public class CreateCMExam {

	public static void main(String[] args) {
		String namespace = "zdb-test2";
		String serviceName = "zdb-test2-mha";
		
		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {

//			 * 	- report_status.sh 을 실행 할 수 있도록 configmap 등록
//			 * 	- spec>template>spec>containers>lifecycle : shell command 등록
//			 * 	- spec>template>spec>containers>volumeMounts  :  report-status 추가 
//			 * 	- spec>template>spec>volumes :  report-status 추가
//			 * 	- label 추가 (zdb-failover-enable=true)
			
			
			{
				// * - report_status.sh 을 실행 할 수 있도록 configmap 등록

				MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> configMaps = client.inNamespace(namespace).configMaps();

				String name = "report-status";
				ConfigMap configMap = configMaps.withName(name).get();

				if (configMap == null) {
					log.info("create configmap : " + namespace +" > "+ name);
					
					InputStream is = new ClassPathResource("mariadb/report_status.template").getInputStream();

					String temp = IOUtils.toString(is, StandardCharsets.UTF_8.name());

					Map<String, String> data = new HashMap<>();
					data.put("report_status.sh", temp);

					Resource<ConfigMap, DoneableConfigMap> configMapResource = client.configMaps().inNamespace(namespace).withName(name);

					configMap = configMapResource.createOrReplace(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build());
				
					log.info("created configmap : " + namespace +" > "+ name);
				} else {
					log.info("exist configmap : " + namespace +" > "+ configMap.getMetadata().getName());
				}
			}
			
			{
//			 * 	- spec>template>spec>containers>lifecycle : shell command 등록
//			 * 	- spec>template>spec>containers>volumeMounts  :  report-status 추가 
//			 * 	- spec>template>spec>volumes :  report-status 추가
//			 * 	- label 추가 (zdb-failover-enable=true)
				
				MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = 
						client.inNamespace(namespace).apps().statefulSets();
				
				List<StatefulSet> stsList = statefulSets.withLabel("app", "mariadb").withLabel("release", serviceName).list().getItems();
				if(stsList == null || stsList.size() < 2) {
					return;
				}
				
				for (StatefulSet sts : stsList) {
					log.info("Start statefulset update. " + namespace +" > "+ sts.getMetadata().getName());
					
					PodSpec spec = sts.getSpec().getTemplate().getSpec();
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
						String msg = "mariadb container 가 존재하지 않습니다. ["+namespace+" > "+serviceName+"]";
						log.error(msg);
						
//						result = new Result(txId, Result.ERROR, msg);
//						result.setThrowable(new Exception(msg));
//						
//						return result;
						return;
					}

					List<Volume> volumes = spec.getVolumes();
					for (Volume v : volumes) {
						if("report-status".equals(v.getName())) {
							existVolume = true;
							break;
						}
					}
					
					
					StatefulSetBuilder builder = new StatefulSetBuilder(sts);

					Map<String, String> labels = sts.getMetadata().getLabels();
					String labelKey = "zdb-failover-enable";
					labels.put(labelKey, "true");
					
					builder.editMetadata().withLabels(labels).endMetadata();
					
					log.info("withLabels : " + sts.getMetadata().getName());
					
					if(!existCommand) {
						String[] command = new String[] {
								"/bin/sh",
								"-c",
								"/usr/bin/nohup /report_status.sh 1>report.log 2>/dev/null &"
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

						log.info("addToCommand : " + sts.getMetadata().getName());
					} else {
						log.info("existCommand : " + namespace +" > "+ sts.getMetadata().getName());
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
						
						log.info("addToVolumeMounts : " + sts.getMetadata().getName());
					} else {
						log.info("existVolumeMount : " + namespace +" > "+ sts.getMetadata().getName());
					}
					
					if(!existVolume) {
						Volume volume = new Volume();
						
						ConfigMapVolumeSource cmvs = new ConfigMapVolumeSource();
						cmvs.setDefaultMode(484);
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
						log.info("addToVolumes : " + sts.getMetadata().getName());
					} else {
						log.info("existVolume : " + namespace +" > "+ sts.getMetadata().getName());
					}
					
					StatefulSet newSvc = builder.build();
					
					statefulSets.createOrReplace(newSvc);
					
					log.info("End statefulset update. " + namespace +" > "+ sts.getMetadata().getName());
				}
			}
			
			

		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}

}
