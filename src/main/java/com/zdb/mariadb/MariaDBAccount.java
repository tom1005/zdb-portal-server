package com.zdb.mariadb;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.zdb.core.domain.DBUser;
import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.ZDBMariaDBAccountRepository;
import com.zdb.core.util.ExecUtil;
import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.Getter;

/**
 * 
 * A class to control mariadb variables
 * 
 * @author nojinho@bluedigm.com
 *
 */

public class MariaDBAccount {
	private static final Logger logger = LoggerFactory.getLogger(MariaDBAccount.class);

	@Inject
	protected ZDBMariaDBAccountRepository accountRepository;

	@Getter
	private String id;
	@Getter
	private String password;
	@Getter
	private ZDBMariaDBAccount account;

	public MariaDBAccount(final String releaseName, final String id) {
		this.id = id;

		this.account = accountRepository.findByReleaseNameAndUserId(releaseName, id);
		if (this.account != null) {
			this.password = this.account.getUserPassword();
		}
	}

	public MariaDBAccount(final String id, final String password, final ZDBMariaDBAccount account) {
		this.id = id;
		this.password = password;
		this.account = account;
	}

	public MariaDBAccount(final ZDBMariaDBAccount account) {
		this.id = account.getUserId();
		this.password = account.getUserPassword();
		this.account = account;
	}

	/**
	 * create new MariaDB account
	 * 
	 * @param id
	 * @param password
	 * @param accessIp
	 * @param create
	 * @param read
	 * @param update
	 * @param delete
	 * @param grant
	 * 
	 * @return ZDBMariaDBAccount
	 */
	public ZDBMariaDBAccount addAccount(final String id, final String password, final String accessIp, final boolean create, final boolean read, final boolean update, final boolean delete, final boolean grant) {
		ZDBMariaDBAccount account = new ZDBMariaDBAccount();

		// TODO: set all fields.

		return account;
	}

	/**
	 * Update a MariaDB account
	 * 
	 * @param id
	 * @param password
	 * @param accessIp
	 * @param create
	 * @param read
	 * @param update
	 * @param delete
	 * @param grant
	 * 
	 * @return ZDBMariaDBAccount
	 */
	public ZDBMariaDBAccount updateAccount(final String id, final String password, final String accessIp, final boolean create, final boolean read, final boolean update, final boolean delete, final boolean grant) {
		ZDBMariaDBAccount account = new ZDBMariaDBAccount();

		account.setAccessIp(accessIp);
		account.setUserPassword(password);
		account.setCreate(create);
		account.setRead(read);
		account.setUpdate(update);
		account.setDelete(delete);
		account.setGrant(grant);

		accountRepository.save(account);

		return account;
	}
	
	public static void main(String[] a) {
		//maria-test079-mariadb-master
		
		ZDBMariaDBAccount acc = new ZDBMariaDBAccount();
		acc.setAccessIp("%");
		acc.setCreate(true);
		acc.setDelete(true);
		acc.setUpdate(true);
		acc.setRead(true);
		acc.setUserId("zdbadmin2");
		acc.setGrant(true);
		
		ZDBMariaDBAccount bacc = new ZDBMariaDBAccount();
		bacc.setAccessIp("%");
		bacc.setCreate(true);
		bacc.setDelete(true);
		bacc.setUpdate(true);
		bacc.setRead(true);
		bacc.setUserId("zdbadmin2");
		bacc.setGrant(true);
		;
		updateAccount(null, "zdb-maria","maria-test007", bacc, acc);
	}

