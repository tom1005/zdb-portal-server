package com.zdb.core.service;

import java.util.List;
import java.util.Map;

import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.Tag;
import com.zdb.core.domain.UserInfo;
import com.zdb.core.domain.ZDBConfig;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBPersistenceEntity;
import com.zdb.core.domain.ZDBType;

/**
 * ZDBRestService interface
 * 
 */
/**
 * @author a06919
 *
 */
public interface ZDBRestService {
	
	static final int MAX_QUEUE_SIZE = 10;
	
	/**
	 * To return a zdbEntity object fetched by ID
	 * 
	 * @param zdbEntityId zdbEntity ID
	 * @return Customer object
	 */
	Result getDeployment(String namespace, String serviceName) throws Exception;
	
	/**
	 * @return the list of all zdbEntitys
	 */
	Result getDeployments(String namespace, String serviceType) throws Exception;
	
	/**
	 * @param zdbEntity Customer entity to be saved
	 * @throws Exception 
	 */
	Result createDeployment(String txId, ZDBEntity zdbEntity, UserInfo userInfo) throws Exception;
	
	/**
	 * @param id zdbEntity ID to be updated
	 * @param zdbEntity updated zdbEntity entity
	 * @return updated zdbEntity entity
	 */
	Result updateScale(String txId, ZDBEntity zdbEntity) throws Exception;

	/**
	 * @param id zdbEntity ID to be updated
	 * @param zdbEntity updated zdbEntity entity
	 * @return updated zdbEntity entity
	 */
	Result updateScaleOut(String txId, ZDBEntity zdbEntity) throws Exception;
	
	
	/**
	 * @param id zdbEntity ID to be deleted
	 * @return true, if deleted; otherwise, return false
	 */
	Result deleteServiceInstance(String txId, String namespace, String serviceType, String serviceName) throws Exception; 
	
	/**
	 *  Service restart
	 * @param id
	 */
	Result restartService(String txId, ZDBType dbType, String namespace, String serviceName) throws Exception;

//	/**
//	 * @param txId
//	 * @param zdbEntity
//	 * @return
//	 * @throws Exception
//	 */
//	Result createPersistentVolumeClaim(String txId, String namespace, String serviceName, PersistenceSpec pvcSpec) throws Exception;

	/**
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @param pvcName
	 * @return
	 * @throws Exception
	 */
	Result deletePersistentVolumeClaimsService(String txId, String namespace, String serviceName, String pvcName) throws Exception;
	
	/**
	 *  get persistent volume claims
	 * @param namespace
	 */
	Result getPersistentVolumeClaims(final String namespace) throws Exception;
	
	/**
	 *  get a persistent volume claim
	 * @param namespace
	 * @param pvcName
	 */
	Result getPersistentVolumeClaim(final String namespace, final String pvcName) throws Exception;

	/**
	 *  get pod logs
	 * @param namespace
	 * @param podName
	 */
	Result getPodLog(final String namespace, final String podName) throws Exception;
		
	/**
	 * @param namespace
	 * @param kind
	 * @return
	 * @throws Exception
	 */
	Result getOperationEvents(String namespace, String servceName, String startTime, String endTime, String keyword) throws Exception;
	
	Result getEvents(String namespace, String servceName, String kind, String startTime, String endTime, String keyword) throws Exception;
	
	/**
	 *  get user namespaces
	 * @param namespace
	 */
	Result getNamespaces(List<String> filters) throws Exception;
	
	// 전체 서비스 getServices
	Result getAllServices() throws Exception;
	
	// 네임스페이스별 getServices(namesapce)
	Result getServicesWithNamespaces(final String namespaces, boolean detail) throws Exception;
	
	// 네임스페이스, 서비스타입, 서비스명 getService(namesapce, servicetype, servicename)
	Result getService(final String namespace, final String serviceType, final String serviceName) throws Exception;
	
	// 서비스타입별  getServicesOfServiceType(servicetype)
	Result getServicesOfServiceType(String serviceType) throws Exception;
	
	// 네임스페이스, 서비스타입  getServices(namesapce, servicetype)
	Result getServices(final String namespace, String serviceType) throws Exception;
	
	// 네임스페이스, 서비스타입 getPods(namesapce, servicetype, servicename)
	Result getPods(final String namespace, final String serviceType, final String serviceName) throws Exception;

	// 서비스타입, 서비스명 getPod(namespace, servicetype,  servicename, podName)
	Result getPodWithName(final String namespace, final String serviceType, final String serviceName, final String podName) throws Exception;
	
	// Pod Resource Info
	Result getPodResources(final String namespace, final String serviceType, final String serviceName) throws Exception;

	Result getConnectionInfo(String namespace, String serviceType, String serviceName) throws Exception;

	Result getPodMetrics(String namespace, String podName) throws Exception;

	Result getPodMetricsV2(String namespace, String podName) throws Exception;
	
	Result updateDBVariables(final String txId, final String namespace, final String serviceName, Map<String, String> config) throws Exception;

	Result getDBVariables(String txId, String namespace, String serviceName);
	
	Result getAllDBVariables(String txId, String namespace, String serviceName);

	Result getAbnormalPersistentVolumeClaims(String namespace, String abnormalType) throws Exception;

