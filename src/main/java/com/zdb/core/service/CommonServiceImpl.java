package com.zdb.core.service;


import java.io.FileNotFoundException;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBPersistenceEntity;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
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
}
