package com.zdb.core.util;

import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zdb.core.domain.CPUUnit;
import com.zdb.core.domain.MemoryUnit;
import com.zdb.core.domain.NamespaceResource;
import com.zdb.core.domain.ResourceQuota;
import com.zdb.core.exception.ResourceException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class NamespaceResourceChecker {

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
		if(property == null) {
			property = "true";
		}
		
		System.out.println("AVAILABLE_RESOURCE_CHECK : " + property);
		
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
				throw new ResourceException("가용 CPU 자원이 부족합니다. [가용CPU : " + availableCpu +"m]");
			}
			
			if( availableMemory - serviceRequestMemory < 0) {
				throw new ResourceException("가용 메모리가 부족합니다. [가용메모리 : " + availableMemory +"Mi]");
			}
		}
		
		
		return true;
	}
	
	
	public static void main(String[] args) {
		try {
			iamBaseUrl = "https://zcp-iam.cloudzcp.io:443";
			boolean availableResource = isAvailableResource("ns-zdb-02","userid", 5900,1500);
			System.out.println(availableResource);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
