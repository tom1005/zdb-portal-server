package com.zdb.core.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.zdb.core.domain.AlertingRule;
import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.AlertingRuleTemplate;
import com.zdb.core.domain.Result;

import io.fabric8.kubernetes.api.model.ConfigMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class PrometheusManager {
  
  @Value("${spring.profiles.active}")
  private String profile;

  @Value("${alert.zdbAlertAgent.namespace}")
  private String zdbAlertAgentNamespace;
  
  @Value("${alert.zdbAlertAgent.configMap}")
  private String zdbAlertAgentConfigMap;

  @Value("${alert.alertManager.namespace}")
  private String alertmanagerNamespace;
  
  @Value("${alert.alertManager.configMap}")
  private String alertmanagerConfigMap;

  @Value("${alert.prometheus.namespace}")
  private String prometheusNamespace;

  @Value("${alert.prometheus.configMap}")
  private String prometheusConfigMap;

  /*
   * Alerting Rule Template을 조회합니다.
   * Profile이 'prod'면 ConfigMap에서 조회해오고, 그렇지 않으면('local'이면) 사전에 정의된 값으로 조회합니다.
   */
  public AlertingRuleTemplate getAlertingRuleTemplate(String type) throws Exception {
    AlertingRuleTemplate alertingRuleTemplate = new AlertingRuleTemplate();
    if (profile.equals("prod")) {
      return alertingRuleTemplate;
    } else {
      List<AlertingRuleTemplate> alertingRuleTemplates = getAlertingRuleTemplates();
      for (AlertingRuleTemplate t : alertingRuleTemplates) {
        if(t.getType().equals(type)) {
          alertingRuleTemplate = t;
        }
      }
      return alertingRuleTemplate;
    }
  }

  public Result updateAlertingRuleTemplate(AlertingRule alertingRule) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  public Result deleteAlertingRuleTemplate(String alertingRuleName) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  /*
   * Alerting Rule Template의 목록을 조회합니다.
   * Profile이 'prod'면 ConfigMap에서 조회해오고, 그렇지 않으면('local'이면) 사전에 정의된 값으로 조회합니다.
   */
  @SuppressWarnings("unused")
  public List<AlertingRuleTemplate> getAlertingRuleTemplates() {
    List<AlertingRuleTemplate> alertingRuleTemplates = new ArrayList<AlertingRuleTemplate>();
    if (profile.equals("prod")) {
      return alertingRuleTemplates;
    } else {
      AlertingRuleTemplate alertingRuleTemplate = new AlertingRuleTemplate();
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "PodHealthCheck", 
          "ZDB-MariaDB-PodHealthCheck-<SERVICENAME>",
          "absent(mysql_up{service=\"<SERVICENAME>\"}) == 1", 
          "1m", 
          "default", 
          "critical", 
          "P1", 
          "Pod 동작 중단",
          "MariaDB의 Pod 동작하지 않습니다. (서비스: {{ $labels.service }})"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "ContainerHealthCheck",
          "ZDB-MariaDB-ContainerHealthCheck-<SERVICENAME>",
          "kube_pod_container_status_ready{pod=~\"<SERVICENAME>.*\", container=\"mariadb\"} == 0", 
          "1m",
          "default", 
          "critical", 
          "P1", 
          "MariaDB 컨테이너 동작 중단",
          "MariaDB 컨테이너의 동작이 중단되었습니다. (서비스: {{ $labels.pod }})"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "DBHealthCheck", 
          "ZDB-MariaDB-DBHealthCheck-<SERVICENAME>",
          "mysql_up{service=~\"<SERVICENAME>.*\"} == 0", 
          "1m", 
          "default", 
          "critical", 
          "P1", 
          "MariaDB 동작 중단",
          "MariaDB의 동작이 중단되었습니다. (서비스: {{ $labels.service }})"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "CPUUsage", 
          "ZDB-MariaDB-CPUUsage",
          "(sum(rate(container_cpu_usage_seconds_total{pod_name=~\"<SERVICENAME>.*\", container_name=\"mariadb\"}[1m])) by (pod_name, container_name)) / ((sum(container_spec_cpu_quota{pod_name=~\"<SERVICENAME>.*\", container_name=\"mariadb\"}) by (pod_name, container_name)) / 100000) * 100 > <THRESHOLD>",
          "1m", 
          "default", 
          "warning", 
          "P3", 
          "CPU 사용량 임계치 초과",
          "CPU 사용량이 임계치를 초과하였습니다. (서비스: {{ $labels.pod_name }}, 임계값: <THRESHOLD>%, 현재값: {{ $value }}%)"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "MemoryUsage", 
          "ZDB-MariaDB-MemoryUsage-<SERVICENAME>",
          "(sum(container_memory_working_set_bytes{pod_name=~\"<SERVICENAME>.*\", container_name=\"mariadb\"}) by (pod_name, container_name)) / (sum (container_spec_memory_limit_bytes{pod_name=~\"<SERVICENAME>.*\", container_name=\"mariadb\"}) by (pod_name, container_name)) * 100 > <THRESHOLD>",
          "1m", 
          "default", 
          "warning", 
          "P3", 
          "메모리 사용량 임계치 초과",
          "메모리 사용량이 임계치를 초과하였습니다. (서비스: {{ $labels.pod_name }}, 임계값: <THRESHOLD>%, 현재값: {{ $value }}%)"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "Connections", 
          "ZDB-MariaDB-Connections-<SERVICENAME>",
          "(mysql_global_status_threads_connected{service=~\"<SERVICENAME>.*\"} / mysql_global_variables_max_connections{service=~\"<SERVICENAME>.*\"} * 100) > <THRESHOLD>",
          "1m", 
          "default", 
          "warning", 
          "P3", 
          "Connections 임계치 초과",
          "MariaDB의 Connnections값이 임계치를 초과하였습니다. (서비스: {{ $labels.service }}, 임계값: <THRESHOLD>%, 현재값: {{ $value }}%)"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "SlowQueries", 
          "ZDB-MariaDB-SlowQueries-<SERVICENAME>",
          "rate(mysql_global_status_slow_queries{service=~\"<SERVICENAME>.*\"}[2m]) > <THRESHOLD>", 
          "1m",
          "default", 
          "warning", 
          "P3", 
          "MariaDB Slow Queries",
          "MariaDB의 Slow Query가 발생했습니다. (서비스: {{ $labels.service }})"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "ReplicationStatus", 
          "ZDB-MariaDB-ReplicationStatus-<SERVICENAME>",
          "mysql_slave_status_slave_sql_running{service=\"<SERVICENAME>-slave\"} == 0 OR mysql_slave_status_slave_io_running{service=\"<SERVICENAME>-slave\"} == 0",
          "1m", 
          "default", 
          "low", 
          "P4", 
          "Replication 중단", 
          "MariaDB의 Replication이 정상적으로 수행되지 않고 있습니다. (서비스: {{ $labels.service }})"));
      alertingRuleTemplates.add(alertingRuleTemplate = new AlertingRuleTemplate(
          "ReplicationDelay", 
          "ZDB-MariaDB-ReplicationStatus-<SERVICENAME>", 
          "mysql_slave_status_sql_delay{service=\"<SERVICENAME>-slave\"} > <THRESHOLD>", 
          "1m",
          "default", 
          "low", 
          "P4", 
          "Replication Delay", 
          "MariaDB의 Replication 수행 중 지연이 발생하였습니다. (서비스: {{ $labels.service }}, 지연: {{ $value }}초)"));
      
      return alertingRuleTemplates;
    }
  }
  
  /*
   * Prometheus의 ConfigMap을 수정하여 Alert Rule을 생성합니다.
   */
  @SuppressWarnings({ "rawtypes", "unchecked"})
  public AlertingRule createAlertingRule(AlertingRuleEntity alertingRuleEntity) throws Exception {
    AlertingRule alertingRule = new AlertingRule();
    FileWriter fileWriter = null;
    List<AlertingRule> alertingRules = new ArrayList<AlertingRule>();
    try {
      ConfigMap configMap = K8SUtil.getConfigMaps(prometheusNamespace, prometheusConfigMap);
      Iterator<String> iterator = configMap.getData().keySet().iterator();
      List keyList = new LinkedList();
      while (iterator.hasNext()) {
        String key = iterator.next();
        keyList.add(key);
      }
      
      // 현재 ConfigMap을 파일에 쓰기
      File file = new File("prometheus-zdb-rules.yaml");
      fileWriter = new FileWriter(file, false);
      fileWriter.write(configMap.getData().get(keyList.get(0)));
      fileWriter.flush();
      
      // 새로운 Alerting Rule 추가하기
      HashMap<String, Object> newAlertingRule = new LinkedHashMap<String, Object>();
      newAlertingRule.put("alert", alertingRuleEntity.getAlertingRuleName());
      newAlertingRule.put("expr", alertingRuleEntity.getExpr());
      newAlertingRule.put("for", alertingRuleEntity.getDuration());
      
      HashMap<String, String> labels = new HashMap<String, String>();
      labels.put("severity", alertingRuleEntity.getSeverity());
      labels.put("channel", alertingRuleEntity.getChannel());
      newAlertingRule.put("labels", labels);

      HashMap<String, String> annotations = new HashMap<String, String>();
      annotations.put("summary", alertingRuleEntity.getSummary());
      annotations.put("description", alertingRuleEntity.getDescription());
      newAlertingRule.put("annotations", annotations);
      
      YamlReader yamlReader = new YamlReader(new FileReader("prometheus-zdb-rules.yaml"));
      Object object = yamlReader.read();
      Map<String, Map<String, Object>> mapGlobal = (Map) object;

      
    } catch (Exception e) {
      // TODO: handle exception
    }
    
    return alertingRule;
  }
}