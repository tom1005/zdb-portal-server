package com.zdb.core.job;

import com.zdb.core.job.Job.JobResult;

public interface EventListener {
	public String getTxId();
	
	public void onEvent(Job job, String event);

	public void done(Job job, JobResult status, String message, Throwable e);
	
	public void status(Job job, String status);
}
