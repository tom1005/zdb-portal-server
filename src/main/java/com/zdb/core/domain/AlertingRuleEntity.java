package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class AlertingRuleEntity {
  private String alertingRuleName;
  private String expr;
  private String duration;
  private String channel;
  private String severity;
  private String priority;
  private String summary;
  private String description;
}