package com.zdb.core.job;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.zdb.core.domain.EventType;
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

	private LinkedHashMap<EventType, Map<String, JobResult>> job_event_cache = new LinkedHashMap<EventType, Map<String, JobResult>>() {
		private static final long serialVersionUID = -1L;

		protected boolean removeEldestEntry(java.util.Map.Entry<EventType, Map<String, JobResult>> eldest) {
			return size() > 100;
		};
	};
	
	public synchronized LinkedHashMap<EventType, Map<String, JobResult>> getJobEventCache() {
		return job_event_cache;
	}

	public synchronized Map<String, JobResult> getStatusByOperation(EventType operation) {
		if(getJobEventCache().containsKey(operation)) {
			return getJobEventCache().get(operation);
		} else {
			return Collections.emptyMap();
		}
	}

	public synchronized void setEventStatus(EventType operation, String key, JobResult status) {

		if(getJobEventCache().containsKey(operation )) {
			Map<String, JobResult> statusMap = getJobEventCache().get(operation);
			statusMap.put(key, status);
		} else {
			Map<String, JobResult> statusMap = new LinkedHashMap<String, JobResult>() {
				private static final long serialVersionUID = -1L;
				
				protected boolean removeEldestEntry(java.util.Map.Entry<String, JobResult> eldest) {
					return size() > 100;
				};
			};
			statusMap.put(key, status);
			getJobEventCache().put(operation, statusMap);
		}
	}
	
	public synchronized JobResult getStatus(EventType operation, String serviceKey) {

		if(getJobEventCache().containsKey(operation )) {
			Map<String, JobResult> statusMap = getJobEventCache().get(operation);
			return statusMap.get(serviceKey);
		} 
		
		return null;
	}
	
	/**
	 * @param serviceKey : CLUSTER_KIND_NAMESPACE_NAME
	 * @return
	 */
	public synchronized Map<EventType, JobResult> getStatusAll(String serviceKey) {
		Map<EventType, JobResult> statusMap = new HashMap<EventType, JobResult>();
		
		Set<EventType> keySet = getJobEventCache().keySet();
		for (EventType op : keySet) {
			Map<String, JobResult> map = getJobEventCache().get(op);
			JobResult status = map.get(serviceKey);
			if(status != null) {
				statusMap.put(op, status);
			}
		}
		
		return statusMap;
	}

	public synchronized void removeEventCache(EventType operation, String serviceKey) {
		if (getJobEventCache().containsKey(operation)) {
			Map<String, JobResult> map = getJobEventCache().get(operation);
			if (map.containsKey(serviceKey)) {
				map.remove(serviceKey);
			}
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////
	
	private List<EventListener> listeners = new CopyOnWriteArrayList<EventListener>();

	private synchronized List<EventListener> getListeners() {
		return listeners;
	}

	public synchronized void addListener(EventListener eventListener) {
		if (getListeners().indexOf(eventListener) == -1) {
			listeners.add(eventListener);
		}
	}

	public synchronized void removeListener(EventListener eventListener) {
		if (getListeners().indexOf(eventListener) != -1) {
			listeners.remove(eventListener);
		}
	}
	
	public synchronized void onEvent(Job job, String event) {
		for (Iterator<EventListener> iterator = listeners.iterator(); iterator.hasNext();) {
			EventListener eventListener = iterator.next();
			if(eventListener.getTxId().equals(job.getTxid())) {
				eventListener.onEvent(job, event);
			}
		}
	}

	public synchronized void onDone(Job job, JobResult code, String message, Throwable e) {
		for (Iterator<EventListener> iterator = listeners.iterator(); iterator.hasNext();) {
			EventListener eventListener = iterator.next();
			if(eventListener.getTxId().equals(job.getTxid())) {
				eventListener.done(job, code, message, e);
			}
		}
	}
	
	public synchronized void progress(Job job, String status) {
		for (Iterator<EventListener> iterator = listeners.iterator(); iterator.hasNext();) {
			EventListener eventListener = iterator.next();
			if(eventListener.getTxId().equals(job.getTxid())) {
				eventListener.status(job, status);
			}
		}
	}

}
