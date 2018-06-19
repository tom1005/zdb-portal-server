package com.zdb.core.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.google.gson.Gson;
import com.zdb.core.domain.Exchange;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.repository.ZDBRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * @author 06919
 *
 */
@Slf4j
public abstract class ServiceEventAdapter implements Runnable, JobEventListener {

	protected String serviceName;
	
	protected String namespace;
	
	protected String txId;

	protected long startTime;
	
	protected ZDBRepository metaRepository;
	
	protected Exchange exchange;

	// 3분 (3분간 상태 모니터링)
	protected int expireTime = 1000 * 60 * 3;

	protected List<JobEventListener> eventListers = Collections.synchronizedList(new ArrayList<JobEventListener>());

	public ServiceEventAdapter(Exchange exchange) {
		ZDBEntity service = exchange.getProperty(Exchange.ZDBENTITY, ZDBEntity.class);
		
		this.metaRepository = exchange.getProperty(Exchange.META_REPOSITORY, ZDBRepository.class);
		this.txId = exchange.getProperty(Exchange.TXID, String.class);;
		this.serviceName = service.getServiceName();
		this.namespace = service.getNamespace();
		this.exchange = exchange;
	}
	
	public ServiceEventAdapter(ZDBRepository repo, String txId, String namespace, String servcieName) {
		this.metaRepository = repo;
		this.txId = txId;
		this.serviceName = servcieName;
		this.namespace = namespace;
	}

	/**
	 * @param listener
	 */
	public void addListener(JobEventListener listener) {

		if (!eventListers.contains(listener)) {
			eventListers.add(listener);
		}

	}

	@Override
	public abstract void run();

	public void done(String message) {
		if (!eventListers.isEmpty()) {

			for (JobEventListener listener : eventListers) {
				if (log.isDebugEnabled()) {
					log.debug(message);
				}
				listener.done(message);
			}
		}

		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setNamespace(namespace);
		event.setServiceName(serviceName);
		event.setResultMessage(message);
		event.setStatusMessage("done");
		event.setStatus(IResult.OK);
		event.setEndTIme(new Date(System.currentTimeMillis()));
		
		log.info("!!!done " + new Gson().toJson(event));
		
		ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
	}

	public void running(String message) {
		if (!eventListers.isEmpty()) {
			for (JobEventListener listener : eventListers) {
				if (log.isDebugEnabled()) {
					log.debug(message);
				}
				listener.running(message);
			}
		}
		
		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setNamespace(namespace);
		event.setServiceName(serviceName);
		event.setStatus(IResult.RUNNING);
		event.setUpdateTime(new Date(System.currentTimeMillis()));
		event.setResultMessage(message);
		event.setStatusMessage("처리중...");
		log.info("!!!running " + new Gson().toJson(event));
		ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
	}

	
	public void failure(Throwable t, String message) {
		if (!eventListers.isEmpty()) {
			for (JobEventListener listener : eventListers) {
				log.error(message, t);
				listener.failure(t, message);
				
			}
		}
		
		RequestEvent event = new RequestEvent();
		event.setTxId(txId);
		event.setNamespace(namespace);
		event.setServiceName(serviceName);
		event.setStatus(IResult.ERROR);
		event.setStatusMessage("failure");
		event.setResultMessage(message);
		event.setUpdateTime(new Date(System.currentTimeMillis()));
		event.setEndTIme(new Date(System.currentTimeMillis()));
		log.error("!!!failure " + new Gson().toJson(event));
		ZDBRepositoryUtil.saveRequestEvent(metaRepository, event);
	}
}