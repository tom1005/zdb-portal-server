package com.zdb.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
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

public class PrometheusConfigMapTest {

	final private String configMapName = "prometheus-zdb-rules";
	final private String dataTitle = "prometheus-zdb.rules";
	
	@Test
	public void testConfigMap(){
		try {
			AlertingRuleEntity alertingRuleEntity = new AlertingRuleEntity();
			alertingRuleEntity.setType("ZDB-MariaDB-SlowQueries");
			alertingRuleEntity.setNamespace("zdb-test");
			//alertingRuleEntity.setNamespace("zcp-system");
			alertingRuleEntity.setServiceName("zdb-test-aa");
			alertingRuleEntity.setPriority("P1");
			alertingRuleEntity.setChannel("default");
			alertingRuleEntity.setCondition(">");
			alertingRuleEntity.setValue2("0.2");
			alertingRuleEntity.setAlert(alertingRuleEntity.getType()+"-"+alertingRuleEntity.getServiceName());
			
			String severity = "";
			if("P1".equals(alertingRuleEntity.getPriority())) {
				severity = "critical";
			}else if("P3".equals(alertingRuleEntity.getPriority())) {
				severity = "warning";
			}else if("P4".equals(alertingRuleEntity.getPriority())) {
				severity = "low";
			}
			alertingRuleEntity.setSeverity(severity);
			
			//testClient(alertingRuleEntity);
			//addPrometheusZdbRule(alertingRuleEntity); 
			//deletePrometheusZdbRule(alertingRuleEntity); 
			getPrometheusZdbRuleList(alertingRuleEntity);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<AlertingRuleEntity> getPrometheusZdbRuleList(AlertingRuleEntity alertingRuleEntity) {
		List<AlertingRuleEntity> list = new ArrayList<>();
		try {
			Yaml yaml = new Yaml();
			String namespace = alertingRuleEntity.getNamespace();
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(namespace).configMaps().withName(configMapName);
			PrometheusEntity pn = yaml.loadAs(dt.get().getData().get(dataTitle),PrometheusEntity.class);
			if(pn.getGroups() !=null) {
				PrometheusGroups pg = pn.getGroups().get(0);
				for(int i = 0 ;i < pg.getRules().size(); i++) {
					AlertingRuleEntity ar = new AlertingRuleEntity();
					Map<String,Object> o = (Map)yaml.load(pg.getRules().get(i));
					Map<String,String> label = (Map)o.get("labels");
					String expr = String.valueOf(o.get("expr"));
					Matcher m = Pattern.compile(".*(==|<|>) ((0\\.)?[\\d]+)$").matcher(expr);
					if(m.matches()) {
						ar.setCondition(m.group(1));
						ar.setValue2(m.group(2));
					}
					ar.setExpr(expr);
					ar.setAlert(String.valueOf(o.get("alert")));
					ar.setDuration(String.valueOf(o.get("for")));
					ar.setChannel(String.valueOf(label.get("channel")));
					ar.setPriority(String.valueOf(label.get("priority")));
					ar.setSeverity(String.valueOf(label.get("severity")));
					list.add(ar);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
		
	}
	
	public void addPrometheusZdbRule(AlertingRuleEntity alertingRuleEntity) {
		try {
			Yaml yaml = new Yaml(new Constructor(PrometheusEntity.class)); 
			String type = alertingRuleEntity.getType();
			String namespace = alertingRuleEntity.getNamespace();
			String template = getPrometheusTemplate(type);
			
			template = template.replaceAll("\\$\\{serviceName\\}", alertingRuleEntity.getServiceName())
							.replaceAll("\\$\\{namespace\\}", alertingRuleEntity.getNamespace())
							.replaceAll("\\$\\{serviceName\\}", alertingRuleEntity.getServiceName())
							.replaceAll("\\$\\{severity\\}", alertingRuleEntity.getSeverity())
							.replaceAll("\\$\\{channel\\}", alertingRuleEntity.getChannel())
							.replaceAll("\\$\\{priority\\}", alertingRuleEntity.getPriority())
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
				pg = new PrometheusGroups();
				pgl.add(new PrometheusGroups(dataTitle,new ArrayList<>()));
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
			e.printStackTrace();
		}
	}
	
	public void deletePrometheusZdbRule(AlertingRuleEntity alertingRuleEntity) {
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
			e.printStackTrace();
		}
	}
	
	private String getPrometheusTemplate(String type) {
		String re = "";
		Yaml yaml = new Yaml();
		ClassPathResource cp = new ClassPathResource("mariadb/prometheus-zdb-rules-template.yaml");
		
 		try {
 			Map<String,LinkedHashMap<String,Object>> template = (Map<String, LinkedHashMap<String, Object>>) yaml.load(cp.getInputStream());
 			LinkedHashMap<String,Object> r = template.get(type);
 			re = yaml.dumpAsMap(r);
		} catch (IOException e) {
			e.printStackTrace();
		}	
 		return re;
	}
	private void testClient(AlertingRuleEntity alertingRuleEntity) {
		try {
			Yaml yaml = new Yaml();
			String namespace = alertingRuleEntity.getNamespace();
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(namespace).configMaps().withName(configMapName);
			Map<String, String> cm = dt.get().getData();
			//ConfigMap c = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata().build();
			//dt.createOrReplace(c);
			System.out.println(dt.get());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
