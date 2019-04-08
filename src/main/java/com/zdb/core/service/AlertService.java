package com.zdb.core.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.PrometheusEntity;
import com.zdb.core.domain.PrometheusGroups;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlertService {

	final private String configMapName = "prometheus-zdb-rules";
	final private String dataTitle = "prometheus-zdb.rules";
	
	public List<AlertingRuleEntity> getAlertRules(String namespaces) throws Exception {
		List<AlertingRuleEntity> list = new ArrayList<>();
		Yaml yaml = new Yaml();
		
		if(!StringUtils.isEmpty(namespaces)) {
			String[] namespaceSplit = namespaces.split(",");
			for (String namespace : namespaceSplit) {
				DefaultKubernetesClient client = K8SUtil.kubernetesClient();
				Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(namespace).configMaps().withName(configMapName);
				if(dt.get() !=null && dt.get().getData() != null) {
				PrometheusEntity pn = yaml.loadAs(dt.get().getData().get(dataTitle),PrometheusEntity.class);
					if(pn.getGroups() !=null) {
						PrometheusGroups pg = pn.getGroups().get(0);
						for(int i = 0 ;i < pg.getRules().size(); i++) {
							String pgr = pg.getRules().get(i);
							AlertingRuleEntity ar = parseAlertRuleEntity(namespace,pgr);
							list.add(ar);
						}
					}
				}
			}
		}
		
		return list;
	}


	public AlertingRuleEntity getAlertRule(String namespace, String alert)throws Exception {
		AlertingRuleEntity alertingRuleEntity = null;
		Yaml yaml = new Yaml();
		
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(namespace).configMaps().withName(configMapName);
		if (dt.get() != null && dt.get().getData() != null) {
			PrometheusEntity pn = yaml.loadAs(dt.get().getData().get(dataTitle), PrometheusEntity.class);
			if (pn.getGroups() != null) {
				PrometheusGroups pg = pn.getGroups().get(0);
				for (int i = 0; i < pg.getRules().size(); i++) {
					String pgr = pg.getRules().get(i);
					AlertingRuleEntity ar = parseAlertRuleEntity(namespace,pgr);
					if(alert.equals(ar.getAlert())) {
						alertingRuleEntity = ar;
						break;
					}
				}
			}
		}
		return alertingRuleEntity;	
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private AlertingRuleEntity parseAlertRuleEntity(String namespace,String pgr) {
		AlertingRuleEntity ar = new AlertingRuleEntity();
		Yaml yaml = new Yaml();
		Map<String,Object> o = (Map) yaml.load(pgr);
		Map<String, String> label = (Map) o.get("labels");
		String expr = String.valueOf(o.get("expr"));
		Matcher m = Pattern.compile(".*(==|<|>) ((0\\.)?[\\d]+)$").matcher(expr);
		if (m.matches()) {
			ar.setCondition(m.group(1));
			ar.setValue2(m.group(2));
			ar.setValue(m.group(1) + m.group(2));
		}
		ar.setExpr(expr);
		String alert = String.valueOf(o.get("alert"));
		ar.setAlert(alert);
		ar.setNamespace(namespace);
		if (alert.indexOf(namespace) > -1) {
			ar.setServiceName(alert.substring(alert.indexOf(namespace), alert.length()));
			ar.setType(alert.substring(0, alert.indexOf(namespace) - 1));
		}
		ar.setDuration(String.valueOf(o.get("for")));
		ar.setChannel(String.valueOf(label.get("channel")));
		ar.setPriority(String.valueOf(label.get("priority")));
		ar.setSeverity(String.valueOf(label.get("severity")));
		return ar;
	}


	public boolean createAlertRule(AlertingRuleEntity alertingRuleEntity){
		boolean isSuccess = true;
		try {
			Yaml yaml = new Yaml(new Constructor(PrometheusEntity.class)); 
			String type = alertingRuleEntity.getType();
			String namespace = alertingRuleEntity.getNamespace();
			String alert = alertingRuleEntity.getAlert();
			String template = getPrometheusTemplate(type);
			String severity = "";
			if("P1".equals(alertingRuleEntity.getPriority())) {
				severity = "critical";
			}else if("P3".equals(alertingRuleEntity.getPriority())) {
				severity = "warning";
			}else if("P4".equals(alertingRuleEntity.getPriority())) {
				severity = "low";
			}
			alertingRuleEntity.setSeverity(severity);
			alertingRuleEntity.setChannel("default");
			
			template = template.replaceAll("\\$\\{serviceName\\}", alertingRuleEntity.getServiceName())
							.replaceAll("\\$\\{namespace\\}", alertingRuleEntity.getNamespace())
							.replaceAll("\\$\\{severity\\}", alertingRuleEntity.getSeverity())
							.replaceAll("\\$\\{channel\\}", alertingRuleEntity.getChannel())
							.replaceAll("\\$\\{priority\\}", alertingRuleEntity.getPriority())
							.replaceAll("\\$\\{duration\\}", alertingRuleEntity.getDuration())
							.replaceAll("\\$\\{condition\\}", alertingRuleEntity.getCondition())
							.replaceAll("\\$\\{value2\\}", alertingRuleEntity.getValue2());
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(namespace).configMaps().withName(configMapName);
			
			if(dt.get() == null) {
				ConfigMap c = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata().build();
				dt.createOrReplace(c);
			}
			
			PrometheusEntity pn = null;
			List<PrometheusGroups> pgl = null;
			PrometheusGroups pg = null;
			List<String> rules = null;
			if(dt.get().getData() == null || dt.get().getData().get(dataTitle) == null) {
				pn = new PrometheusEntity();
				pgl = new ArrayList<PrometheusGroups>();
				pg = new PrometheusGroups(dataTitle,new ArrayList<>());
				pn.setGroups(pgl);
				pgl.add(pg);
			}else {
				pn = yaml.loadAs(dt.get().getData().get(dataTitle),PrometheusEntity.class);
				pgl = pn.getGroups();
				if(pgl == null) {
					pgl = new ArrayList<PrometheusGroups>();
					pn.setGroups(pgl);
					pgl.add(new PrometheusGroups(dataTitle,new ArrayList<>()));
				}
				pg = pn.getGroups().get(0); 
			}
			rules = pg.getRules();				
			rules.add(template);
			
			String data = yaml.dumpAsMap(pn);
			dt.edit().addToData(dataTitle, data).done();
		}catch(Exception e) {
			log.error(e.getMessage(), e);
			isSuccess = false;
		}
		return isSuccess;
	}

	@SuppressWarnings("unchecked")
	private String getPrometheusTemplate(String type) {
		String re = "";
		Yaml yaml = new Yaml();
		ClassPathResource cp = new ClassPathResource("mariadb/prometheus-zdb-rules-template.yaml");
		
 		try {
 			Map<String,LinkedHashMap<String,Object>> template = (Map<String, LinkedHashMap<String, Object>>) yaml.load(cp.getInputStream());
 			LinkedHashMap<String,Object> r = template.get(type);
 			re = yaml.dumpAsMap(r);
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}	
 		return re;
	}


	public void deleteAlertRule(AlertingRuleEntity alertingRuleEntity) {
		try {
			String namespace = alertingRuleEntity.getNamespace();
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			Yaml yaml = new Yaml();
			
			Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(namespace).configMaps().withName(configMapName);
			if(dt.get()!=null && dt.get().getData()!=null) {
				PrometheusEntity pn = yaml.loadAs(dt.get().getData().get(dataTitle),PrometheusEntity.class);
				List<String> rules = pn.getGroups().get(0).getRules();
				for(int i = rules.size()-1 ; i > -1 ;i--) {
					Map<String,Object> rule = (Map<String, Object>) yaml.load(rules.get(i));
					if(alertingRuleEntity.getAlert().equals(rule.get("alert"))) {
						rules.remove(i);
						break;
					}
				}
				String data = yaml.dumpAsMap(pn);
				dt.edit().addToData(dataTitle, data).done();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

}
