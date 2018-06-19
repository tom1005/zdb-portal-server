package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OneToOne;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class EventMetaData {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	@Column(name = "id")
	private String id;
	
	@Column(name = "uid")
	String uid;

	@Column(name = "name")
	String name;

	@Column(name = "firstTimestamp")
	String firstTimestamp;
	
	@Column(name = "lastTimestamp")
	String lastTimestamp;

	@Column(name = "kind")
	String kind;

	@Column(name = "namespace")
	String namespace;
	
	@JoinColumn(name = "release_name")
	@Column(name = "releaseName")
	String releaseName;
	
	@Lob
	@Column(length=1000000, name = "message")
	String message;
	
	@Lob
	@Column(length=1000000, name = "reason")
	String reason;
	
	@Lob
	@Column(length=1000000, name = "metadata")
	String metadata;
}
