package com.zdb.snippet;

import com.zdb.snippet.JobHandler.JobKind;

public interface Job {

	enum JobResult {
		OK, RUNNING, ERROR
	}

	public String getJobName();

	public JobKind getKind();

	public void done(JobResult code, Object obj, Throwable e);

	public void run();

	public void progress(String status);

	public JobResult getStatus();

	public void setStatus(JobResult status);
}
