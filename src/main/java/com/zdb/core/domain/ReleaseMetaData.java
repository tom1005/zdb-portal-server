package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ReleaseMetaData {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	@Column(name = "id")
	String uid;

	@Column(name = "userId")
	String userId;
	
	@Column(name = "createTime")
	Date createTime;
	
	@Column(name = "updateTime")
	Date updateTime;

	@Column(name = "chartVersion")
	String chartVersion;
	
	@Column(name = "chartName")
	String chartName;

	@Column(name = "releasename")
	String releaseName;
	
	@Column(name = "namespace")
	String namespace;
	
	@Column(length=1000, name = "description")
	String description;
	
	@Column(name = "dbname")
	String dbname;
	
	// mariadb, redis, postgresql ...
	@Column(name = "app")
	String app;
	
	@Column(name = "appVersion")
	String appVersion;
	
	@Column(name = "status")
	String status;
	
	@Column(name = "action")
	String action;
	
	@Lob
	@Column(length=5000000, name = "manifest")
	String manifest;

	@Lob
	@Column(length=5000000, name = "notes")
	String notes;
	
	@Lob
	@Column(length=5000000, name = "inputValues")
	String inputValues;
	
	@Column(name = "publicEnabled")
	Boolean publicEnabled;
	
	@Column(name = "purpose")
	String purpose;
	
	@Column(name = "clusterEnabled")
	Boolean clusterEnabled;
}
