package com.zdb.core.job;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.zdb.core.job.Job.JobResult;

public class JobHandler {

	static JobHandler instance;

	private JobHandler() {
	}

	public static JobHandler getInstance() {
		if (instance == null) {
			instance = new JobHandler();
		}
		return instance;
	}

	private static List<EventListener> listeners = new CopyOnWriteArrayList<EventListener>();

	private static synchronized List<EventListener> getListeners() {
		return listeners;
	}

	public static synchronized void addListener(EventListener eventListener) {
		if (getListeners().indexOf(eventListener) == -1) {
			listeners.add(eventListener);
		}
	}

	public static synchronized void removeListener(EventListener eventListener) {
		if (getListeners().indexOf(eventListener) != -1) {
			listeners.remove(eventListener);
		}
	}
	
	public synchronized void onEvent(Job job, String event) {
		for (Iterator<EventListener> iterator = listeners.iterator(); iterator.hasNext();) {
			EventListener eventListener = iterator.next();
			eventListener.onEvent(job, event);
		}
	}

	public synchronized void onDone(Job job, JobResult code, Throwable e) {
		for (Iterator<EventListener> iterator = listeners.iterator(); iterator.hasNext();) {
			EventListener eventListener = iterator.next();
			eventListener.done(job, code, e);
		}
	}
	
	public synchronized void progress(Job job, String status) {
		for (Iterator<EventListener> iterator = listeners.iterator(); iterator.hasNext();) {
			EventListener eventListener = iterator.next();
			eventListener.status(job, status);
		}
	}

}
