package com.zdb.manager;

import com.zdb.core.util.ExecUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class UpdatePrivileges {

	public static void main(String[] args) throws Exception {
		// mysql -uroot -p$MARIADB_ROOT_PASSWORD -e "REVOKE ALL PRIVILEGES ON *.* FROM
		// '$MARIADB_USER'@'%';GRANT ALL PRIVILEGES ON *.* TO '$MARIADB_USER'@'%'
		// IDENTIFIED BY '$MARIADB_PASSWORD' WITH GRANT OPTION;GRANT CREATE USER ON *.*
		// TO '$MARIADB_USER'@'%';UPDATE mysql.user SET super_priv='N' WHERE user <>
		// 'root' and user <> 'replicator';FLUSH PRIVILEGES;"
		// sb.append("REVOKE ALL PRIVILEGES ON *.* FROM '$MARIADB_USER'@'%';");
		// sb.append("GRANT ALL PRIVILEGES ON *.* TO '$MARIADB_USER'@'%' IDENTIFIED BY
		// '$MARIADB_PASSWORD' WITH GRANT OPTION;");
		// sb.append("GRANT CREATE USER ON *.* TO '$MARIADB_USER'@'%';");
		// sb.append("UPDATE mysql.user SET super_priv='N' WHERE user <> 'root' and user
		// <> 'replicator';");
		// sb.append("FLUSH PRIVILEGES;");
		//
		// System.out.println(sb.toString());
		// System.out.println("====================================================================================================");

		String userId = "admin";
		String password = "jLk86nytXk";
		String namespace = "zdb-test";
		String podName = "zdb-test-pns2-mariadb-slave-0";
		String container = "mariadb";

		StringBuffer sb = new StringBuffer();
		 sb.append("mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"");
		// sb.append("REVOKE ALL PRIVILEGES ON *.* FROM '" + userId + "'@'%';");
		// sb.append("FLUSH PRIVILEGES;");
		// sb.append("GRANT ALL PRIVILEGES ON *.* TO '" + userId + "'@'%' IDENTIFIED BY '"+password+"' WITH GRANT OPTION;");
		// sb.append("GRANT CREATE USER ON *.* TO '" + userId + "'@'%';");
		// sb.append("UPDATE mysql.user SET super_priv='N' WHERE user <> 'root' and user <> 'replicator';");
		// sb.append("FLUSH PRIVILEGES;\"");
		  sb.append("show databases;\"");
//		sb.append("/opt/bitnami/mariadb/bin/mysqladmin -uroot -p$MARIADB_ROOT_PASSWORD status;");

		System.out.println(sb.toString());
		int ii = 1;
		while (true) {
			try (DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
				System.out.println("" + (ii++));

				String result = new ExecUtil().exec(client, namespace, podName, container, sb.toString());
				System.out.println("====================================================================================================");
				System.out.println(">" + result + "<");
				System.out.println("====================================================================================================");

			} catch (Exception e) {
				e.printStackTrace();
				Thread.sleep(3000);
			}
		}
	}

}
