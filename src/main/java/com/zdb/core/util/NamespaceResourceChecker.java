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
	
	private static NamespaceResource getNamespaceResource(String namespace, String userId) {
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
        httpRequestFactory.setConnectionRequestTimeout(1000);
        httpRequestFactory.setConnectTimeout(1000);
        httpRequestFactory.setReadTimeout(1000);
        
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
		NamespaceResource resource = getNamespaceResource(namespace, userId);
		
		if(resource == null) {
			log.error("네임스페이스[{}] 의 가용 리소스 정보를 알 수 없습니다.", namespace);
			throw new ResourceException("가용 리소스 정보 조회 에러.[" + namespace +"]");
		}
		
		ResourceQuota hard = resource.getHard();
		Integer cpuLimits = hard.getCpuLimits();
		CPUUnit cpuLimitsUnit = hard.getCpuLimitsUnit();
		if(cpuLimitsUnit == CPUUnit.Core) {
			cpuLimits = cpuLimits * 1000;
		}
		
		Integer memLimits = hard.getMemoryLimits();
		MemoryUnit memRequestsUnit = hard.getMemoryLimitsUnit();
		if(memRequestsUnit == MemoryUnit.Gi) {
			memLimits = memLimits * 1000;
		}
		
		ResourceQuota used = resource.getUsed();
		Integer usedCpuLimits = used.getCpuLimits();
		CPUUnit usedCpuLimitsUnit = used.getCpuLimitsUnit();
		if(usedCpuLimitsUnit == CPUUnit.Core) {
			usedCpuLimits = usedCpuLimits * 1000;
		}
		
		Integer usedMemLimits = used.getMemoryLimits();
		MemoryUnit usedMemRequestsUnit = used.getMemoryLimitsUnit();
		
		if(usedMemRequestsUnit == MemoryUnit.Gi) {
			usedMemLimits = usedMemLimits * 1000;
		}
		
		int availableCpu = cpuLimits - usedCpuLimits;
		int availableMemory = memLimits - usedMemLimits;
		
		
		
		int serviceRequestMemory = memory;//K8SUtil.convertToMemory(memory);
		int serviceRequestCpu = cpu;//K8SUtil.convertToCpu(cpu);
		
		if( availableCpu - serviceRequestCpu < 0) {
			throw new ResourceException("가용 CPU 자원이 부족합니다. [가용CPU : " + availableCpu +"m]");
		}
		
		if( availableMemory - serviceRequestMemory < 0) {
			throw new ResourceException("가용 메모리가 부족합니다. [가용메모리 : " + availableMemory +"Mi]");
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
