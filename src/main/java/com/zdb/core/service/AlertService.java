package com.zdb.core.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.zdb.core.domain.AlertRule;
import com.zdb.core.domain.AlertRuleLabels;
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
	final private String targetNamespace = "zcp-system";

	public List<AlertingRuleEntity> getAlertRules(String namespaces) throws Exception {
		List<AlertingRuleEntity> list = new ArrayList<>();
		Yaml yaml = new Yaml();
		
		if(!StringUtils.isEmpty(namespaces)) {
			List<String> namespaceList = Arrays.asList(namespaces.split(","));
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(targetNamespace).configMaps().withName(configMapName);
			if(dt.get() !=null && dt.get().getData() != null) {
				String d = dt.get().getData().get(dataTitle);
				PrometheusEntity pn = yaml.loadAs(d,PrometheusEntity.class);
				if(pn.getGroups() !=null) {
					PrometheusGroups pg = pn.getGroups().get(0);
					for(int i = 0 ;i < pg.getRules().size(); i++) {
						AlertRule ar = pg.getRules().get(i);
						AlertingRuleEntity are = parseAlertRuleEntity(ar);
						if(namespaceList.indexOf(are.getNamespace()) > -1) {
							list.add(are);
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
		Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(targetNamespace).configMaps().withName(configMapName);
		if (dt.get() != null && dt.get().getData() != null) {
			String d = dt.get().getData().get(dataTitle);
			PrometheusEntity pn = yaml.loadAs(d,PrometheusEntity.class);
			if (pn.getGroups() != null) {
				PrometheusGroups pg = pn.getGroups().get(0);
				for (int i = 0; i < pg.getRules().size(); i++) {
					AlertRule ar = pg.getRules().get(i);
					AlertingRuleEntity are = parseAlertRuleEntity(ar);
					if(alert.equals(are.getAlert())) {
						alertingRuleEntity = are;
						break;
					}
				}
			}
		}
		return alertingRuleEntity;	
	}

	public boolean createAlertRule(AlertingRuleEntity alertingRuleEntity){
		boolean isSuccess = true;
		try {
			Yaml yaml = new Yaml(new Constructor(PrometheusEntity.class)); 
			String type = alertingRuleEntity.getType();
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
							.replaceAll("\\$\\{value2\\}", alertingRuleEntity.getValue2())
							.replaceAll("\\$\\{namespace\\}", alertingRuleEntity.getNamespace());
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(targetNamespace).configMaps().withName(configMapName);
			
			if(dt.get() == null) {
				ConfigMap c = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata().build();
				dt.createOrReplace(c);
			}
			
			PrometheusEntity pn = null;
			List<PrometheusGroups> pgl = null;
			PrometheusGroups pg = null;
			List<AlertRule> rules = null;
			if(dt.get().getData() == null || dt.get().getData().get(dataTitle) == null) {
				pn = new PrometheusEntity();
				pgl = new ArrayList<PrometheusGroups>();
				pg = new PrometheusGroups(dataTitle,new ArrayList<>());
				pn.setGroups(pgl);
				pgl.add(pg);
			}else {
				String d = dt.get().getData().get(dataTitle);
				pn = yaml.loadAs(d,PrometheusEntity.class);
				pgl = pn.getGroups();
				if(pgl == null) {
					pgl = new ArrayList<PrometheusGroups>();
					pn.setGroups(pgl);
					pgl.add(new PrometheusGroups(dataTitle,new ArrayList<>()));
				}
				pg = pn.getGroups().get(0); 
			}
			rules = pg.getRules();				
			rules.add(yaml.loadAs(template,AlertRule.class));
			String data = parseWriteAlertRule(pn);
			dt.edit().addToData(dataTitle, data).done();
		}catch(Exception e) {
			log.error(e.getMessage(), e);
			isSuccess = false;
		}
		return isSuccess;
	}

	public void deleteAlertRule(AlertingRuleEntity alertingRuleEntity) {
		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			Yaml yaml = new Yaml();
			
			Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(targetNamespace).configMaps().withName(configMapName);
			if(dt.get()!=null && dt.get().getData()!=null) {
				String d = dt.get().getData().get(dataTitle);
				PrometheusEntity pn = yaml.loadAs(d,PrometheusEntity.class);
				List<AlertRule> rules = pn.getGroups().get(0).getRules();
				for(int i = rules.size()-1 ; i > -1 ;i--) {
					AlertRule rule = rules.get(i);
					if(alertingRuleEntity.getAlert().equals(rule.getAlert())) {
						rules.remove(i);
						break;
					}
				}
				String data = parseWriteAlertRule(pn);
				dt.edit().addToData(dataTitle, data).done();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
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

	private String parseWriteAlertRule(PrometheusEntity pn) {
		Yaml yaml = new Yaml();
		String re = yaml.dumpAsMap(pn);
		re = re.replaceAll("forVariable:.*[\\s]+", "");
		return re;
	}
	
	private AlertingRuleEntity parseAlertRuleEntity(AlertRule ar) {
		AlertingRuleEntity are = new AlertingRuleEntity();
		AlertRuleLabels label = ar.getLabels();
		String expr = String.valueOf(ar.getExpr());
		Matcher m = Pattern.compile(".*(==|<|>) ((0\\.)?[\\d]+)$").matcher(expr);
		if (m.matches()) {
			are.setCondition(m.group(1));
			are.setValue2(m.group(2));
			are.setValue(m.group(1) + " " + m.group(2));
		}
		are.setExpr(expr);
		String alert = String.valueOf(ar.getAlert());
		are.setAlert(alert);
		are.setNamespace(label.getNamespace());
		String serviceName = label.getServiceName();
		are.setServiceName(serviceName);
		if (alert.indexOf(serviceName) > -1) {
			are.setType(alert.substring(0, alert.indexOf(serviceName) - 1));
		}
		are.setDuration(ar.getFor());
		are.setChannel(label.getChannel());
		are.setPriority(label.getPriority());
		are.setSeverity(label.getSeverity());
		return are;
	}
}
