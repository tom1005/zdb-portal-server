package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

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
public class MariaDBVariable {

	private String category;

	@Id
	private String name;
	private String alias;
	private String value;
	private String label;
	private String valueRange;
	private String dataType;
	private boolean dynamic;
	
	@Column(length=4000, name = "description")
	private String description;
	
}