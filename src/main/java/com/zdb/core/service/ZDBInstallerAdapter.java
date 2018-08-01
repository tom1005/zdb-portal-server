package com.zdb.core.service;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.repository.TagRepository;
import com.zdb.core.repository.ZDBMariaDBAccountRepository;
import com.zdb.core.repository.ZDBMariaDBConfigRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepository;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class ZDBInstallerAdapter implements ZDBInstaller {

	@Autowired
	protected ZDBMariaDBAccountRepository accountRepository;

	@Autowired
	protected ZDBMariaDBConfigRepository configRepository;
	
	@Autowired
	protected ZDBReleaseRepository releaseRepository;
	
	@Autowired
	protected ZDBRepository metaRepository;

	@Autowired
	protected TagRepository tagRepository;
	
	@Autowired
	protected K8SService k8sService;

	@Autowired
	protected DiskUsageRepository diskUsageRepository;

	@Autowired
	@Qualifier("backupProvider")
	protected BackupProviderImpl backupProvider;
	
	public void saveReleaseError(String serviceName, Exception e) {
		try {
			ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(serviceName);
			if(releaseMeta != null) {
				releaseMeta.setStatus("ERROR");
				
				String stackTraceToString = getStackTraceToString(e);
				releaseMeta.setManifest(stackTraceToString);
				
				releaseRepository.save(releaseMeta);
			}
		} catch (Exception e1) {
			log.error(e.getMessage(), e);
		}
	}
	
	private String getStackTraceToString(Exception e) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream pinrtStream = new PrintStream(out);

		e.printStackTrace(pinrtStream);

		String stackTraceString = out.toString();
		
		return stackTraceString;
	}
}
