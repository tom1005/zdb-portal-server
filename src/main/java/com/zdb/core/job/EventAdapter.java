package com.zdb.core.job;

import com.zdb.core.job.Job.JobResult;

public class EventAdapter implements EventListener {
	
	String txId;
	
	public EventAdapter(String txId) {
		this.txId = txId;
	}

	@Override
	public void onEvent(Job job, String event) {
		
	}

	@Override
	public void done(Job job, JobResult status, String message, Throwable e) {
		
	}

	@Override
	public void status(Job job, String status) {
		
	}

	@Override
	public String getTxId() {
		return txId;
	}

}
