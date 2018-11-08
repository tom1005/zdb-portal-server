package com.zdb.core.job;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class JobAdapter implements Job {

	protected JobParameter param;
	
	protected JobResult status;

	public JobAdapter(JobParameter param) {
		this.param = param;
	}

	public void done(JobResult result, Object message, Throwable e) {
		setStatus(result);
		
		if (result == JobResult.OK) {
			log.info(getJobName() + " success. {}", "- "+message);
		} else if (result == JobResult.RUNNING) {
			log.info(getJobName() + " running.");
		} else if (result == JobResult.ERROR) {
			log.error(getJobName() + " error.", e);
		}
		
		JobHandler.getInstance().onDone(this, result, e);
	}

	public void progress(String status) {
		log.info(this.getJobName() + " : " +status);
		JobHandler.getInstance().progress(this, status);
	}

	@Override
	public JobResult getStatus() {
		return status;
	}

	@Override
	public void setStatus(JobResult status) {
		this.status = status;
	}
}
