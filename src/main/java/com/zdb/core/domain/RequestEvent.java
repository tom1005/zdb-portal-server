package com.zdb.core.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RequestEvent {
	/*
	public static final String CREATE = "서비스 생성";
	public static final String CREATE_PVC = "스토리지 생성";
	public static final String UPDATE = "업데이트";
	public static final String DELETE = "서비스 삭제";
	public static final String READ = "READ";
	public static final String RESTART = "서비스 재시작";
	public static final String POD_RESTART = "Pod 재시작";
	public static final String RESTORE = "데이터 복원";
	public static final String CREATE_DB_USER = "사용자 생성";
	public static final String UPDATE_DB_USER = "사용자 업데이트";
	public static final String DELETE_DB_USER = "사용자 삭제";
	public static final String DELETE_USER_PRIVILEGES = "사용자 권한 삭제";
	public static final String CREATE_DATABASE = "데이터베이스 생성";
	public static final String DELETE_DATABASE = "데이터베이스 삭제";
	public static final String CREATE_TAG = "태그 추가";
	public static final String DELETE_TAG = "태그 삭제";
	public static final String SCALE_UP = "스케일 업";
	public static final String STORAGE_SCALE_UP = "스토리지 스케일 업";
	public static final String SERVICE_ON = "서비스 On";
	public static final String SERVICE_OFF = "서비스 Off";
	public static final String SERVICE_MASTER_TO_SLAVE = "서비스 전환(Master To Slave)";
	public static final String SERVICE_SLAVE_TO_MASTER = "서비스 전환(Slave To Master)";
	public static final String SERVICE_FAIL_OVER_STATUS = "서비스 전환 상태";
	public static final String SLOWLOG_ROTATION = "Slowlog Rotation";
	public static final String SCALE_OUT = "스케일 아웃";
	public static final String UPDATE_CONFIG = "환경설정 변경";
	public static final String MODIFY_PASSWORD = "비밀번호 변경";
	public static final String SET_BACKUP_SCHEDULE = "스케줄 설정";
	public static final String EXEC_BACKUP = "백업 실행";
	public static final String REQ_BACKUP = "백업 요청";
	public static final String REQ_FULL_BACKUP = "전체 백업 요청";
	public static final String REQ_INCR_BACKUP = "증분 백업 요청";
	public static final String DELETE_BACKUP_DATA = "백업 데이터 삭제";
	public static final String RESTORE_BACKUP = "백업 데이터 복원";
	public static final String MIGRATION_BACKUP = "백업 데이터 기반 마이그레이션";
	public static final String ABRORT_BACKUP = "백업 수행중지";
	public static final String RESTORE_SLAVE = "Slave DB 복원";
	public static final String CREATE_PUBLIC_SVC = "Public Network 생성";
	public static final String DELETE_PUBLIC_SVC = "Public Network 삭제";
	public static final String CREATE_ZDBCONFIG = "Global 환경설정 생성";
	public static final String SERVICE_RESTORE_SLAVE = "";
	public static final String SET_AUTO_FAILOVER_USABLE = "Auto Failover 설정";
	public static final String ADD_AUTO_FAILOVER = "Auto Failover 환경 등록";
	public static final String CHANGE_PORT = "포트 변경";
	public static final String WORKER_POOLS_READ = "Node Worker Pools 조회";
	public static final String PUT_NODE_WORKER_POOL = "Node Worker Pool 변경";
	public static final String PUT_ZDB_WORKER_POOL = "ZDB Worker Pool 변경";
	public static final String FAILBACK = "서비스 전환(Fail-Back : Slave To Master)";
	public static final String ADDBACKUPDIK = "백업 디스크 추가";
	public static final String REMOVEBACKUPDIK = "백업 디스크 제거";
	public static final String CREATE_ALERT_RULE = "알람 규칙 생성";
	public static final String DELETE_ALERT_RULE = "알람 규칙 삭제";
	public static final String UPDATE_ALERT_RULE = "알람 규칙 수정";	
	public static final String SELECT_PROCESS = "Pocess 조회";
	public static final String KILL_PROCESS = "Process 종료";
	public static final String SELECT_DATABASE_STATUS = "DB Status 조회";
	public static final String SELECT_DATABASE_CONNECTION = "DB Connection 조회";
	public static final String SELECT_DATABASE_STATUS_VARIABLES = "DB Status Variables 조회";
	public static final String SELECT_DATABASE_SYSTEM_VARIABLES = "DB System Variables 조회";
	public static final String SELECT_DATABASE_VARIABLES = "DB Variables 조회";
	public static final String SELECT_DATABASE_SCHEMAS = "DB Schema 조회";
	public static final String SELECT_USER_PRIVILEGES = "DB Privileges 조회";
	public static final String UPDATE_DATABASE_VARIABLES = "DB Variables 수정";
*/	
	private String operation;

	@Id
	@GeneratedValue
	private Long id;
	
	@Column(name = "txId")
	private String txId;
	
	@Column(name = "type")
	private String type;
	
	private String namespace;

	@Column(name = "serviceName")
	private String serviceName;
	
	@Column(name = "serviceType")
	private String serviceType;
	
	@Column(name = "userId")
	private String userId;
	
	@Column(name = "startTime")
	private Date startTime;
	
	@Column(name = "endTime")
	private Date endTime;
	
	/**
	 * IResult.OK, IResult.RUNNING, IResult.WARNING, IResult.ERROR
	 */
	private int status = IResult.INIT;
	
	@Lob
	@Column(length=1000000, name = "resultMessage")
	private String resultMessage;
	
	@Column(length=1000000, name = "history")
	private String history;
}
