package com.zdb.manager;

import com.zdb.core.util.ExecUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class UpdatePrivileges {

	public static void main(String[] args) throws Exception {
		// mysql -uroot -p$MARIADB_ROOT_PASSWORD -e "REVOKE ALL PRIVILEGES ON *.* FROM '$MARIADB_USER'@'%';GRANT ALL PRIVILEGES ON *.* TO '$MARIADB_USER'@'%' IDENTIFIED BY '$MARIADB_PASSWORD' WITH GRANT OPTION;GRANT CREATE USER ON *.* TO '$MARIADB_USER'@'%';UPDATE mysql.user SET super_priv='N' WHERE user <> 'root' and user <> 'replicator';FLUSH PRIVILEGES;"
		StringBuffer sb = new StringBuffer();
		sb.append("REVOKE ALL PRIVILEGES ON *.* FROM '$MARIADB_USER'@'%';");
		sb.append("GRANT ALL PRIVILEGES ON *.* TO '$MARIADB_USER'@'%' IDENTIFIED BY '$MARIADB_PASSWORD' WITH GRANT OPTION;");
		sb.append("GRANT CREATE USER ON *.* TO '$MARIADB_USER'@'%';");
		sb.append("UPDATE mysql.user SET super_priv='N' WHERE user <> 'root' and user <> 'replicator';");
		sb.append("FLUSH PRIVILEGES;");
		
		System.out.println(sb.toString());
		System.out.println("====================================================================================================");
		
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		String result = new ExecUtil().exec(client, "ns-zdb-02", "ns-zdb-02-hhh-mariadb-master-0", "exec mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \""+sb.toString()+"\"");
		System.out.println("====================================================================================================");
		System.out.println(">"+result+"<");
		System.out.println("====================================================================================================");
	}

}
