package com.zdb.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.zdb.core.domain.AlertingRule;
import com.zdb.core.domain.AlertingRuleEntity;
import com.zdb.core.domain.AlertingRuleTemplate;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Result;
import com.zdb.core.repository.AlertingRuleRepository;
import com.zdb.core.util.PrometheusManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("alertService")
@Configuration
public abstract class AlertServiceImpl implements AlertService{
  
  @Autowired
  private AlertingRuleRepository alertingRuleRepository;
  
  @Autowired
  private PrometheusManager prometheusManager;
  
  @Override
  public Result createAlertingRule(String txId, String alertingRuleType, String namespace, String serviceName, String threshold, String channel) throws Exception {       
    try {
      AlertingRuleTemplate alertingRuleTemplate = prometheusManager.getAlertingRuleTemplate(alertingRuleType);
      AlertingRuleEntity alertingRuleEntity = new AlertingRuleEntity();
      alertingRuleEntity.setAlertingRuleName(alertingRuleTemplate.getAlertingRuleName().replace("<SERVICENAME>", serviceName));
      alertingRuleEntity.setDuration(alertingRuleTemplate.getDuration());
      alertingRuleEntity.setChannel(channel);
      alertingRuleEntity.setSeverity(alertingRuleTemplate.getSeverity());
      alertingRuleEntity.setPriority(alertingRuleTemplate.getPriority());
      alertingRuleEntity.setSummary(alertingRuleTemplate.getSummary());
      if (alertingRuleTemplate.getExpr().contains("<THRESHOLD>")) {
        alertingRuleEntity.setExpr(alertingRuleTemplate.getExpr().replace("<SERVICENAME>", serviceName).replace("<THRESHOLD>", threshold));
      } else {
        alertingRuleEntity.setExpr(alertingRuleTemplate.getExpr().replace("<SERVICENAME>", serviceName));
      }
      if (alertingRuleTemplate.getDescription().matches("<THRESHOLD>")) {
        alertingRuleEntity.setDescription(alertingRuleTemplate.getDescription().replace("<THRESHOLD>", threshold));
      } else {
        alertingRuleEntity.setDescription(alertingRuleTemplate.getDescription());
      }
      
      AlertingRule alertingRule = prometheusManager.createAlertingRule(alertingRuleEntity);
      return new Result(txId, Result.OK, "Alerting Rule이 생성되었습니다.").putValue("alertingRule", alertingRule);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return new Result(txId, Result.ERROR, e.getMessage(), e);
    }
  }

  @Override
  public Result getAlertingRule(String alertingRuleName) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Result updateAlertingRule(AlertingRule alertingRule) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Result deleteAlertingRule(String alertingRuleName) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Result getAlerts() throws Exception {
    // TODO Auto-generated method stub
    return null;
  }
}