package com.zdb.core.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.PersistenceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBPersistenceEntity;
import com.zdb.core.repository.ZDBRepository;

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

	@Override
	public Result getFileLog(String namespace, String serviceName,String logType, String dates) {
		// TODO Auto-generated method stub
		return null;
	}
}
