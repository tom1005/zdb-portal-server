package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OneToOne;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class MetaData {

	@Id
	@Column(name = "uid")
	String uid;

	@Column(name = "createTime")
	Date createTime;
	
	@Column(name = "updateTime")
	Date updateTime;

	@Column(name = "kind")
	String kind;

	@Column(name = "releasename")
	String releaseName;
	
	@Column(name = "namespace")
	String namespace;
	
	@Column(name = "name")
	String name;
	
	@Column(name = "app")
	String app;
	
	@Column(name = "status")
	String status;
	
	@Column(name = "action")
	String action;
	
	@Column(name = "ready")
	boolean ready;

	@Lob
	@Column(length=1000000, name = "metadata")
	String metadata;
}