	/**
	 * Update a MariaDB account
	 * 
	 * @param id
	 * @param password
	 * @param accessIp
	 * @param create
	 * @param read
	 * @param update
	 * @param delete
	 * @param grant
	 * 
	 * @return ZDBMariaDBAccount
	 */
	public static ZDBMariaDBAccount updateAccount(final ZDBMariaDBAccountRepository repo, final String namespace, 
			final String releaseName, final ZDBMariaDBAccount accountBefore, final ZDBMariaDBAccount account) {
		MariaDBConnection connection = null;

		try {
			String database = getMariaDBDatabase(namespace, releaseName);
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			Statement statement = connection.getStatement();

			if (!accountBefore.equalsPrivileges(account)) {
				updateMariaDBPrivileges(statement, database, namespace, releaseName, account);
			}

			if (!accountBefore.getUserPassword().equals(account.getUserPassword())) {
				updateMariaDBPassword(statement, account);
			}

			account.setId(accountBefore.getId());
			repo.save(account);
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return account;
	}
	
	public static ZDBMariaDBAccount updateAdminPassword(final ZDBMariaDBAccountRepository repo, final String namespace, final String releaseName, final String pwd) {
		MariaDBConnection connection = null;
		ZDBMariaDBAccount account = null;
		try {
			String userId = "admin";
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			Statement statement = connection.getStatement();

			account = repo.findByReleaseNameAndUserId(releaseName, userId);
			if(account == null) {
				account = new ZDBMariaDBAccount();
			}
			account.setAccessIp("%");
			account.setUserId(userId);
			account.setUserPassword(pwd);
			
			updateMariaDBPassword(statement, account);

			repo.save(account);
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

		return account;
	}
	
	public static void updateAdminPrivileges(String namespace, String releaseName, String userId) throws Exception {
		MariaDBConnection connection = null;
		try {
			List<Secret> secrets = K8SUtil.getSecrets(namespace, releaseName);
			if( secrets == null || secrets.isEmpty()) {
				throw new Exception("등록된 Secret이 없거나 조회 중 오류 발생. [" + namespace +" > "+releaseName +"]");
			}
			
			String password = "";
			for(Secret secret : secrets) {
				Map<String, String> secretData = secret.getData();
				password = secretData.get("mariadb-password");
				
				if (password != null && !password.isEmpty()) {
					password = new String(Base64.getDecoder().decode(password));
					password = password.trim();
				}
				break;
			}
			
			String container = "mariadb";

			while(true) {
				try(DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
//					connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
					
					List<Pod> items = K8SUtil.kubernetesClient().inNamespace(namespace).pods()
							.withLabel("release", releaseName)
							.withLabel("component", "master")
							.list().getItems();
					
					String podName = "";
					for (Pod pod : items) {
						podName = pod.getMetadata().getName();
					}
					
					String cmd = "mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"show databases;\"";
					
					String result = new ExecUtil().exec(client, namespace, podName, container, cmd);
					
					if(result != null && result.indexOf("mysql") > -1) {
						break;
					}
				}catch(Exception e) {
					Thread.sleep(5000);
				}
			}
			
//			Statement statement = connection.getStatement();
//			
//			statement.executeUpdate("REVOKE ALL PRIVILEGES ON *.* FROM '" + userId + "'@'%';");
//			statement.executeUpdate("FLUSH PRIVILEGES");
//			statement.executeUpdate("GRANT ALL PRIVILEGES ON *.* TO '" + userId + "'@'%' IDENTIFIED BY '"+password+"' WITH GRANT OPTION");
//			statement.executeUpdate("GRANT CREATE USER ON *.* TO '" + userId + "'@'%'");
//			statement.executeUpdate("UPDATE mysql.user SET super_priv='N' WHERE user <> 'root' and user <> 'replicator'");
//			statement.executeUpdate("FLUSH PRIVILEGES");
			
			List<Pod> items = K8SUtil.kubernetesClient().inNamespace(namespace).pods()
					.withLabel("release", releaseName)
					.withLabel("component", "master")
					.list().getItems();
			
			String podName = "";
			for (Pod pod : items) {
				podName = pod.getMetadata().getName();
			}
			
			
			StringBuffer sb = new StringBuffer();
			sb.append("mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"");
			sb.append("REVOKE ALL PRIVILEGES ON *.* FROM '" + userId + "'@'%';");
			sb.append("FLUSH PRIVILEGES;");
			sb.append("GRANT ALL PRIVILEGES ON *.* TO '" + userId + "'@'%' IDENTIFIED BY '"+password+"' WITH GRANT OPTION;");
			sb.append("GRANT CREATE USER ON *.* TO '" + userId + "'@'%';");
			sb.append("UPDATE mysql.user SET super_priv='N' WHERE user <> 'root' and user <> 'replicator';");
			sb.append("FLUSH PRIVILEGES;\"");
			
			System.out.println(sb.toString());
			
			try(DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
				String result = new ExecUtil().exec(client, namespace, podName, container, sb.toString());
				System.out.println("====================================================================================================");
				System.out.println(">"+result+"<");
				System.out.println("====================================================================================================");
				
			} catch(Exception e) {
				logger.error(releaseName + ">" + releaseName +" " +userId +" 관리자 권한 설정 오류.", e);
				throw e;
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			throw e;
		} finally {
			if(connection != null) {
				connection.close();
			}
		}
	}

	private static void updateMariaDBPassword(final Statement statement, final ZDBMariaDBAccount account) {
		try {
			String query = " SET PASSWORD FOR '" + account.getUserId() + "'@'" + account.getAccessIp() + "' = PASSWORD('" + account.getUserPassword() + "')";
			logger.debug("query: {}", query);

			statement.executeUpdate(query);
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
		}
	}

	private static void revokeMariaDBAllPrivileges(final Statement statement, final String database, final ZDBMariaDBAccount account) {
		try {
			String privilegeTypes = buildPrivilegeType(account);
			if (privilegeTypes.isEmpty()) {
				throw new Exception("invalid mariadb privileges.");
			}

			logger.debug("privilegeTypes: {}", privilegeTypes);
			String query = "REVOKE ALL PRIVILEGES ON `" + database + "`.* FROM '" + account.getUserId() + "'@'" + account.getAccessIp() + "'";
			logger.debug("query: {}", query);

			statement.executeUpdate(query);
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
		}
	}

	private static void grantMariaDBPrivileges(final Statement statement, final String database, final ZDBMariaDBAccount account) {
		try {
			String privilegeTypes = buildPrivilegeType(account);
			if (privilegeTypes.isEmpty()) {
				throw new Exception("invalid mariadb privileges.");
			}

			logger.debug("privilegeTypes: {}", privilegeTypes);
			String query = "GRANT " + privilegeTypes + " ON `" + database + "`.* TO '" + account.getUserId() + "'@'" + account.getAccessIp() + "'";
			logger.debug("query: {}", query);

			statement.executeUpdate(query);
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
		}
	}

	private static ZDBMariaDBAccount updateMariaDBPrivileges(final Statement statement, final String database, final String namespace, final String releaseName, final ZDBMariaDBAccount account) {
		revokeMariaDBAllPrivileges(statement, database, account);
		grantMariaDBPrivileges(statement, database, account);

		return account;
	}

	/**
	 * delete an account
	 * 
	 * @param id
	 */
	public void deleteAccount(final String id) {
		accountRepository.delete(id);
	}

	public static void deleteAccounts(final ZDBMariaDBAccountRepository repo, final String namespace, final String serviceName) {
		logger.debug("delete accounts. releaseName: {}", serviceName);
		repo.deleteByReleaseName(serviceName);
	}

	public ZDBMariaDBAccount getAccount(final String id) {
		return accountRepository.getOne(id);
	}

	private static String getSecret(final String namespace, final String releaseName, final String key) {
		Secret secret = null;
		try {
			secret = K8SUtil.getSecret(namespace, releaseName);
			if (secret != null) {
				Map<String, String> data = secret.getData();

				if (!data.isEmpty()) {
					if (logger.isDebugEnabled()) {
						logger.debug("secret: {}", data.get(key));
					}

					return data.get(key);
				}
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			return null;
		}

		return null;
	}

	private static String getAdminPassword(final String namespace, final String releaseName) {
		return getSecret(namespace, releaseName, "mariadb-password");
	}

	public static String getRootPassword(final String namespace, final String releaseName) {
		return getSecret(namespace, releaseName, "mariadb-root-password");
	}

	private static String getAdminId(final String namespace, final String deploymentName) throws Exception {
		return getMariaDBEnv(namespace, deploymentName, "MARIADB_USER");
	}

	public static MariaDBAccount getAdminAccount(final String namespace, final String releaseName) throws Exception {
		MariaDBAccount account = null;
		String adminId = getAdminId(namespace, releaseName);
		String adminPassword = getAdminPassword(namespace, releaseName);

		if (logger.isDebugEnabled()) {
			logger.debug("adminId: {}", adminId);
			logger.debug("adminPassword: {}", adminPassword);
		}

		if (adminId != null && adminPassword != null) {
			account = new MariaDBAccount(adminId, adminPassword);
		}

		return account;
	}

	public ZDBMariaDBAccount save() {
		return this.accountRepository.save(this.account);
	}

	/**
	 * @param namespace
	 * @param deploymentName
	 * @param envKey
	 * @return
	 */
	private static String getMariaDBEnv(final String namespace, final String releaseName, final String envKey) throws Exception {
//		ServiceOverview serviceOverview = K8SUtil.getServiceWithName(namespace, ZDBType.MariaDB.name(), releaseName);
		List<StatefulSet> statefulSets = K8SUtil.getStatefulSets(namespace, releaseName);
		for(StatefulSet sfs : statefulSets) {
			String component = sfs.getMetadata().getLabels().get("component");
			String app = sfs.getMetadata().getLabels().get("app");
			
			if(ZDBType.MariaDB.name().equalsIgnoreCase(app) && "master".equals(component)) {
				List<Container> containers = sfs.getSpec().getTemplate().getSpec().getContainers();

				for (Container container : containers) {
					if (container.getName().equals("mariadb")) {
						List<EnvVar> envs = container.getEnv();
						for (EnvVar env : envs) {
							if (env.getName().equals(envKey)) {
								return env.getValue();
							}
						}
					}
				}
			}
		}

		return null;
	}

	public static String getMariaDBDatabase(String namespace, String releaseName) throws Exception {
		return getMariaDBEnv(namespace, releaseName, "MARIADB_DATABASE");
	}

	private static String buildPrivilegeType(final ZDBMariaDBAccount account) {
		StringBuilder sb = new StringBuilder();

		if (account.isCreate()) {
			sb.append("INSERT");
		}

		if (account.isRead()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}

			sb.append("SELECT");
		}

		if (account.isUpdate()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}

			sb.append("UPDATE");
		}

		if (account.isDelete()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}

			sb.append("DELETE");
		}

		return sb.toString();
	}

	/**
	 * @param namespace
	 * @param releaseName
	 * @param account
	 * @return
	 */
	public static DBUser createAccount(final String namespace, final String releaseName, final DBUser account) throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			if( connection != null) {
				statement = connection.getStatement();
				
				query = String.format("CREATE USER '%s'@'%s' identified by '%s'",account.getUser(),account.getHost(),account.getPassword());
				logger.info("query: {}", query);
				statement.executeUpdate(query);
				
				List<String> privilegeTypes = getPrivilegeList(account);
				if (!privilegeTypes.isEmpty()) {
					query = String.format("GRANT %s ON *.* TO '%s'@'%s' IDENTIFIED BY '%s'",
										String.join(",",privilegeTypes),account.getUser(),account.getHost(),account.getPassword());
					logger.debug("query: {}", query);
					statement.executeUpdate(query);
				}
				
			} else {
				throw new Exception("cannot create connection.");
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
			if(statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return account;
	}
	/**
	 * Update a MariaDB account
	 * 
	 * @param id
	 * @param password
	 * @param accessIp
	 * @param create
	 * @param read
	 * @param update
	 * @param delete
	 * @param grant
	 * 
	 * @return DBUser
	 */
	public static DBUser updateAccount(final String namespace, final String releaseName,DBUser account) throws Exception  {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			statement = connection.getStatement();
			
			query = String.format("REVOKE ALL PRIVILEGES ON *.* FROM '%s'@'%s';", account.getUser(),account.getHost());
			statement.executeUpdate(query);
			List<String> privilegeTypes = getPrivilegeList(account);
			if (!privilegeTypes.isEmpty()) {
				query = String.format("GRANT %s ON *.* TO '%s'@'%s' IDENTIFIED BY '%s';", String.join(",",privilegeTypes),account.getUser(),account.getHost(),account.getPassword());
				logger.debug("query: {}", query);
				statement.executeUpdate(query);
			}
			
			if (!StringUtils.isEmpty(account.getPassword())) {
				query = String.format(" SET PASSWORD FOR '%s'@'%s' = PASSWORD('%s');",account.getUser(),account.getHost(),account.getPassword());
				logger.debug("query: {}", query);
				statement.executeUpdate(query);
			}
			
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return account;
	}
	public static DBUser deleteAccount(final String namespace,final String releaseName,DBUser account)throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			statement = connection.getStatement();
			
			List<String> blackList = Arrays.asList("admin","root");
			if(blackList.indexOf(account.getUser()) == -1) {
				query = String.format("DROP USER '%s'@'%s';", account.getUser(),account.getHost());
				statement.executeQuery(query);
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return account;		
	}
	private static List<String> getPrivilegeList(DBUser account) {
		List<String> privilegeList = new ArrayList<>();
		String [] grantCols = {"select","insert","update","delete","execute","create","alter","drop","createView","trigger","grant","createUser"};
		
		Class cls = account.getClass();
		for(String col : grantCols) {
			try {
				Method m = cls.getMethod("get"+col.substring(0,1).toUpperCase()+col.substring(1));
				String yn = (String)m.invoke(account);
				if(yn.equals("Y")) {
					privilegeList.add(col);
				}
			} catch (Exception e) {
			}
		}
		return privilegeList;
	}
	public static ZDBMariaDBAccount createAdminAccount(final ZDBMariaDBAccountRepository repo, final String namespace, final String releaseName, final String id, final String password) {
		ZDBMariaDBAccount account = new ZDBMariaDBAccount(null, releaseName, id, password, "%", true, true, true, true, true);
		repo.save(account);

		return account;
	}

	public static ZDBMariaDBAccount getAccount(final ZDBMariaDBAccountRepository repo, final String namespace, final String releaseName, final String id) {
		ZDBMariaDBAccount account = repo.findByReleaseNameAndUserId(releaseName, id);
		return account;
	}

	public static List<ZDBMariaDBAccount> getAccounts(final ZDBMariaDBAccountRepository repo, final String namespace, final String releaseName) {
		List<ZDBMariaDBAccount> accounts = repo.findAllByReleaseName(releaseName);
		return accounts;
	}
	
	public static List<DBUser> getUserGrants(String namespace, String releaseName) throws Exception {
		MariaDBConnection connection = null;

		List<DBUser> userList = new ArrayList<DBUser>();

		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			Statement statement = connection.getStatement();

			StringBuffer q = new StringBuffer();

			q.append("select ");
			q.append("	user as 'USER', ");
			q.append("	host as 'HOST', ");
			q.append("	Select_priv as 'SELECT', ");
			q.append("	Insert_priv as 'INSERT', ");
			q.append("	Update_priv as 'UPDATE', ");
			q.append("	Delete_priv as 'DELETE', ");
			q.append("	Execute_priv as 'EXECUTE', ");
			q.append("	Create_priv as 'CREATE', ");
			q.append("	Alter_priv as 'ALTER', ");
			q.append("	Drop_priv as 'DROP', ");
			q.append("	Create_view_priv as 'CREATE_VIEW', ");
			q.append("	Trigger_priv as 'TRIGGER', ");
			q.append("	Grant_priv as 'GRANT', ");
			q.append("	Create_user_priv as 'CREATE_USER' ");
			q.append("from  ");
			q.append("	mysql.user ");
			q.append("where ");
			q.append("  1 = 1");
			q.append("	and user <> 'root' ");
			q.append("	and user <> 'replicator' ");
			
			logger.debug("query: {}", q.toString());

			ResultSet rs = statement.executeQuery(q.toString());
			while (rs.next()) {
				String user = rs.getString("USER");
				String host = rs.getString("HOST");
				String select = rs.getString("SELECT");
				String insert = rs.getString("INSERT");
				String update = rs.getString("UPDATE");
				String delete = rs.getString("DELETE");
				String execute = rs.getString("EXECUTE");
				String create = rs.getString("CREATE");
				String alter = rs.getString("ALTER");
				String drop = rs.getString("DROP");
				String createView = rs.getString("CREATE_VIEW");
				String trigger = rs.getString("TRIGGER");
				String grant = rs.getString("GRANT");
				String createUser = rs.getString("CREATE_USER");
				
				DBUser dbUser = new DBUser();
				dbUser.setUser(user);
				dbUser.setHost(host);
				dbUser.setSelect(select);
				dbUser.setInsert(insert);
				dbUser.setUpdate(update);
				dbUser.setDelete(delete);
				dbUser.setExecute(execute);
				dbUser.setCreate(create);
				dbUser.setAlter(alter);
				dbUser.setDrop(drop);
				dbUser.setCreateView(createView);
				dbUser.setTrigger(trigger);
				dbUser.setGrant(grant);
				dbUser.setCreateUser(createUser);
				
				userList.add(dbUser);
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			throw e;
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
		
		return userList;
	}

	public static void deleteAccount(final ZDBMariaDBAccountRepository repo, final String namespace, final String releaseName, final String id) throws Exception {
		MariaDBConnection connection = null;
		ZDBMariaDBAccount account = repo.findByReleaseNameAndUserId(releaseName, id);

		if(account != null) {
			try {
				connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
				Statement statement = connection.getStatement();
				
				String query = "DROP USER IF EXISTS'" + id + "'@'" + account.getAccessIp() + "'";
				logger.debug("query: {}", query);
				
				statement.executeUpdate(query);
			} catch (Exception e) {
				logger.error("Exception.", e);
				throw e;
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
			repo.deleteByReleaseNameAndUserId(releaseName, id);
		} else {
			throw new Exception("등록되지 않은 사용자 입니다. [" +id+"]");
		}
	}
}
