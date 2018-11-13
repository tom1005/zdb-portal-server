package com.zdb.core.job;

public interface Job extends Runnable{

	enum JobResult {
		OK, RUNNING, ERROR
	}

	public String getJobName();

	public JobParameter getJobParameter();
	
	public JobKind getKind();

	public void done(JobResult code, String message, Throwable e);

	public void run();

	public void progress(String status);

	public JobResult getStatus();

	public void setStatus(JobResult status);
}
