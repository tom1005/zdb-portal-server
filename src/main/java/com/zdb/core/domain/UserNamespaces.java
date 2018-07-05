package com.zdb.core.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserNamespaces {

	@Id
	@GeneratedValue
	private Long id;
	
	private String userId;
	
	private String namespace;
}
