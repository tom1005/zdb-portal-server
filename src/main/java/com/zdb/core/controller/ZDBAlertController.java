package com.zdb.core.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdb.core.service.AlertService;

import lombok.extern.slf4j.Slf4j;

@RefreshScope
@RestController
@Slf4j
@RequestMapping(value = "/api/v1")
public class ZDBAlertController {

	@Autowired
	private HttpServletRequest request;
	
	@Autowired
	@Qualifier("alertService")
	private AlertService alertService;
}