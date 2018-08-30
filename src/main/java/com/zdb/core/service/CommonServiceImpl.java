package com.zdb.core.service;


import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.zdb.core.domain.Result;
import com.zdb.core.domain.ZDBEntity;

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

}
