package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Lob;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MariadbVariable
 *
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@IdClass(MariaDBVariablePk.class)
public class MariaDBVariable {
	
	@Id
	@Column(length=64, name = "category")
	private String category;
	@Id
	@Column(length=64, name = "name")
	private String name;
	@Column(length=64, name = "alias")
	private String alias;
	
	private boolean dynamic;
	@Column(length=2048)
	private String defaultValue;
	@Column(length=64)
	private String variableType;
	@Column(length=2048)
	private String variableComment;
	@Column(length=21)
	private String numericMinValue;
	@Column(length=21)
	private String numericMaxValue;
	@Column(length=21)
	private String numericBlockSize;
	@Lob
	private String enumValueList;
	
	private String value;
	
}
