package com.zdb.core.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.Result;
import com.zdb.core.repository.UserNamespaceRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.service.AdminService;

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
	protected ZDBReleaseRepository releaseRepository;
	
	@RequestMapping(value = "/mariadb/cm/backup", method = RequestMethod.PUT)
	public ResponseEntity<String> mycnfBackup(final UriComponentsBuilder ucBuilder) {
		RequestEvent event = new RequestEvent();
		try {
			String mycnfBackup = adminService.mycnfBackup();
			
			return new ResponseEntity<String>(mycnfBackup + " configmap 이 저장 되었습니다.", HttpStatus.OK);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			Result result = new Result("", IResult.ERROR, RequestEvent.SERVICE_TAKE_OVER+" 오류!").putValue(IResult.EXCEPTION, e);
			
			event.setStatus(result.getCode());
			event.setResultMessage(result.getMessage());
			
			return new ResponseEntity<String>(result.toJson(), HttpStatus.EXPECTATION_FAILED);
		} finally {
		}	
	}
	

}
