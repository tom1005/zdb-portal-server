package com.zdb.core.domain;

public enum RequestEventCode {
	
	SERVICE_CREATE("SERVICE", "서비스 생성"), 
	PVC_CREATE("PVC", "스토리지 생성"), 
	SERVICE_UPDATE("SERVICE", "업데이트"), 
	SERVICE_DELETE("SERVICE", "서비스 삭제"), 
	SERVICE_READ("SERVICE", "READ"), 
	SERVICE_RESTART("SERVICE", "서비스 재시작"), 
	SERVICE_POD_RESTART("SERVICE", "Pod 재시작"), 
	SERVICE_ON("SERVICE", "서비스 On"), 
	SERVICE_OFF("SERVICE", "서비스 Off"), 
	CHANGEOVER_MASTER_TO_SLAVE("CHANGEOVER", "서비스 전환(Master To Slave)"), 
	CHANGEOVER_SLAVE_TO_MASTER("CHANGEOVER", "서비스 전환(Slave To Master)"), 
	CHANGEOVER_STATUS("CHANGEOVER", "서비스 전환 상태"), 
	CHANGEOVER_FAILBACK("CHANGEOVER", "서비스 전환(Fail-Back : Slave To Master)"), 
	PROCESS_SELECT("PROCESS", "Pocess 조회"), 
	PROCESS_KILL("PROCESS", "Process 종료"), 
	DATABASE_STATUS("DATABASE", "DB Status 조회"), 
	DATABASE_CONNECTION("DATABASE", "DB Connection 조회"), 
	DATABASE_STATUS_VARIABLES("DATABASE", "DB Status Variables 조회"), 
	DATABASE_SYSTEM_VARIABLES("DATABASE", "DB System Variables 조회"), 
	DATABASE_VARIABLES("DATABASE", "DB Variables 조회"), 
	DATABASE_SCHEMAS("DATABASE", "DB Schema 조회"), 
	DATABASE_UPDATE_VARIABLES("DATABASE", "DB Variables 수정"), 
	NETWORK_PUBLIC_CREATE("NETWORK", "Public Network 생성"), 
	NETWORK_PUBLIC_DELETE("NETWORK", "Public Network 삭제"), 
	NETWORK_CHANGE_PORT("NETWORK", "포트 변경"), 
	AUTOFAILOVER_SET("AUTOFAILOVER", "Auto Failover 설정"), 
	AUTOFAILOVER_ADD("AUTOFAILOVER", "Auto Failover 환경 등록"), 
	BACKUPDISK_ADD("BACKUPDISK", "백업 디스크 추가"), 
	BACKUPDISK_REMOVE("BACKUPDISK", "백업 디스크 제거"), 
	BACKUP_EXEC("BACKUP", "백업 실행"), 
	BACKUP_REQ("BACKUP", "백업 요청"), 
	BACKUP_FULLREQ("BACKUP", "전체 백업 요청"), 
	BACKUP_INCRREQ("BACKUP", "증분 백업 요청"), 
	BACKUP_DELETE("BACKUP", "백업 데이터 삭제"), 
	BACKUP_ABRORT("BACKUP", "백업 수행중지"), 
	BACKUPSCHEDULE_SET("BACKUPSCHEDULE", "스케줄 설정"), 
	RESTORE_BACKUP("RESTORE", "백업 데이터 복원"), 
	RESTORE_MIGRATION("RESTORE", "백업 데이터 기반 마이그레이션"), 
	RESTORE_SLAVE("RESTORE", "Slave DB 복원"), 
	USER_CREATE("USER", "사용자 생성"), 
	USER_UPDATE("USER", "사용자 업데이트"), 
	USER_DELETE("USER", "사용자 삭제"), 
	USER_DELETE_PRIVILEGES("USER", "사용자 권한 삭제"), 
	USER_PRIVILEGES("USER", "DB Privileges 조회"), 
	DATABASE_CREATE("DATABASE", "데이터베이스 생성"), 
	DATABASE_DELETE("DATABASE", "데이터베이스 삭제"), 
	TAG_CREATE("TAG", "태그 추가"), 
	TAG_DELETE("TAG", "태그 삭제"), 
	SCALE_UP("SCALE", "스케일 업"), 
	SCALE_STORAGE_UP("SCALE", "스토리지 스케일 업"), 
	SCALE_OUT("SCALE", "스케일 아웃"), 
	WORKERPOOL_READ("WORKERPOOL", "Node Worker Pools 조회"), 
	WORKERPOOL_UPDATE("WORKERPOOL", "Node Worker Pool 변경"), 
	WORKERPOOL_ZDBUPDATE("WORKERPOOL", "ZDB Worker Pool 변경"), 
	LOG_SLOWLOG_ROTATION("LOG", "Slowlog Rotation"), 
	GLOBALCONFIG_CREATE("GLOBALCONFIG", "Global 환경설정 생성"), 
	CONFIG_UPDATE("CONFIG", "환경설정 변경"), 
	CREADENTIAL_UPDATE("CREADENTIAL", "비밀번호 변경"),
	ALERTRULE_CREATE("ALERTRULE", "Alert Rule 생성"),
	ALERTRULE_UPDATE("ALERTRULE", "Alert Rule 삭제");
	
	private String type;
	private String desc;
	
	RequestEventCode(String type, String desc) {
		this.type = type;
		this.desc = desc;
	}

	public String getType() {
		return type;
	}
	
	public String getDesc() {
		return desc;
	}
	
	public boolean isEquals(String type, String desc) {
		return getType().equals(type) && getDesc().equals(desc);
	}
}
