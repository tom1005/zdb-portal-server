package com.zdb.core.service;


import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBPersistenceEntity;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

/**
 * ZDBRestService Implementation
 * 
 * @author 06919
 *
 */
@Service("commonService")
@Slf4j
@Configuration
public class CommonServiceImpl extends AbstractServiceImpl {
	@Autowired
	protected ZDBRepository zdbRepository;
	
	@Override
	public Result getDeployment(String namespace, String serviceName) throws Exception {
		return null;
	}

	@Override
	public Result updateScale(String txId, ZDBEntity zdbEntity) throws Exception {
		return null;
	}

	@Override
	public Result updateScaleOut(String txId, ZDBEntity zdbEntity) throws Exception {
		return null;
	}

	@Override
	public Result deletePersistentVolumeClaimsService(String txId, String namespace, String serviceName, String pvcName)
			throws Exception {
		return null;
	}

	@Override
	public Result getPersistentVolumeClaims(String namespace) throws Exception {
		return null;
	}

	@Override
	public Result getPersistentVolumeClaim(String namespace, String pvcName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result getConnectionInfo(String namespace, String serviceType, String serviceName) throws Exception {
		return null;
	}

	@Override
	public Result getDBVariables(String txId, String namespace, String serviceName) {
		return null;
	}
	
	@Override
	public Result getAllDBVariables(String txId, String namespace, String serviceName) {
		return null;
	}

	@Override
	public Result getServiceCheckAlive(String namespace, String serviceType, String serviceName) throws Exception {
		return null;
	}

	@Override
	public Result getMycnf(String namespace, String releaseName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result getUserGrants(String namespace, String serviceType, String releaseName) {
		return null;
	}

	@Override
	public Result createPersistentVolumeClaim(String txId, ZDBPersistenceEntity entity) throws Exception {
		
		PersistenceSpec spec = null;
		try {
			spec = k8sService.createPersistentVolumeClaim(entity);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, "스토리지 생성 실패 - "+ e.getMessage(), e);
		}
		
		return new Result(txId, IResult.OK, "스토리지 생성 완료.[" + spec.getPvcName() +" | "+ spec.getSize() +"]");
	}

	
	/* (non-Javadoc)
	 * Master 장애로 서비스LB 를 Master -> Slave 로 전환 여부를 반환한다.
	 * 
	 * Result.message 로 상태값 반환
	 *  - MasterToSlave
	 *  - MasterToMaster
	 *  - unknown
	 *  
	 *  
	 * @see com.zdb.core.service.AbstractServiceImpl#serviceFailOverStatus(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Result serviceFailOverStatus(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			
			List<io.fabric8.kubernetes.api.model.Service> services = k8sService.getServices(namespace, serviceName);
			for (io.fabric8.kubernetes.api.model.Service service : services) {
				String sName = service.getMetadata().getName();
				String role = null;
				
				String selectorTarget = null;
				if("redis".equals(serviceType)) {
					if(sName.endsWith("master")) {
						role = "master";
					}
					
					selectorTarget = service.getSpec().getSelector().get("role");
					
				} else if("mariadb".equals(serviceType)) {
					role = service.getMetadata().getLabels().get("component");
					
					selectorTarget = service.getSpec().getSelector().get("component");
				}
				
				if(!"master".equals(role)) {
					continue;
				}
				
				// takeover 된 상태
				if("master".equals(role) && "slave".equals(selectorTarget)) {
					return new Result(txId, Result.OK, "MasterToSlave");
				} else if("master".equals(role) && "master".equals(selectorTarget)) {
					return new Result(txId, Result.OK, "MasterToMaster");
				} else {
					return new Result(txId, Result.ERROR, "unknown");
				}
			}

			return new Result(txId, Result.ERROR, "unknown");
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}


	/**
	 * Auto Failover 
	 *  - On : add label : zdb-failover-enable=true
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=true" --overwrite
	 *  - Off : update label : zdb-failover-enable=false
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=false" --overwrite
	 *        
	 * @param txId
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	@Override
	public Result updateAutoFailoverEnable(String txId, String namespace, String serviceType, String serviceName, boolean enable) throws Exception {
		
		Result result = null;
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = 
					client.inNamespace(namespace).apps().statefulSets();

			List<StatefulSet> stsList = statefulSets.withLabel("app", "mariadb").withLabel("release", serviceName).list().getItems();
			if(stsList == null || stsList.size() < 2) {
				result = new Result(txId, Result.ERROR , "Master/Slave 로 구성된 서비스에서만 설정 가능합니다. ["+namespace +" > "+ serviceName +"]");
				return result;
			}
			
			List<StatefulSet> items = statefulSets
					.withLabel("app", "mariadb")
					.withLabel("component", "master")
					.withLabel("release", serviceName)
					.list().getItems();
			
			
			if(items != null && !items.isEmpty()) {
				
				StatefulSet sts = items.get(0);
				
				Map<String, String> labels = sts.getMetadata().getLabels();
				String oldValue = labels.get("zdb-failover-enable");
				if(oldValue == null || oldValue.isEmpty()) {
					oldValue = "false";
				}
				
				labels.put("zdb-failover-enable", String.valueOf(enable));
				
				StatefulSetBuilder newSts = new StatefulSetBuilder(sts);
				StatefulSet newSvc = newSts.editMetadata().withLabels(labels).endMetadata().build();
				
				statefulSets.createOrReplace(newSvc);
				
				result = new Result(txId, Result.OK , "failover 설정 변경 완료. [oldValue : "+oldValue+"; newValue : "+enable+"]");
			} else {
				result = new Result(txId, Result.ERROR , "실행중인 서비스가 없습니다. ["+namespace +" > "+ serviceName +"]");
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		
		return result;
	}
	
	@Override
	public Result getAutoFailoverServices(String txId, String namespace) throws Exception {
		return getAutoFailoverService(txId, namespace, null);
	}
	
	public Result getAutoFailoverService(String txId, String namespace, String serviceName) throws Exception {
		Result result = new Result(txId);
		List<StatefulSet> services = k8sService.getAutoFailoverServices(namespace, serviceName);

		if(services != null && !services.isEmpty()) {
			String[] array = new String[services.size()];
			
			for (int i = 0; i < services.size(); i++) {
				StatefulSet sts = services.get(i);
				array[i] = sts.getMetadata().getName();
			}
			
			result.putValue("services", array);
			result.setCode(Result.OK);
		} else {
			result.setCode(Result.OK);
			result.setMessage("[]");
		}

		return result;
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
	 * @see com.zdb.core.service.AbstractServiceImpl#addAutoFailover(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Result addAutoFailover(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		
		
		Result result = null;
		StringBuffer resultMsg = new StringBuffer();
		
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
			// * - report_status.sh 을 실행 할 수 있도록 configmap 등록

			MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> configMaps = client.inNamespace(namespace).configMaps();

			String cmName = "report-status";
			ConfigMap configMap = configMaps.withName(cmName).get();

			if (configMap == null) {
				log.info("create configmap : " + namespace +" > "+ cmName);
				
				InputStream is = new ClassPathResource("mariadb/report_status.template").getInputStream();

				String temp = IOUtils.toString(is, StandardCharsets.UTF_8.name());

				Map<String, String> data = new HashMap<>();
				data.put("report_status.sh", temp);

				Resource<ConfigMap, DoneableConfigMap> configMapResource = client.configMaps().inNamespace(namespace).withName(cmName);

				configMap = configMapResource.createOrReplace(new ConfigMapBuilder().withNewMetadata().withName(cmName).endMetadata().addToData(data).build());
			
				log.info("Created configmap : " + namespace +" > "+ cmName);
				
			} else {
				log.info("Exist configmap : " + namespace +" > "+ configMap.getMetadata().getName());
			}

//			 * 	- spec>template>spec>containers>lifecycle : shell command 등록
//			 * 	- spec>template>spec>containers>volumeMounts  :  report-status 추가 
//			 * 	- spec>template>spec>volumes :  report-status 추가
//			 * 	- label 추가 (zdb-failover-enable=true)
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = 
					client.inNamespace(namespace).apps().statefulSets();
			
			List<StatefulSet> stsList = statefulSets.withLabel("app", "mariadb").withLabel("release", serviceName).list().getItems();
			if(stsList == null || stsList.size() < 2) {
				String msg = "["+namespace+" > "+serviceName+"] 서비스가 없거나 single 서비스 입니다.";
				result = new Result(txId, Result.ERROR, msg);
				return result;
			}
			
			for (StatefulSet sts : stsList) {
				String stsName = sts.getMetadata().getName();
				log.info("Start update statefulset. " + namespace +" > "+ stsName);
				
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
					
					result = new Result(txId, Result.ERROR, msg);
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

				Map<String, String> labels = sts.getMetadata().getLabels();
				
				boolean buildFlag = false;
				
				String labelKey = "zdb-failover-enable";
				if(!labels.containsKey(labelKey) || !"true".equals(labels.get(labelKey))) {
					labels.put(labelKey, "true");
					
					builder.editMetadata().withLabels(labels).endMetadata();
					buildFlag = true;
					
				}
				
				log.info("withLabels : " + stsName);
				
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
					
					buildFlag = true;
					log.info("addToVolumes : " + stsName);
				} else {
					log.info("existVolume : " + namespace +" > "+ stsName);
				}
				
				if (buildFlag) {
					StatefulSet newSvc = builder.build();

					statefulSets.createOrReplace(newSvc);
					log.info("End statefulset update. " + namespace +" > "+ stsName);
					resultMsg.append("Update statefulset. " + namespace +" > "+ stsName).append("\n");
				} else {
					log.info("Skip update statefulset. " + namespace +" > "+ stsName);
				}
				
			}
					
			result = new Result(txId, Result.OK , resultMsg.toString());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} 
		return result;
	
	}
	
}
