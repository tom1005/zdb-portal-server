package com.zdb.core.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service("mariadbBackupService")
@Slf4j
@Configuration
public class MariaDBBackupServiceImpl extends AbstractBackupServiceImpl {

}