	Result getAbnormalPersistentVolumes() throws Exception;

	Result restartPod(String txId, String namespace, String serviceName, String podName) throws Exception;

	Result getServiceCheckAlive(String namespace, String serviceType, String serviceName) throws Exception;

	Result setNewPassword(String txId, String namespace, String serviceType, String serviceName, String newPassword, String clusterEnabled) throws Exception;

	Result createTag(Tag tag) throws Exception;

	Result createPersistentVolumeClaim(String txId, ZDBPersistenceEntity entity) throws Exception;

	Result deleteTag(Tag tag) throws Exception;

	Result getTagsWithService(String namespace, String serviceName) throws Exception;

	Result getTagsWithNamespace(String namespace) throws Exception;

	Result getTags(List<String> namespaceList) throws Exception;

	Result getNodes() throws Exception;

	Result getNodeCount() throws Exception;

	Result getUnusedPersistentVolumeClaims(String namespace) throws Exception;
	
	Result isAvailableResource(String namespace, String userId, String cpu, String memory, boolean clusterEnabled) throws Exception;
	
	Result getNamespaceResource(String namespace, String userId) throws Exception;

	Result createPublicService(String txId, String namespace, String serviceType, String serviceName) throws Exception;
	
	Result deletePublicService(String txId, String namespace, String serviceType, String serviceName) throws Exception;
	
	Result getSlowLog(String namespace, String podName) throws Exception;
	
	Result getSlowLogDownload(String namespace, String podName) throws Exception;
	
	Result getMycnf(String namespace, String releaseName);
	
	Result getUserGrants(String namespace, String serviceType, String releaseName);

	Result createZDBConfig(ZDBConfig zdbconfig);
	
	Result getZDBConfig(String namespace);

	Result updateZDBConfig(ZDBConfig zdbconfig);

	Result deleteZDBConfig(ZDBConfig zdbConfig);

	Result updateStorageScale(String txId, String namespace, String serviceType, String serviceName, String pvcSize) throws Exception;

	Result serviceOff(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception;
	
	Result serviceOn(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception;
	
	/**
	 * Master 장애로 서비스LB 를 Master -> Slave 로 전환 (MariaDB 만 지원)
	 * 
	 * @param txId
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	Result serviceChangeMasterToSlave(String txId, String namespace, String serviceType, String serviceName) throws Exception;

	/**
	 * 
	 * Master 장애로 서비스LB 를 Master -> Slave 로 전환 후 다시 Slave -> Master 로 복원(MariaDB 만 지원)
	 * 
	 * @param txId
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	Result serviceChangeSlaveToMaster(String txId, String namespace, String serviceType, String serviceName) throws Exception;

	/**
	 * Master 장애로 서비스LB 를 Master -> Slave 로 전환 여부를 반환한다.
	 * 
	 * Result.message 로 상태값 반환
	 *  - MasterToSlave (전환된 상태)
	 *  - MasterToMaster (정상 서비스 상태)
	 *  - unknown (파라메터 오류 또는 알수 없음)
	 * @param txId
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	Result serviceFailOverStatus(String txId, String namespace, String serviceType, String serviceName) throws Exception;

	Result slowlogRotation(String txId, String namespace, String serviceType, String serviceName) throws Exception;
	
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
	Result updateAutoFailoverEnable(String txId, String namespace, String serviceType, String serviceName, boolean enable) throws Exception;

//	/**
//	 * 
//	 * @param txId
//	 * @param namespace
//	 * @param serviceType
//	 * @param serviceName
//	 * @return
//	 * @throws Exception
//	 */
//	Result addAutoFailover(String txId, String namespace, String serviceType, String serviceName) throws Exception;
	
	/**
	 * Statefulset 의 label : zdb-failover-enable=true 가 등록된 서비스 목록.(master)
	 * 
	 * @param txId
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	Result getAutoFailoverServices(String txId, String namespace) throws Exception;
	
	Result getAutoFailoverEnabledServices(String txId, String namespace) throws Exception;

	/**
	 * Statefulset 의 label : zdb-failover-enable=true 가 등록된 서비스.(master)
	 * 
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	Result getAutoFailoverService(String txId, String namespace, String serviceName) throws Exception;
	
	Result getWorkerPools() throws Exception;

	Result getDatabases(String namespace, String serviceType, String serviceName);

	Result getFileLog(String namespace, String serviceName, String logType, String dates) throws Exception;

	Result getAllServices2();

	Result getAlertRules(String txId, String namespaces);

	Result getAlertRule(String txId, String namespace,String alert);
	
	Result createAlertRule(String txId, AlertingRuleEntity alertingRuleEntity);

	Result updateDefaultAlertRule(String txId, AlertingRuleEntity alertingRuleEntity);

	Result updateAlertRule(String txId, AlertingRuleEntity alertingRuleEntity);

	Result deleteAlertRule(String txId, AlertingRuleEntity alertingRuleEntity);

	Result getStorages(String namespace, String keyword, String app, String storageClassName, String billingType, String phase, String stDate, String edDate) throws Exception;

	Result getStoragesData() throws Exception;

	Result getUserNamespaces(String userId) throws Exception;

}
