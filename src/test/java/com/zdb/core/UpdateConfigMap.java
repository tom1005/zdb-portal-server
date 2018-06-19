package com.zdb.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

import com.zdb.core.domain.Mycnf;
import com.zdb.core.domain.Result;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorable;
import io.fabric8.kubernetes.client.utils.BlockingInputStreamPumper;
import okhttp3.Response;

public class UpdateConfigMap {

	public static void main(String[] args) {
		new UpdateConfigMap().doUpdate();
	}

	public void doUpdate() {

		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {

			InputStream is = new ClassPathResource("mariadb/mycnf.template").getInputStream();
			
			String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());

			inputJson = inputJson.replace("${local_infile}", "0");
			inputJson = inputJson.replace("${wait_timeout}", "3600");
			inputJson = inputJson.replace("${interactive_timeout}", "3600");

			inputJson = inputJson.replace("${query_cache_size}", "0");
			inputJson = inputJson.replace("${query_cache_type}", "0");
			inputJson = inputJson.replace("${query_cache_limit}", "0");

			inputJson = inputJson.replace("${character_set_server}", "utf8");
			inputJson = inputJson.replace("${collation_server}", "utf8_general_ci");

			inputJson = inputJson.replace("${connect_timeout}", "10");
			inputJson = inputJson.replace("${max_connections}", "200");

			inputJson = inputJson.replace("${back_log}", "200");
			inputJson = inputJson.replace("${join_buffer_size}", "64k");
			inputJson = inputJson.replace("${read_buffer_size}", "1M");
			inputJson = inputJson.replace("${read_rnd_buffer_size}", "1M");
			inputJson = inputJson.replace("${sort_buffer_size}", "64k");
			inputJson = inputJson.replace("${tmp_table_size}", "64M");

			inputJson = inputJson.replace("${binlog_cache_size}", "1M");
			inputJson = inputJson.replace("${table_open_cache}", "4000");
			inputJson = inputJson.replace("${transaction-isolation}", "READ-COMMITTED");

			inputJson = inputJson.replace("${long_query_time}", "5");

			inputJson = inputJson.replace("${innodb_buffer_pool_size}", "256M");
			inputJson = inputJson.replace("${innodb_flush_method}", "O_DIRECT");
			inputJson = inputJson.replace("${innodb_lock_wait_timeout}", "15");
			inputJson = inputJson.replace("${innodb_log_file_size}", "100M");
			inputJson = inputJson.replace("${innodb_log_files_in_group}", "4");
			inputJson = inputJson.replace("${innodb_read_io_threads}", "4");
			inputJson = inputJson.replace("${innodb_write_io_threads}", "8");
			inputJson = inputJson.replace("${innodb_buffer_pool_instances}", "4");
			inputJson = inputJson.replace("${innodb_sort_buffer_size}", "4M");

			inputJson = inputJson.replace("${bulk_insert_buffer_size}", "16M");
			inputJson = inputJson.replace("${key_buffer_size}", "32M");
			inputJson = inputJson.replace("${myisam_sort_buffer_size}", "16M");

			inputJson = inputJson.replace("${thread_handling}", "pool-of-threads");
			inputJson = inputJson.replace("${thread_pool_stall_limit}", "60");
			inputJson = inputJson.replace("${thread_pool_idle_timeout}", "120");
					   
			inputJson = inputJson.replace("${max_allowed_packet}", "16M");
			inputJson = inputJson.replace("${default-time-zone}", "+02:00");

			System.out.println(inputJson);
			
			client.configMaps().inNamespace("zdb-maria").withName("zdb-maria-pns-mariadb-master").edit().addToData("my.cnf", inputJson).done();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		}
	}

	
}
