package com.zdb.core.job;

import com.zdb.core.job.Job.JobResult;

public class EventAdapter implements EventListener {

	@Override
	public void onEvent(Job job, String event) {
		
	}

	@Override
	public void done(Job job, JobResult status, Throwable e) {
		
	}

	@Override
	public void status(Job job, String status) {
		
	}

}
