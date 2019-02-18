package com.zdb.core.controller;

import java.util.Date;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.service.AdminService;
import com.zdb.core.service.ZDBRestService;

import lombok.extern.slf4j.Slf4j;

/**
 * RestServiceController class
 * 
 * @author 06919
 *
 */
@RefreshScope
@RestController
@Slf4j
@RequestMapping(value = "/api/v1")
public class ZDBAdminController {

	@Autowired
	private HttpServletRequest request;
	
	@Autowired
	protected AdminService adminService;

	@Autowired
	@Qualifier("commonService")
	private ZDBRestService commonService;
	
	@Autowired
	private ZDBRepository metaRepository;
	
	private String txId() {
		return UUID.randomUUID().toString();
	}
	
	@RequestMapping(value = "/mariadb/cm/backup", method = RequestMethod.PUT)
	public ResponseEntity<String> mycnfBackup(final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		try {
			String mycnfBackup = adminService.mycnfBackup();
			
			return new ResponseEntity<String>(mycnfBackup + " configmap 이 저장 되었습니다.", HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result("", IResult.ERROR, "my.cnf Backup 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
		}	
	}
	
	/**
	 * Auto Failover 
	 *  - On : add label : zdb-failover-enable=true
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=true" --overwrite
	 *  - Off : update label : zdb-failover-enable=false
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=false" --overwrite
	 *        
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @param enable
	 * @param ucBuilder
	 * @return
	 */
	@RequestMapping(value = "/failover/{namespace}/{serviceType}/{serviceName}/{enable}", method = RequestMethod.PUT)
	public ResponseEntity<String> updateAutoFailoverEnable(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceType") final String serviceType, 
			@PathVariable("serviceName") final String serviceName,
			@PathVariable("enable") final Boolean enable,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			event.setTxId(txId);
			event.setServiceName(serviceName);
			event.setServiceType(ZDBType.MariaDB.getName());
			event.setNamespace(namespace);
			event.setStartTime(new Date(System.currentTimeMillis()));
			event.setOperation(RequestEvent.SET_AUTO_FAILOVER_USABLE);
			
			Result result = commonService.updateAutoFailoverEnable(txId, namespace, serviceType, serviceName, enable);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result("", IResult.ERROR, "my.cnf Backup 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
			ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
		}	
	}
	
	/**
	 * Statefulset 의 label : zdb-failover-enable=true 가 등록된 서비스 목록.(master)
	 * 
	 * @param txId
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/failover/{namespace}/services", method = RequestMethod.GET)
	public ResponseEntity<String> getAutoFailoverServices(
			@PathVariable("namespace") final String namespace,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			Result result = commonService.getAutoFailoverServices(txId, namespace);
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result("", IResult.ERROR, "조회 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
		}	
	}	
	
	/**
	 * Statefulset 의 label : zdb-failover-enable=true 가 등록된 서비스 목록.(master)
	 * 
	 * @param txId
	 * @param namespace
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/failover/{namespace}/{serviceName}/service", method = RequestMethod.GET)
	public ResponseEntity<String> getAutoFailoverService(
			@PathVariable("namespace") final String namespace,
			@PathVariable("serviceName") final String serviceName,
			final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		String txId = txId();
		
		try {
			Result result = commonService.getAutoFailoverService(txId, namespace, serviceName);
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result(txId, IResult.ERROR, "조회 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
		}	
	}	
}
