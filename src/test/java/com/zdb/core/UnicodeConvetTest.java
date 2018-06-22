package com.zdb.core;

import java.nio.charset.StandardCharsets;

public class UnicodeConvetTest {

	public static void main(String[] args) throws Exception {

		String text = "\\u001b[0m\\n\\u001b[0m\\u001b[1mWelcome to the Bitnami redis container\\u001b[0m\\n\\u001b[0mSubscribe to project updates by watching \\u001b[1mhttps://github.com/bitnami/bitnami-docker-redis\\u001b[0m\\n\\u001b[0mSubmit issues and feature requests at \\u001b[1mhttps://github.com/bitnami/bitnami-docker-redis/issues\\u001b[0m\\n\\u001b[0m\\nnami    INFO  Initializing redis\\nredis   INFO  \\u003d\\u003d\\u003e Validating inputs...\\nredis   INFO \\nredis   INFO  ########################################################################\\nredis   INFO   Installation parameters for redis:\\nredis   INFO     Password: **********\\nredis   INFO     Replication Mode: master\\nredis   INFO   (Passwords are not shown for security reasons)\\nredis   INFO  ########################################################################\\nredis   INFO \\nnami    INFO  redis successfully initialized\\n\\u001b[0m\\u001b[38;5;2mINFO \\u001b[0m \\u003d\\u003d\\u003e Starting redis... \\n36:C 20 Jun 16:17:36.346 # oO0OoO0OoO0Oo Redis is starting oO0OoO0OoO0Oo\\n36:C 20 Jun 16:17:36.346 # Redis version\\u003d4.0.9, bits\\u003d64, commit\\u003d00000000, modified\\u003d0, pid\\u003d36, just started\\n36:C 20 Jun 16:17:36.346 # Configuration loaded\\n36:M 20 Jun 16:17:36.349 * Running mode\\u003dstandalone, port\\u003d6379.\\n36:M 20 Jun 16:17:36.349 # WARNING: The TCP backlog setting of 511 cannot be enforced because /proc/sys/net/core/somaxconn is set to the lower value of 128.\\n36:M 20 Jun 16:17:36.349 # Server initialized\\n36:M 20 Jun 16:17:36.349 # WARNING you have Transparent Huge Pages (THP) support enabled in your kernel. This will create latency and memory usage issues with Redis. To fix this issue run the command \\u0027echo never \\u003e /sys/kernel/mm/transparent_hugepage/enabled\\u0027 as root, and add it to your /etc/rc.local in order to retain the setting after a reboot. Redis must be restarted after THP is disabled.\\n36:M 20 Jun 16:17:36.349 * Ready to accept connections\\n36:M 20 Jun 16:17:43.429 * Slave 172.30.68.49:6379 asks for synchronization\\n36:M 20 Jun 16:17:43.429 * Full resync requested by slave 172.30.68.49:6379\\n36:M 20 Jun 16:17:43.429 * Starting BGSAVE for SYNC with target: disk\\n36:M 20 Jun 16:17:43.430 * Background saving started by pid 52\\n52:C 20 Jun 16:17:43.433 * DB saved on disk\\n52:C 20 Jun 16:17:43.434 * RDB: 6 MB of memory used by copy-on-write\\n36:M 20 Jun 16:17:43.466 * Background saving terminated with success\\n36:M 20 Jun 16:17:43.466 * Synchronization with slave 172.30.68.49:6379 succeeded\\n36:M 20 Jun 16:17:44.137 * Slave 172.30.176.31:6379 asks for synchronization\\n36:M 20 Jun 16:17:44.137 * Full resync requested by slave 172.30.176.31:6379\\n36:M 20 Jun 16:17:44.137 * Starting BGSAVE for SYNC with target: disk\\n36:M 20 Jun 16:17:44.138 * Background saving started by pid 53\\n53:C 20 Jun 16:17:44.140 * DB saved on disk\\n53:C 20 Jun 16:17:44.229 * RDB: 6 MB of memory used by copy-on-write\\n36:M 20 Jun 16:17:44.330 * Background saving terminated with success\\n36:M 20 Jun 16:17:44.330 * Synchronization with slave 172.30.176.31:6379 succeeded";

		String unescapeString = unescapeJava(text);
		System.out.println(unescapeString.replace("\\n",System.getProperty("line.separator")));
	}

	public static String unescapeJava(String escaped) {
	    if(escaped.indexOf("\\u")==-1)
	        return escaped;

	    String processed="";

	    int position=escaped.indexOf("\\u");
	    while(position!=-1) {
	        if(position!=0)
	            processed+=escaped.substring(0,position);
	        String token=escaped.substring(position+2,position+6);
	        escaped=escaped.substring(position+6);
	        processed+=(char)Integer.parseInt(token,16);
	        position=escaped.indexOf("\\u");
	    }
	    processed+=escaped;

	    return processed;
	}
}