package com.zdb.core;

import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.zdb.core.domain.AlertRule;
import com.zdb.core.domain.AlertRuleLabels;
import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.MariadbUserPrivileges;
import com.zdb.core.domain.PrometheusEntity;
import com.zdb.core.domain.PrometheusGroups;
import com.zdb.core.domain.UserPrivileges;
import com.zdb.core.util.K8SUtil;
import com.zdb.mariadb.MariaDBConnection;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrometheusConfigMapTest {

	final private String configMapName = "prometheus-zdb-rules";
	final private String dataTitle = "prometheus-zdb.rules";
	final private String targetNamespace = "zdb-test";
	
	@Test
	public void testConfigMap(){
		try {
			AlertingRuleEntity alertingRuleEntity = new AlertingRuleEntity();
			alertingRuleEntity.setType("ZDB-MariaDB-SlowQueries");
			alertingRuleEntity.setNamespace("zdb-test");
			//alertingRuleEntity.setNamespace("zcp-system");
			alertingRuleEntity.setServiceName("zdb-test-abc");
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
			
			
			//init();
			//createAlertRule(alertingRuleEntity);
			//deleteAlertRule(alertingRuleEntity); 
			//getAlertRules("zdb-test");
			testClient(alertingRuleEntity);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void init()throws Exception {
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		Resource<ConfigMap, DoneableConfigMap> dt = client.inNamespace(targetNamespace).configMaps().withName(configMapName);
		
		//dt.delete();
		ConfigMap c = new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata().build();
		dt.createOrReplace(c);
	}
	private void testClient(AlertingRuleEntity alertingRuleEntity)throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		List<MariadbUserPrivileges> userPrivilegesList = new ArrayList<>();
		String [] privileges = {"SELECT","INSERT","UPDATE","DELETE","EXECUTE","SHOW VIEW","CREATE","ALTER","REFERENCES","INDEX","CREATE VIEW"
				,"CREATE ROUTINE","ALTER ROUTINE","EVENT","DROP","TRIGGER","GRANT","CREATE TMP TABLE","LOCK TABLES"};
		
		try {
			connection = MariaDBConnection.getRootMariaDBConnection("zdb-test", "zdb-test-mat");
			statement = connection.getStatement();
			StringBuffer q = new StringBuffer();
			q.append(" SELECT GRANTEE grantee, TABLE_SCHEMA \"schema\" ");
			for(String privilege : privileges) {
				q.append(String.format(" ,SUM(IF(PRIVILEGE_TYPE = '%s',1,0)) AS \"%s\" ",privilege
							,privilege.toLowerCase().replaceAll("( [a-z]{1})", ("$1").trim().toUpperCase())));
			}
			q.append(" FROM (SELECT GRANTEE, TABLE_SCHEMA, PRIVILEGE_TYPE FROM INFORMATION_SCHEMA.SCHEMA_PRIVILEGES");
			q.append(" UNION ALL ");
			q.append(" SELECT GRANTEE, '*',PRIVILEGE_TYPE FROM INFORMATION_SCHEMA.USER_PRIVILEGES");
			q.append(" )A GROUP BY GRANTEE,TABLE_SCHEMA ");
			
			ResultSet rs = statement.executeQuery(q.toString());
			
			while (rs.next()) {
				MariadbUserPrivileges mp = new MariadbUserPrivileges();
				Class cls = mp.getClass();
				for(String privilege : privileges) {
					String c = privilege.toLowerCase().replaceAll("( [a-z]{1})", ("$1").trim().toUpperCase());
					Method m = cls.getMethod("set"+c.substring(0,1).toUpperCase()+c.substring(1),String.class);
					m.invoke(mp,rs.getString(c)); 
				}
				userPrivilegesList.add(mp);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(statement!=null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
		System.out.println(userPrivilegesList);
	}
	private void testClient2(AlertingRuleEntity alertingRuleEntity)throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		String namespace = "zdb-test";
		String serviceName = "zdb-test-mat";
		List<UserPrivileges> userPrivilegesList = new ArrayList<>();

		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			statement = connection.getStatement();
			StringBuffer q = new StringBuffer();
			q.append("SELECT GRANTEE, TABLE_CATALOG, TABLE_SCHEMA, PRIVILEGE_TYPE, IS_GRANTABLE FROM INFORMATION_SCHEMA.SCHEMA_PRIVILEGES");
			q.append(" UNION ALL ");
			q.append("SELECT GRANTEE, TABLE_CATALOG, '*',PRIVILEGE_TYPE, IS_GRANTABLE FROM INFORMATION_SCHEMA.USER_PRIVILEGES");
			
			ResultSet rs = statement.executeQuery(q.toString());
			//String 
			while (rs.next()) {
				UserPrivileges u = new UserPrivileges();
				u.setGrantee(rs.getString("GRANTEE"));
				u.setTableCatalog(rs.getString("TABLE_CATALOG"));
				u.setTableSchema(rs.getString("TABLE_SCHEMA"));
				u.setPrivilegeType(rs.getString("PRIVILEGE_TYPE"));
				u.setIsGrantable(rs.getString("IS_GRANTABLE"));
				userPrivilegesList.add(u);
			}
			 Map<Object, Map<Object, List<UserPrivileges>>> g = userPrivilegesList.stream().collect(Collectors.groupingBy(ob -> ob.getGrantee(),Collectors.groupingBy(ob->ob.getTableSchema())));
			System.out.println(g);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(statement!=null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
 		
	}

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
		//re = yaml.dump(re);
		
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
			are.setValue(m.group(1) + m.group(2));
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
