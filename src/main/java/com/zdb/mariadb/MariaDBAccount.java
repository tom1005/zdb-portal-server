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
import com.zdb.core.domain.Database;
import com.zdb.core.domain.MariadbUserPrivileges;
import com.zdb.core.domain.MariadbUserPrivileges.MariadbPrivileges;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.UserPrivileges;
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
			sb.append("UPDATE mysql.user SET super_priv='N' WHERE user <> 'root' and user <> 'replicator' and user <> 'admin';");
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
	public static String createAccount(final String namespace, final String releaseName, final DBUser account) throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		int re = 1;//createUser,1:성공 0:실패
		String user = String.format("'%s'@'%s'",account.getUser(),account.getHost());
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			if( connection != null) {
				statement = connection.getStatement();
				
				try {
					query = String.format("CREATE USER %s identified by '%s'",user,account.getPassword());
					logger.info("query: {}", query);
					statement.executeUpdate(query);
					resultMessage.append(String.format("[%s] 유저 생성 ", user));
				}catch (Exception e) {
					resultMessage.append(String.format("[%s] 유저 생성 실패 : %s",user,e.getMessage()));
					re = 0;
				}
				if(re == 1) {
					try {
						List<String> privilegeTypes = getPrivilegeList(account);
						if (!privilegeTypes.isEmpty()) {
							query = String.format("GRANT %s ON *.* TO %s IDENTIFIED BY '%s'", String.join(",",privilegeTypes),user,account.getPassword());
							logger.info("query: {}", query);
							statement.executeUpdate(query);
							resultMessage.append(String.format(" , [%s] 유저 권한 생성 : [%s]", user,String.join(",",privilegeTypes)));
						}
					} catch (Exception e) {
						resultMessage.append(String.format(" , [%s] 권한 생성실패 : %s",user,e.getMessage()));
					}					
				}
			} else {
				throw new Exception("cannot create connection.");
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format(" [%s] 유저 생성 오류 :%s ",user,e.getMessage()));
		} finally {
			if(statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();
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
	public static String updateAccount(final String namespace, final String releaseName,DBUser account) throws Exception  {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		int re = 1;//createUser,1:성공 0:실패
		String user = String.format("'%s'@'%s'",account.getUser(),account.getHost());
		
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			statement = connection.getStatement();
			
			try {
				query = String.format("REVOKE ALL PRIVILEGES ON *.* FROM %s", user);
				statement.executeUpdate(query);
				query = String.format("REVOKE GRANT OPTION ON *.* FROM %s", user);
				statement.executeUpdate(query);
				List<String> privilegeTypes = getPrivilegeList(account);
				
				if (!privilegeTypes.isEmpty()) {
					query = String.format("GRANT %s ON *.* TO %s ", String.join(",",privilegeTypes),user);
					logger.info("query: {}", query);
					statement.executeUpdate(query);
				}
				resultMessage.append(String.format("[%s] 유저 권한 변경: [%s]",user,String.join(",",privilegeTypes)));
			} catch (Exception e) {
				resultMessage.append(String.format(" [%s] 유저 권한 변경 실패: %s",user,e.getMessage()));
				re = 0;
			}
			
			if (!StringUtils.isEmpty(account.getPassword()) && re ==1) {
				try {
					query = String.format(" SET PASSWORD FOR %s = PASSWORD('%s');",user,account.getPassword());
					logger.debug("query: {}", query);
					statement.executeUpdate(query);
					resultMessage.append(String.format(" [%s] 유저 비밀번호 변경",user));
				} catch (Exception e) {
					resultMessage.append(String.format(" [%s] 유저 비밀번호 변경 실패 :%s ",user,e.getMessage()));
				}
			}
			
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format(" [%s] 유저 수정 오류 :%s ",user,e.getMessage()));
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();
	}
	public static String deleteAccount(final String namespace,final String releaseName,DBUser account)throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		String user = String.format("'%s'@'%s'",account.getUser(),account.getHost());
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, releaseName);
			statement = connection.getStatement();
			
			List<String> blackList = Arrays.asList("admin","root");
			if(blackList.indexOf(account.getUser()) == -1) {
				query = String.format("DROP USER %s;", user);
				statement.executeQuery(query);
				resultMessage.append(String.format("[%s] 유저 삭제", user));
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format("[%s] 유저 삭제 실패", user,e.getMessage()));
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();		
	}
	private static List<String> getPrivilegeList(DBUser account) {
		List<String> privilegeList = new ArrayList<>();
		String [] grantCols = {"select","insert","update","delete","execute","showView","create","alter","references","index","createView"
				,"createRoutine","alterRoutine","event","drop","trigger","grant","createTmpTable","lockTables"};
		
		Class cls = account.getClass();
		for(String col : grantCols) {
			try {
				Method m = cls.getMethod("get"+col.substring(0,1).toUpperCase()+col.substring(1));
				String yn = (String)m.invoke(account);
				if(yn.equals("Y")) {
					if(col.equals("grant")) {
						privilegeList.add("GRANT OPTION");	
					}else if(col.equals("createTmpTable")) {
						privilegeList.add("CREATE TEMPORARY TABLES");
					} else {
						privilegeList.add(col.replaceAll("([A-Z]+)", " $1").toUpperCase());
					}
				}
			} catch (Exception e) {
			}
		}
		return privilegeList;
	}
	private static List<String> getPrivilegeList(MariadbPrivileges privilege) {
		List<String> privilegeList = new ArrayList<>();
		String [] grantCols = {"select","insert","update","delete","execute","showView","create","alter","references","index","createView"
				,"createRoutine","alterRoutine","event","drop","trigger","grantOption","createTemporaryTables","lockTables"};
		
		Class cls = privilege.getClass();
		for(String col : grantCols) {
			try {
				Method m = cls.getMethod("get"+col.substring(0,1).toUpperCase()+col.substring(1));
				String yn = (String)m.invoke(privilege);
				if(yn.equals("Y")) {
					privilegeList.add(col.replaceAll("([A-Z]+)", " $1").toUpperCase());
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
			q.append("	Show_view_priv as 'SHOW_VIEW', ");
			q.append("	Create_priv as 'CREATE', ");
			q.append("	Alter_priv as 'ALTER', ");
			q.append("	References_priv as 'REFERENCES', ");
			q.append("	Index_priv as 'INDEX', ");
			q.append("	Create_view_priv as 'CREATE_VIEW', ");
			q.append("	Create_routine_priv as 'CREATE_ROUTINE', ");
			q.append("	Alter_routine_priv as 'ALTER_ROUTINE', ");
			q.append("	Event_priv as 'EVENT', ");
			q.append("	Drop_priv as 'DROP', ");
			q.append("	Trigger_priv as 'TRIGGER', ");
			q.append("	Grant_priv as 'GRANT', ");
			q.append("	Create_tmp_table_priv as 'CREATE_TMP_TABLE', ");
			q.append("	Lock_tables_priv as 'LOCK_TABLES', ");
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
				DBUser dbUser = new DBUser();
				dbUser.setUser(rs.getString("USER"));
				dbUser.setHost(rs.getString("HOST"));
				dbUser.setSelect(rs.getString("SELECT"));
				dbUser.setInsert(rs.getString("INSERT"));
				dbUser.setUpdate(rs.getString("UPDATE"));
				dbUser.setDelete(rs.getString("DELETE"));
				dbUser.setExecute(rs.getString("EXECUTE"));
				dbUser.setShowView(rs.getString("SHOW_VIEW"));
				dbUser.setCreate(rs.getString("CREATE"));
				dbUser.setAlter(rs.getString("ALTER"));
				dbUser.setReferences(rs.getString("REFERENCES"));
				dbUser.setIndex(rs.getString("INDEX"));
				dbUser.setCreateView(rs.getString("CREATE_VIEW"));
				dbUser.setCreateRoutine(rs.getString("CREATE_ROUTINE"));
				dbUser.setAlterRoutine(rs.getString("ALTER_ROUTINE"));
				dbUser.setEvent(rs.getString("EVENT"));
				dbUser.setDrop(rs.getString("DROP"));
				dbUser.setTrigger(rs.getString("TRIGGER"));
				dbUser.setGrant(rs.getString("GRANT"));
				dbUser.setCreateTmpTable(rs.getString("CREATE_TMP_TABLE"));
				dbUser.setLockTables(rs.getString("LOCK_TABLES"));
				dbUser.setCreateUser(rs.getString("CREATE_USER"));
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

	public static List<Database> getDatabases(String namespace, String serviceName) throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		List<Database> databaseList = new ArrayList<>();

		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			statement = connection.getStatement();
			StringBuffer q = new StringBuffer();
			q.append("show databases ");
			
			ResultSet rs = statement.executeQuery(q.toString());
			while (rs.next()) {
				String name = rs.getString("database");
				Database database = new Database();
				database.setName(name);
				databaseList.add(database);
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			throw e;
		} finally {
			if(statement!=null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
		return databaseList;
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

	public static String createDatabase(String namespace, String serviceName, Database database)throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			if( connection != null) {
				statement = connection.getStatement();
				
				try {
					query = String.format("CREATE DATABASE %s ",database.getName());
					logger.info("query: {}", query);
					statement.executeUpdate(query);
					resultMessage.append(String.format("[%s] %s ", database.getName(),RequestEvent.CREATE_DATABASE));
				}catch (Exception e) {
					resultMessage.append(String.format("[%s] %s 실패 : %s",database.getName(),RequestEvent.CREATE_DATABASE,e.getMessage()));
				}
			} else {
				throw new Exception("cannot create connection.");
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format(" [%s] %s 오류 :%s ",database.getName(),RequestEvent.CREATE_DATABASE,e.getMessage()));
		} finally {
			if(statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();
	}

	public static String deleteDatabase(String namespace, String serviceName, Database database)throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			statement = connection.getStatement();
			query = String.format("DROP DATABASE IF EXISTS %s;", database.getName());
			statement.executeQuery(query);
			resultMessage.append(String.format("[%s] %s", database.getName(),RequestEvent.DELETE_DATABASE));
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format("[%s] %s 실패 : %s", database.getName(),RequestEvent.DELETE_DATABASE,e.getMessage()));
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();	
	}

	public static List<UserPrivileges> getUserPrivileges(String namespace, String serviceName) throws Exception {
		MariaDBConnection connection = null;
		Statement statement = null;
		List<UserPrivileges> userPrivilegesList = new ArrayList<>();
		String [] privileges = {"SELECT","INSERT","UPDATE","DELETE","EXECUTE","SHOW VIEW","CREATE","ALTER","REFERENCES","INDEX","CREATE VIEW"
				,"CREATE ROUTINE","ALTER ROUTINE","EVENT","DROP","TRIGGER","GRANT","CREATE TMP TABLE","LOCK TABLES"};
		
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			statement = connection.getStatement();
			StringBuffer q = new StringBuffer();
			q.append("SELECT GRANTEE, TABLE_CATALOG, TABLE_SCHEMA, PRIVILEGE_TYPE, IS_GRANTABLE FROM INFORMATION_SCHEMA.SCHEMA_PRIVILEGES");
			q.append(" UNION ALL ");
			q.append("SELECT GRANTEE, TABLE_CATALOG, '*',PRIVILEGE_TYPE, IS_GRANTABLE FROM INFORMATION_SCHEMA.USER_PRIVILEGES");
			
			ResultSet rs = statement.executeQuery(q.toString());
			while (rs.next()) {
				UserPrivileges u = new UserPrivileges();
				u.setGrantee(rs.getString("GRANTEE"));
				u.setTableCatalog(rs.getString("TABLE_CATALOG"));
				u.setTableSchema(rs.getString("TABLE_SCHEMA"));
				u.setPrivilegeType(rs.getString("PRIVILEGE_TYPE"));
				u.setIsGrantable(rs.getString("IS_GRANTABLE"));
				userPrivilegesList.add(u);
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			throw e;
		} finally {
			if(statement!=null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
		return userPrivilegesList;
	}
	public static String createUserPrivileges(final String namespace, final String serviceName,MariadbUserPrivileges userPrivilege) throws Exception  {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		int re = 1;//createUser,1:성공 0:실패
		String grantee = userPrivilege.getGrantee();
		List<MariadbPrivileges> privilegesList = userPrivilege.getPrivileges();
		String schema = "";
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			statement = connection.getStatement();
			
			try {
				query = String.format("CREATE USER %s identified by '%s'",grantee,userPrivilege.getPassword());
				logger.info("query: {}", query);
				statement.executeUpdate(query);
				resultMessage.append(String.format("[%s] 유저 생성 ", grantee));
			}catch (Exception e) {
				resultMessage.append(String.format("[%s] 유저 생성 실패 : %s",grantee,e.getMessage()));
				re = 0;
			}
			if(re == 1) {
				for(int i = 0 ; i < privilegesList.size();i++) {
					MariadbPrivileges priviliege = privilegesList.get(i);
					schema = priviliege.getSchema();
					List<String> privilegeTypes = getPrivilegeList(priviliege);
					
					if (!privilegeTypes.isEmpty()) {
						query = String.format("GRANT %s ON %s.* TO %s ", String.join(",",privilegeTypes),schema,grantee);
						logger.info("query: {}", query);
						statement.executeUpdate(query);
					}
					resultMessage.append(String.format("[%s][%s] 유저 권한 생성: [%s]",grantee,schema,String.join(",",privilegeTypes)));
				}				
			}
			
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format(" [%s] 유저 생성 오류 :%s ",grantee,e.getMessage()));
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();		
	}
	public static String updateUserPrivileges(final String namespace, final String serviceName,MariadbUserPrivileges userPrivilege) throws Exception  {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		int re = 1;//createUser,1:성공 0:실패
		String grantee = userPrivilege.getGrantee();
		List<MariadbPrivileges> privilegesList = userPrivilege.getPrivileges();
		String schema = "";
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			statement = connection.getStatement();
			
			try {
				for(int i = 0 ; i < privilegesList.size();i++) {
					MariadbPrivileges priviliege = privilegesList.get(i);
					schema = priviliege.getSchema();
					try {
						query = String.format("REVOKE ALL PRIVILEGES ON %s.* FROM %s",schema, grantee);
						statement.executeUpdate(query);
					
						query = String.format("REVOKE GRANT OPTION ON %s.* FROM %s",schema, grantee);
						statement.executeUpdate(query);
					} catch (Exception e) {
					}
					List<String> privilegeTypes = getPrivilegeList(priviliege);
					
					if (!privilegeTypes.isEmpty()) {
						query = String.format("GRANT %s ON %s.* TO %s ", String.join(",",privilegeTypes),schema,grantee);
						logger.info("query: {}", query);
						statement.executeUpdate(query);
					}
					resultMessage.append(String.format("[%s][%s] 유저 권한 변경: [%s]",grantee,schema,String.join(",",privilegeTypes)));
				}
			} catch (Exception e) {
				resultMessage.append(String.format(" [%s][%s] 유저 권한 변경 실패: %s",grantee,schema,e.getMessage()));
				re = 0;
			}
			
			if (!StringUtils.isEmpty(userPrivilege.getPassword()) && re ==1) {
				try {
					query = String.format(" SET PASSWORD FOR %s = PASSWORD('%s');",grantee,userPrivilege.getPassword());
					logger.debug("query: {}", query);
					statement.executeUpdate(query);
					resultMessage.append(String.format(" [%s] 유저 비밀번호 변경",grantee));
				} catch (Exception e) {
					resultMessage.append(String.format(" [%s] 유저 비밀번호 변경 실패 :%s ",grantee,e.getMessage()));
				}
			}
			
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format(" [%s] 유저 수정 오류 :%s ",grantee,e.getMessage()));
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();
	}

	public static String deleteUserPrivileges(String namespace, String serviceName, MariadbUserPrivileges userPrivilege)throws Exception  {
		MariaDBConnection connection = null;
		Statement statement = null;
		String query = null;
		StringBuffer resultMessage = new StringBuffer();
		int re = 1;//createUser,1:성공 0:실패
		String grantee = userPrivilege.getGrantee();
		String schema = "";
		try {
			connection = MariaDBConnection.getRootMariaDBConnection(namespace, serviceName);
			statement = connection.getStatement();
			try {
				List<MariadbPrivileges> privilegesList = userPrivilege.getPrivileges();
				for(int i = 0 ; i < privilegesList.size();i++) {
					MariadbPrivileges priviliege = privilegesList.get(i);
					schema = priviliege.getSchema();
					try {
						query = String.format("REVOKE ALL PRIVILEGES ON %s.* FROM %s",schema, grantee);
						statement.executeUpdate(query);
					
						query = String.format("REVOKE GRANT OPTION ON %s.* FROM %s",schema, grantee);
						statement.executeUpdate(query);
					} catch (Exception e) {
					}
					List<String> privilegeTypes = getPrivilegeList(priviliege);
					
					if (!privilegeTypes.isEmpty()) {
						query = String.format("GRANT %s ON %s.* TO %s ", String.join(",",privilegeTypes),schema,grantee);
						logger.info("query: {}", query);
						statement.executeUpdate(query);
					}
					resultMessage.append(String.format("[%s][%s] 유저 권한 변경: [%s]",grantee,schema,String.join(",",privilegeTypes)));
				}
			} catch (Exception e) {
				resultMessage.append(String.format(" [%s][%s] 유저 권한 변경 실패: %s",grantee,schema,e.getMessage()));
				re = 0;
			}
		} catch (Exception e) {
			logger.error("Exception.", e);
			resultMessage.append(String.format(" [%s] 유저 수정 오류 :%s ",grantee,e.getMessage()));
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}

		return resultMessage.toString();
	}
}
