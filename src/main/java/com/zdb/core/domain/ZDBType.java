package com.zdb.core.domain;

import java.util.Arrays;

public enum ZDBType {

	MariaDB("mariadb"), Redis("redis"), PostgreSQL("postgresql"), RabbitMQ("rabbitmq"), MongoDB("mongodb");

	private String name;

	ZDBType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
	
	public static boolean contains(String typeName) {
		return Arrays.asList(ZDBType.values()).contains(getType(typeName));
	}

	public static ZDBType getType(String typeName) {
		return Arrays.stream(ZDBType.values()).filter(payGroup -> payGroup.isEquals(typeName)).findAny().orElse(null);
	}

	public boolean isEquals(String typeName) {
		return getName().equals(typeName);
	}
}
