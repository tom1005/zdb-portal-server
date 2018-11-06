package com.zdb.snippet;

import com.zdb.snippet.Job.JobResult;

public interface EventListener {
	public void onEvent(Job job, String event);

	public void done(Job job, JobResult status, Throwable e);
	
	public void status(Job job, String status);
}
