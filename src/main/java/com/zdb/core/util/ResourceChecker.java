package com.zdb.core.util;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zdb.core.domain.CPUUnit;
import com.zdb.core.domain.MemoryUnit;
import com.zdb.core.domain.NamespaceResource;
import com.zdb.core.domain.NodeResource;
import com.zdb.core.domain.ResourceQuota;
import com.zdb.core.exception.ResourceException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ResourceChecker {

	public static String iamBaseUrl;
	
	@Value("${iam.baseUrl}")
	public void setMasterUrl(String url) {
		iamBaseUrl = url;
	}
	
	public static NamespaceResource getNamespaceResource(String namespace, String userId) {
		//https://zcp-iam.cloudzcp.io:443/iam/namespace/ns-zdb-02/resource
		
		RestTemplate restTemplate = getRestTemplate();
		URI uri = URI.create(iamBaseUrl + "/iam/namespace/"+namespace+"/resource?userId="+userId);
		Map<String, Object> namespaceResource = restTemplate.getForObject(uri, Map.class);
		
		if(namespaceResource != null) {
			Object object = namespaceResource.get("data");
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String json = gson.toJson(object);
			
			NamespaceResource resource = gson.fromJson(json, NamespaceResource.class);
			return resource;
		}
		
		return null;
	}
	


	public static List<NodeResource> getNodeResource() {
		//https://zcp-iam.cloudzcp.io:443/iam/metrics/nodes
		//https://pog-dev-internal-iam.cloudzcp.io:443/iam/metrics/nodes
		
		RestTemplate restTemplate = getRestTemplate();
		URI uri = URI.create(iamBaseUrl + "/iam/metrics/nodes");
//		URI uri = URI.create("https://pog-dev-internal-iam.cloudzcp.io:443/iam/metrics/nodes");
		Map<String, Object> nodeResource = restTemplate.getForObject(uri, Map.class);
		
		if(nodeResource != null) {
			Object object = nodeResource.get("data");
			
			if(object != null && object instanceof Map) {
				Object items = ((Map)object).get("items");
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String json = gson.toJson(items);
				Type listType = new TypeToken<ArrayList<NodeResource>>(){}.getType();
				List<NodeResource> resource = gson.fromJson(json, listType);
				return resource;
			}
			
		}
		
		return null;
	}
	
	private static RestTemplate getRestTemplate() {
		HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(1000*10);
        httpRequestFactory.setConnectTimeout(1000*10);
        httpRequestFactory.setReadTimeout(1000*10);
        
        return new RestTemplate(httpRequestFactory);
	}
	
	/**
	 * @param namespace
	 * @param userId
	 * @param memory
	 * @param cpu
	 * @return
	 * @throws Exception
	 */
	public static boolean isAvailableResource(String namespace, String userId, int memory, int cpu) throws Exception {
		String property = System.getProperty("AVAILABLE_RESOURCE_CHECK");
		
		boolean availableNamespaceCpuFlag = false;
		boolean availableNamespaceMemFlag = false;
		
		if(property == null) {
			property = "true";
		} else {
			availableNamespaceCpuFlag = true;
			availableNamespaceMemFlag = true;
		}
		
		log.info("AVAILABLE_RESOURCE_CHECK : " + property);
		
		// 2018-10-08 추가
		// 환경 설정에서 체크 옵션값에 따라 로직 실행
		//AVAILABLE_RESOURCE_CHECK : null or true : 리소스 체크 
		//AVAILABLE_RESOURCE_CHECK : false : 리소스 체크 skip 
		if(Boolean.parseBoolean(property)) {
			NamespaceResource resource = getNamespaceResource(namespace, userId);
			
			if(resource == null) {
				log.error("네임스페이스[{}] 의 가용 리소스 정보를 알 수 없습니다.", namespace);
				throw new ResourceException("가용 리소스 정보 조회 에러.[" + namespace +"]");
			}
			
			ResourceQuota hard = resource.getHard();
			ResourceQuota used = resource.getUsed();
			if(hard == null || used == null) {
				log.error("네임스페이스[{}] 의 리소스 쿼터가 설정되지 않았습니다.", namespace);
				throw new ResourceException("네임스페이스["+namespace+"] 의 리소스 쿼터가 설정되지 않았습니다.");
			}
			
			Integer cpuRequests = hard.getCpuRequests();
			CPUUnit cpuRequestsUnit = hard.getCpuRequestsUnit();
			if(cpuRequestsUnit == CPUUnit.Core) {
				cpuRequests = cpuRequests * 1000;
			}
			
			Integer memRequests = hard.getMemoryRequests();
			MemoryUnit memRequestsUnit = hard.getMemoryRequestsUnit();
			if(memRequestsUnit == MemoryUnit.Gi) {
				memRequests = memRequests * 1000;
			}
			
			
			Integer usedCpuRequests = used.getCpuRequests();
			CPUUnit usedCpuRequestsUnit = used.getCpuRequestsUnit();
			if(usedCpuRequestsUnit == CPUUnit.Core) {
				usedCpuRequests = usedCpuRequests * 1000;
			}
			
			Integer usedMemRequests = used.getMemoryRequests();
			MemoryUnit usedMemRequestsUnit = used.getMemoryRequestsUnit();
			
			if(usedMemRequestsUnit == MemoryUnit.Gi) {
				usedMemRequests = usedMemRequests * 1000;
			}
			
			int availableCpu = cpuRequests - usedCpuRequests;
			int availableMemory = memRequests - usedMemRequests;
			
			int serviceRequestMemory = memory;//K8SUtil.convertToMemory(memory);
			int serviceRequestCpu = cpu;//K8SUtil.convertToCpu(cpu);
			
			log.warn("availableCpu : {}, serviceRequestCpu : {}", availableCpu, serviceRequestCpu);
			
			if( availableCpu - serviceRequestCpu < 0) {
				throw new ResourceException("네임스페이스["+namespace +"] - 가용 CPU 자원이 부족합니다.<br>[가용CPU(네임스페이스) : " + availableCpu +"m]");
			} else {
				availableNamespaceCpuFlag = true;
			}
			
			if( availableMemory - serviceRequestMemory < 0) {
				throw new ResourceException("네임스페이스["+namespace +"] - 가용 메모리가 부족합니다.<br>[가용메모리(네임스페이스) : " + availableMemory +"Mi]");
			} else {
				availableNamespaceMemFlag = true;
			}
		}
		
		return availableNamespaceCpuFlag && availableNamespaceMemFlag;
	}
	
	/**
	 * @param namespace
	 * @param userId
	 * @param memory
	 * @param cpu
	 * @return
	 * @throws Exception
	 */
	public static int availableNodeCount(int memory, int cpu) throws Exception {
		
		int availableNodeCount = 0;
		
		try {
			List<NodeResource> nodeResourceList = getNodeResource();
			
			for (NodeResource nodeResource : nodeResourceList) {
				boolean availableNodeCpuFlag = false;
				boolean availableNodeMemFlag = false;
				String nodeRole = nodeResource.getNodeRoles();
				String status = nodeResource.getStatus();
				
				if(nodeRole.indexOf("zdb") < 0) {
					continue;
				}
				if(!"Ready".equals(status)) {
					log.info("노드["+nodeResource.getNodeName()+"] 가용 상태 점검 - [Status : " + status +"]");
					continue;
				}
				
				String _nodeAllocatableCpu = nodeResource.getAllocatableCpu();
				String _nodeAllocatableMemory = nodeResource.getAllocatableMemory();
				String _nodeCpuRequest = nodeResource.getCpuRequests();
				String _nodeMemoryRequest = nodeResource.getMemoryRequests();
				
				//"allocatableCpu": "3.91",
				//"allocatableMemory": "14.00Gi",
				Double nodeAllocatableCpu = NumberUtils.cpuByM(_nodeAllocatableCpu);
				Double nodeAllocatableMemory = NumberUtils.memoryByMi(_nodeAllocatableMemory);
				
				Double nodeCpuRequest = NumberUtils.cpuByM(_nodeCpuRequest);
				Double nodeMemoryRequest = NumberUtils.memoryByMi(_nodeMemoryRequest);
				
				double nodeAvailableCpu = nodeAllocatableCpu - nodeCpuRequest;
				double nodeAvailableMemory = nodeAllocatableMemory - nodeMemoryRequest;
				
				if( nodeAvailableCpu - cpu < 0) {
					availableNodeCpuFlag = false;
					log.info("노드["+nodeResource.getNodeName()+"] - 가용 CPU 자원이 부족합니다. [가용CPU: " + nodeAvailableCpu +"m]");
				} else {
					availableNodeCpuFlag = true;
				}
				
				if( nodeAvailableMemory - memory < 0) {
					log.info("노드["+nodeResource.getNodeName()+"] - 가용 메모리가 부족합니다. [가용메모리: " + nodeAvailableMemory +"Mi]");
				} else {
					availableNodeMemFlag = true;
				}
				
				if(availableNodeCpuFlag && availableNodeMemFlag) {
					availableNodeCount++;
				}
			}
			
		} catch (Exception e) {
			throw new ResourceException("노드 리소스 조회 오류 [" +e.getMessage()+"]");
		}
		
		return availableNodeCount;
	}
	
	
	public static void main(String[] args) {
		try {
//			iamBaseUrl = "https://zcp-iam.cloudzcp.io:443";
//			boolean availableResource = isAvailableResource("ns-zdb-02","userid", 5900,1500);
			
			List<NodeResource> nodeResource = getNodeResource();
			
			System.out.println(nodeResource);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
