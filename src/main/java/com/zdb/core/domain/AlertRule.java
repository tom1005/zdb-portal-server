package com.zdb.core.domain;

import javax.persistence.Column;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AlertRule {
	private String alert;
	private String	expr;
	@Column(name="for")
	private String forVariable;
	private AlertRuleLabels labels; 
	private AlertRuleAnnotations annotations;

	public void setFor(String forVariable) {
		this.forVariable = forVariable;
	}
	public String getFor() {
		return this.forVariable;
	}
}
