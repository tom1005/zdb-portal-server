package com.zdb.core.service;

import com.zdb.core.domain.Exchange;

public interface ZDBInstaller {
	
	static public String MARIADB_RESOURCE_CPU_DEFAULT = "250m";
	static public String MARIADB_RESOURCE_MEMORY_DEFAULT = "256Mi";
	static public String REDIS_RESOURCE_CPU_DEFAULT = "100m";
	static public String REDIS_RESOURCE_MEMORY_DEFAULT = "256Mi";
	
	/**
	 * @param exchange
	 */
	public void doInstall(Exchange exchange);
	
	public void doUnInstall(Exchange exchange);

}