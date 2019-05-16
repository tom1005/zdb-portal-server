package com.zdb.core.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import com.zdb.core.domain.AlertRule;
import com.zdb.core.domain.AlertRuleLabels;
import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.PrometheusEntity;
import com.zdb.core.domain.PrometheusGroups;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.repository.ZDBReleaseRepository;
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

	@Autowired ZDBReleaseRepository releaseRepository;
	@Autowired Yaml yaml;

	public List<AlertingRuleEntity> getAlertRules(String namespaces) throws Exception {
		List<AlertingRuleEntity> list = new ArrayList<>();
		
		if(!StringUtils.isEmpty(namespaces)) {
			List<String> namespaceList = Arrays.asList(namespaces.split(","));
			Resource<ConfigMap, DoneableConfigMap> dt = getAlertRuleConfigMap();
			
			if(dt.get() !=null && dt.get().getData() != null) {
				String d = dt.get().getData().get(dataTitle);
				PrometheusEntity pn = yaml.loadAs(d,PrometheusEntity.class);
				if(pn.getGroups() !=null) {
					PrometheusGroups pg = pn.getGroups().get(0);
					for(int i = 0 ;i < pg.getRules().size(); i++) {
						AlertRule ar = pg.getRules().get(i);
						AlertingRuleEntity are = parseAlertRuleEntity(ar);
						if(namespaceList.indexOf(are.getNamespace()) > -1) {
							if(are.getType().indexOf("Replication") == -1 && are.getAlert().endsWith("-slave")) {
								continue;
							}
							list.add(are);
						}
					}
				}
			}
		}
		return list;
	}

	public AlertingRuleEntity getAlertRule(String namespace, String alert) throws Exception {
		AlertingRuleEntity alertingRuleEntity = null;
		Resource<ConfigMap, DoneableConfigMap> dt = getAlertRuleConfigMap();
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
			Resource<ConfigMap, DoneableConfigMap> dt = getAlertRuleConfigMap();
			String d = dt.get().getData().get(dataTitle);
			PrometheusEntity pn = yaml.loadAs(d,PrometheusEntity.class);
			List<PrometheusGroups> pgl = pn.getGroups();
			PrometheusGroups pg = pgl.get(0); 
			List<AlertRule> rules = pg.getRules();
			
			addRulesTemplate(rules,alertingRuleEntity);
			String data = parseWriteAlertRule(pn);
			dt.edit().addToData(dataTitle, data).done();
		}catch(Exception e) {
			e.printStackTrace();
			log.error(e.getMessage(), e);
			isSuccess = false;
		}
		return isSuccess;
	}

	private void addRulesTemplate(List<AlertRule> rules, AlertingRuleEntity alertingRuleEntity) {
		String template = getPrometheusTemplate(alertingRuleEntity.getType()); 
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
		String re = template.replaceAll("\\$\\{role\\}", "master")
							.replaceAll("\\$\\{exprRole\\}", "");
		rules.add(yaml.loadAs(re,AlertRule.class));
		
		//Replication 관련설정이 아니면 slave가 존재하는지 검사 후 추가
		if(alertingRuleEntity.getType().indexOf("Replication")==-1) {
			ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(alertingRuleEntity.getServiceName());
			boolean hasSlave = releaseMeta!=null && releaseMeta.getClusterEnabled();
			if(hasSlave) {
				re = template.replaceAll("\\$\\{role\\}", "slave")
							 .replaceAll("\\$\\{exprRole\\}", "-slave");
				rules.add(yaml.loadAs(re, AlertRule.class));
			}
		}
		
	}

	public boolean deleteAlertRule(AlertingRuleEntity alertingRuleEntity) {
		boolean isSuccess = true;
		try {
			Resource<ConfigMap, DoneableConfigMap> dt = getAlertRuleConfigMap();
			String d = dt.get().getData().get(dataTitle);
			PrometheusEntity pn = yaml.loadAs(d,PrometheusEntity.class);
			List<AlertRule> rules = pn.getGroups().get(0).getRules();
			for(int i = rules.size()-1 ; i > -1 ;i--) {
				AlertRule rule = rules.get(i);
				String as = String.format("%s-%s",alertingRuleEntity.getType(),alertingRuleEntity.getServiceName());
				if(rule.getAlert().startsWith(as)) {
					rules.remove(i);
				}
			}
			String data = parseWriteAlertRule(pn);
			dt.edit().addToData(dataTitle, data).done();
		} catch (Exception e) {
			isSuccess = false;
			log.error(e.getMessage(), e);
		}
		return isSuccess;
	}
	
	@SuppressWarnings("unchecked")
	public boolean updateDefaultAlertRule(AlertingRuleEntity alertingRuleEntity) {
		boolean isSuccess = true;
		
		try {
			Resource<ConfigMap, DoneableConfigMap> dt = getAlertRuleConfigMap();
			String d = dt.get().getData().get(dataTitle);
			PrometheusEntity pn = yaml.loadAs(d,PrometheusEntity.class);
			List<AlertRule> rules = pn.getGroups().get(0).getRules();
			for(int i = rules.size()-1 ; i > -1 ;i--) {
				AlertRule rule = rules.get(i);
				String asn = alertingRuleEntity.getServiceName();
				if(rule.getAlert().endsWith(asn+"-mariadb-master")||rule.getAlert().endsWith(asn+"-mariadb-slave")) {
					rules.remove(i);
				}
			}
			ClassPathResource cp = new ClassPathResource("mariadb/prometheus-zdb-rules-default-value.yaml");
			Map<String, LinkedHashMap<String, Object>> re = (Map<String, LinkedHashMap<String, Object>>) yaml.load(cp.getInputStream());
			Iterator<String> it = re.keySet().iterator();
			while(it.hasNext()) {
				String type = it.next();
				LinkedHashMap<String, Object> defMap = re.get(type);
				alertingRuleEntity.setType(type);
				alertingRuleEntity.setCondition(String.valueOf(defMap.get("condition")));
				alertingRuleEntity.setValue2(String.valueOf(defMap.get("value")));
				alertingRuleEntity.setPriority(String.valueOf(defMap.get("priority")));
				alertingRuleEntity.setDuration(String.valueOf(defMap.get("for")));
				addRulesTemplate(rules,alertingRuleEntity);
			}
			String data = parseWriteAlertRule(pn);
			dt.edit().addToData(dataTitle, data).done();
		} catch (Exception e) {
			isSuccess = false;
			log.error(e.getMessage(), e);
		}
		return isSuccess;		
	}
	
	//alertRule 설정 configmap
	private Resource<ConfigMap, DoneableConfigMap> getAlertRuleConfigMap() throws Exception{
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(targetNamespace).configMaps().withName(configMapName);
		
		if(dt.get().getData() == null || dt.get().getData().get(dataTitle) == null) {
			ConfigMap c = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata().build();
			dt.createOrReplace(c);
			PrometheusEntity pn = null;
			List<PrometheusGroups> pgl = null;
			PrometheusGroups pg = null;			
			pn = new PrometheusEntity();
			pgl = new ArrayList<PrometheusGroups>();
			pg = new PrometheusGroups(dataTitle,new ArrayList<>());
			pn.setGroups(pgl);
			pgl.add(pg);			
			String data = parseWriteAlertRule(pn);
			dt.edit().addToData(dataTitle, data).done();
		}
		return dt;
	}
	
	@SuppressWarnings("unchecked")
	private String getPrometheusTemplate(String type) {
		String template = "";
		ClassPathResource cp = new ClassPathResource("mariadb/prometheus-zdb-rules-template.yaml");
		
		try {
			Map<String, LinkedHashMap<String, Object>> re = (Map<String, LinkedHashMap<String, Object>>) yaml.load(cp.getInputStream());
			template = yaml.dumpAsMap(re.get(type));
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}	
 		return template;
	}

	private String parseWriteAlertRule(PrometheusEntity pn) {
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
