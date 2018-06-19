package com.zdb.core.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.zdb.core.domain.Mycnf;
import com.zdb.core.repository.MycnfRepository;

public class InitData {
	static Map<String, String> defaultConfigMap = new HashMap<>();
	static {
		defaultConfigMap.put("back_log", "200|100이상|false|number");
		defaultConfigMap.put("binlog_cache_size", "1M|4096 to 18446744073709547520|true|numeric");
		defaultConfigMap.put("bulk_insert_buffer_size", "16M|0 ~ 18446744073709547520|true|numeric");
		defaultConfigMap.put("character_set_server", "utf8|utf8,euckr|true|string");
		defaultConfigMap.put("collation_server", "utf8_general_ci||true|string");
		defaultConfigMap.put("connect_timeout", "10|2이상|true|numeric");
		defaultConfigMap.put("innodb_buffer_pool_instances", "4|1,2,4,8|false|numeric");
		defaultConfigMap.put("innodb_buffer_pool_size", "256M|5M ~ 8192PB|false|numeric");
		defaultConfigMap.put("innodb_flush_method", "O_DIRECT|fsync, O_DSYNC, O_DIRECT, O_DIRECT_NO_FSYNC (>=MariaDB 10.0)|false|numeric");
		defaultConfigMap.put("innodb_lock_wait_timeout", "15|1 to 1073741824 (<= MariaDB 10.2)|true|numeric");
		defaultConfigMap.put("innodb_log_file_size", "100M|1048576 to 512GB|false|numeric");
		defaultConfigMap.put("innodb_log_files_in_group", "4|2 to 100|false|numeric");
		defaultConfigMap.put("innodb_read_io_threads", "4|1 to 64|false|numeric");
		defaultConfigMap.put("innodb_sort_buffer_size", "4M|65536 to 67108864|false|numeric");
		defaultConfigMap.put("innodb_write_io_threads", "8|1 to 64|false|numeric");
		defaultConfigMap.put("interactive_timeout", "3600|1 to 31536000|true|numeric");
		defaultConfigMap.put("join_buffer_size", "64k|128 to 18446744073709547520|true|numeric");
		defaultConfigMap.put("key_buffer_size", "32M|8 이상|true|numeric");
		defaultConfigMap.put("local_infile", "0|1 , 0|true|boolean");
		defaultConfigMap.put("long_query_time", "5|0 이상|true|numeric");
		defaultConfigMap.put("max_allowed_packet", "16M|1024 ~ 1073741824|true|numeric");
		defaultConfigMap.put("max_connections", "200|1~100000|true|numeric");
		defaultConfigMap.put("myisam_sort_buffer_size", "16M|4096 ~ 18446744073709547520|true|numeric");
		defaultConfigMap.put("query_cache_limit", "0|0 ~ 4294967295|true|numeric");
		defaultConfigMap.put("query_cache_size", "0|1024 단위의 0보다 큰값|true|numeric");
		defaultConfigMap.put("query_cache_type", "0|O or OFF, 1 or ON, 2 or DEMAND|true|-");
		defaultConfigMap.put("read_buffer_size", "1M|8200 ~ 2147479552|true|numeric");
		defaultConfigMap.put("read_rnd_buffer_size", "1M|8200 ~ 2147483647|true|numeric");
		defaultConfigMap.put("sort_buffer_size", "64k||true|numeric");
		defaultConfigMap.put("table_open_cache", "4000|1 ~ 1048576 (>= MariaDB 10.1.20)|true|numeric");
		defaultConfigMap.put("thread_handling", "pool-of-threads|no-threads, one-thread-per-connection, pool-of-threads|false|-");
		defaultConfigMap.put("thread_pool_idle_timeout", "120||true|numeric");
		defaultConfigMap.put("thread_pool_stall_limit", "60|4 to 600|true|numeric");
		defaultConfigMap.put("tmp_table_size", "64M|1K ~ 409M|true|numeric");
		defaultConfigMap.put("transaction-isolation", "READ-COMMITTED|READ-UNCOMMITTED, READ-COMMITTED, REPEATABLE-READ, SERIALIZABLE|true|-");
		defaultConfigMap.put("wait_timeout", "3600|1 ~ 31536000|true|numeric");

	}

	/**
	 * 
	 */
	public static void initData(MycnfRepository configRepository) {
		//
		for (Iterator<String> iterator = defaultConfigMap.keySet().iterator(); iterator.hasNext();) {
			String key = iterator.next();
			String temp = defaultConfigMap.get(key);

			String[] valueTemp = temp.split("\\|");

			// name, value, label, range, dataType, dynamic, description
			Mycnf cnf = new Mycnf();
			cnf.setName(key);
			cnf.setValue(valueTemp[0]);
			cnf.setDataType(valueTemp[3]);
			cnf.setDynamic(Boolean.parseBoolean(valueTemp[2]));
			cnf.setValueRange(valueTemp[1]);

			configRepository.save(cnf);
		}
	}
}
