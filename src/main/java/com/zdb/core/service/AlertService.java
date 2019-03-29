package com.zdb.core.service;

import com.zdb.core.domain.AlertingRule;
import com.zdb.core.domain.Result;

/**
 * AlertService Interface
 */
public interface AlertService {

  Result createAlertingRule(String txId, String alertingRuleType, String namespace, String serviceName, String threshold, String channel) throws Exception;
  
  Result getAlertingRule(final String alertingRuleName) throws Exception;
  
  Result updateAlertingRule(AlertingRule alertingRule) throws Exception;
  
  Result deleteAlertingRule(final String alertingRuleName) throws Exception;
  
  Result getAlerts() throws Exception;
}