package com.zdb.core.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ZDBMariaDBConfig
 * - https://myshare.skcc.com/display/SKMONITOR/BSP+MariaDB+Service
 * 
 * @author nojinho@bluedigm.com
 *
 */
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ZDBMariaDBConfig {

	@Id
	@GeneratedValue(generator = "uuid")
	@GenericGenerator(name = "uuid", strategy = "uuid2")
	@Column(name = "id")
	private String id;

	private String releaseName;

	/*
	 * event_scheduler
	 * group_concat_max_len
	 * max_connections
	 * wait_timeout
	 */
//	private String eventScheduler;	
//	private String groupConcatMaxLen;
//	private String maxConnections;
//	private String waitTimeout;

	private String back_log;
	private String binlog_cache_size;
	private String bulk_insert_buffer_size;
	private String character_set_server;
	private String collation_server;
	private String connect_timeout;
	private String innodb_buffer_pool_instances;
	private String innodb_buffer_pool_size;
	private String innodb_flush_method;
	private String innodb_lock_wait_timeout;
	private String innodb_log_file_size;
	private String innodb_log_files_in_group;
	private String innodb_read_io_threads;
	private String innodb_sort_buffer_size;
	private String innodb_write_io_threads;
	private String interactive_timeout;
	private String join_buffer_size;
	private String key_buffer_size;
	private String local_infile;
	private String long_query_time;
	private String max_allowed_packet;
	private String max_connections;
	private String myisam_sort_buffer_size;
	private String query_cache_limit;
	private String query_cache_size;
	private String query_cache_type;
	private String read_buffer_size;
	private String read_rnd_buffer_size;
	private String sort_buffer_size;
	private String thread_handling;
	private String thread_pool_idle_timeout;
	private String thread_pool_stall_limit;
	private String tmp_table_size;
	private String transaction_isolation;
	private String wait_timeout;
}
