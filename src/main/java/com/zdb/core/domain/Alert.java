package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Alert {
  private String time;
  private String severity;
  private String type;
  private String receiver;
  private String description;
}