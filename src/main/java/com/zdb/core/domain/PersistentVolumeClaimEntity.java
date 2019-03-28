package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PersistentVolumeClaimEntity {

	@Id
	@Column(name = "volumeName")
	String volumeName;

	@Column(name = "creationTimestamp")
	String creationTimestamp;

	@Column(name = "updateTimestamp")
	String updateTimestamp;
	
	@Column(name = "app")
	String app;

	@Column(name = "billingType")
	String billingType;

	@Column(name = "component")
	String component;
	
	@Column(name = "namespace")
	String namespace;
	
	@Column(name = "name")
	String name;
	
	@Column(name = "releaseName")
	String release;
	
	@Column(name = "phase")
	String phase;
	
	@Column(name = "uid")
	String uid;
	
	@Column(name = "storageClassName")
	String storageClassName;
	
	@Column(name = "accessModes")
	String accessModes;

	@Column(name = "storagSize")
	String storagSize;

	@Column(name = "resourceVersion")
	String resourceVersion;
	
	@Column(name = "zone")
	String zone;

	@Column(name = "region")
	String region;
}
