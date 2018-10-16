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
	public static final String CREATE_TAG = "태그 추가";
	public static final String DELETE_TAG = "태그 삭제";
	public static final String SCALE_UP = "스케일 업";
	public static final String SCALE_OUT = "스케일 아웃";
	public static final String UPDATE_CONFIG = "환경설정 변경";
	public static final String MODIFY_PASSWORD = "비빌번호 변경";
	public static final String SET_BACKUP_SCHEDULE = "스케줄 설정";
	public static final String EXEC_BACKUP = "백업 실행";
	public static final String REQ_BACKUP = "백업 요청";
	public static final String DELETE_BACKUP_DATA = "백업 데이터 삭제";
	public static final String RESTORE_BACKUP = "백업 데이터 복원";
	public static final String CREATE_PUBLIC_SVC = "Public Network 생성";
	public static final String DELETE_PUBLIC_SVC = "Public Network 삭제";
	public static final String CREATE_ZDBCONFIG = "Global 환경설정 생성";
	
	private String operation;

	@Id
	@GeneratedValue
	private Long id;
	
	@Column(name = "txId")
	private String txId;
	
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
