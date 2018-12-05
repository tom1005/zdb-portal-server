package com.zdb.core.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.zdb.core.domain.CommonConstants;
import com.zdb.core.domain.ZDBMariaDBConfig;
import com.zdb.core.repository.ZDBMariaDBConfigRepository;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.extern.slf4j.Slf4j;

/**
 * AdminService
 * 
 * @author 06919
 *
 */
@Service("adminService")
@Slf4j
@Configuration
public class AdminService {
	
	@Autowired
	protected K8SService k8sService;

	@Autowired
	private ZDBMariaDBConfigRepository zdbMariaDBConfigRepository;

	public String mycnfBackup() {
		StringBuffer sb = new StringBuffer();
		try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
			
			List<Namespace> namespaceItems = client.inAnyNamespace().namespaces().withLabel(CommonConstants.ZDB_LABEL, "true").list().getItems();
			
			for (Namespace namespace : namespaceItems) {
				// namespace 의 mariadb configmap 조회
				List<ConfigMap> cmItems = client.inNamespace(namespace.getMetadata().getName()).configMaps().withLabel("app", "mariadb").list().getItems();
				for (ConfigMap configMap : cmItems) {
					
					
					String configMapName = configMap.getMetadata().getName();
					String releaseName = configMap.getMetadata().getLabels().get("release");
					String value = configMap.getData().get("my.cnf");
					
					List<ZDBMariaDBConfig>  configDatas = zdbMariaDBConfigRepository.findByConfigMapName(configMapName);
					if (configDatas == null || configDatas.isEmpty()) {

						ZDBMariaDBConfig config = new ZDBMariaDBConfig();
						config.setConfigMapName(configMapName);
						config.setReleaseName(releaseName);
						config.setConfigMapName(configMapName);
						config.setBeforeValue("");
						config.setAfterValue(value);
						config.setDate(DateUtil.currentDate());

						zdbMariaDBConfigRepository.save(config);
						
						log.info("{} 의 my.cnf 저장 완료.", configMapName); 
						
						sb.append(configMapName).append("; ");
					}
				}
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return sb.toString();
	}
}
