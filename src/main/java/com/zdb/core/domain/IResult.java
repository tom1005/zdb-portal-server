package com.zdb.core.domain;

import org.springframework.http.HttpStatus;

public interface IResult {
	
	public static final int OK = 0x00;
	
	public static final int INIT = 0x01;
	
	public static final int RUNNING = 0x02;

	public static final int WARNING = 0x03;
	
	public static final int ERROR = 0x04;
	
	public static final int DONE = 0x05;
	
	public static final int UNAUTHORIZED = 401;
	
	public static final String PERSISTENTVOLUMECLAIM = "persistentvolumeclaim";
	
	public static final String PERSISTENTVOLUMECLAIMS = "persistentvolumeclaims";

	public static final String UNUSED_PERSISTENTVOLUMECLAIMS = "unusedPersistentvolumeclaims";
	
	public static final String SERVICEOVERVIEW = "serviceoverview";
	
	public static final String SERVICEOVERVIEWS = "serviceoverviews";
	
	public static final String NAMESPACE = "namespace";
	
	public static final String NAMESPACES = "namespaces";

	public static final String NAMESPACE_RESOURCE = "namespaceResource";
	
	public static final String PODS = "pods";
	
	public static final String POD = "POD";
	
	public static final String SERVICE_EVENTS = "serviceEvents";
	
	public static final String PODS_RESOURCE = "podsResource";
	
	public static final String METRICS_CPU_USAGE = "metricsCpuUsage";
	
	public static final String METRICS_MEM_USAGE = "metricsMemoryUsage";
	
	public static final String SECRET = "secret";
	
	public static final String SECRETS = "secrets";
	
	public static final String SERVICEEXPOSE = "serviceexpose";
	
	public static final String SERVICEEXPOSES = "serviceexposes";
	
	public static final String CONFIGMAP = "configmap";
	
	public static final String CONFIGMAPS = "configmaps";
	
	public static final String STATEFULSET = "StatefulSet";
	
	public static final String STATEFULSETS = "StatefulSets";
	
	
	public static final String EXCEPTION = "error";
	
	public static final String DELETE = "delete";
	
	public static final String DEPLOYMENT = "deployment";
	
	public static final String DEPLOYMENTS = "deployments";
	
	public static final String ACCOUNT = "account";
	
	public static final String ACCOUNTS = "accounts";
	
	public static final String MARIADB_CONFIG = "mariaDBConfig";

	public static final String REDIS_CONFIG = "redisConfig";
	
	public static final String UPDATE = "update";
	
	public static final String CONFIG_UPDATE = "configUpdate";

	public static final String POD_LOG = "podLog";

	public static final String SLOW_LOG = "slowLog";

	public static final String MY_CNF = "mycnf";
	
	public static final String USER_GRANTS = "userGrants";
	
	public static final String DATABASES = "databases";

	public static final String CONNECTION_INFO = "connectionInfo";

	public static final String SERVICE_STATUS = "serviceStatus";

	public static final String CHANGE_PASSWORD = "changePassword";
	
	public static final String CREATE_TAG = "createTag";
	public static final String DELETE_TAG = "deleteTag";
	public static final String TAGS = "tags";
	public static final String TAG = "tag";

	public static final String NODES = "nodes";
	public static final String NODE_LIST = "nodeList";
	
	public static final String BACKUP_SCHEDULE = "BackupSchedule";
	public static final String BACKUP_DETAIL = "BackupDetail";
	public static final String SCHEDULE = "schedule";
	public static final String BACKUP_LIST = "BackupList";
	public static final String SCHEDULE_INFO_LIST = "scheduleInfoList";


	
	public static final String OPERATION_EVENTS = "operationEvents";
	
	public static final String CREATE_PUBLIC_SVC = "createPublicService";

	public static final String ZDBConfig = "zdbConfig";
	
	public static final String WORKER_POOLS = "workerPools";

	public static final String FILE_LOG = "fileLog";

	public static final String ALERT_RULES = "alertRules";

	public static final String ALERT_RULE = "alertRule";

	public static final String PROCESSES = "processes";

	public static final String STORAGES = "storages";

	public static final String STORAGES_DATA = "storagesData";

	public static final String DATABASE_CONNECTION = "databaseConnection";

	public static final String DATABASE_STATUS = "databaseStatus";
	
	public static final String DATABASE_STATUS_VARIABLES = "databaseStatusVariables";

	public static final String DATABASE_SYSTEM_VARIABLES = "databaseSystemVariables";
	
	public int getCode();
	
	public Throwable getException();
	
	public String getTxId();

	public String getMessage();
	
	public boolean isOK();
	
	public Result putValue(String key, Object value);
	
	public String toJson(boolean isPretty);
	
	public String toJson();

	public boolean isUnauthorized();

	public HttpStatus status();
	
}
