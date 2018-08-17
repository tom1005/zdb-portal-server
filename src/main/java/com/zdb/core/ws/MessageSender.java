package com.zdb.core.ws;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.service.ZDBRestService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MessageSender {
	@Autowired
	private SimpMessagingTemplate messageSender;
	
	@Autowired
	@Qualifier("commonService")
	private ZDBRestService commonService;

	@EventListener
	public void handleEvent(Object event) {}
	
	@Scheduled(initialDelayString = "30000", fixedRateString = "10000")
	public void pushData() {
		if((System.currentTimeMillis() - lastUpdate) < (9 * 1000) ) {
			return;
		}
		
		sendToClient("auto");
	}
	
	static long lastUpdate = 0;
	
	/**
	 * websocket send
	 */
	public synchronized void sendToClient(String eventType) {
		try {
			if (getSessionCount() > 0) {
				Thread.sleep(500);
				Result result = commonService.getServicesWithNamespaces(null, true);
				if (result.isOK()) {
					Object object = result.getResult().get(IResult.SERVICEOVERVIEWS);
					if (object != null) {
						messageSender.convertAndSend("/services", result);

						List<ServiceOverview> overviews = (List<ServiceOverview>) object;
						for (ServiceOverview serviceOverview : overviews) {
							Result r = result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
							messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
						}
						
						// 최근 업데이트 시간 
						lastUpdate = System.currentTimeMillis();
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} 
	}
	
	Set<String> mySet = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	
	public int getSessionCount() {
		return mySet.size();
	}

	@EventListener
	private void onSessionConnectedEvent(SessionConnectedEvent event) {
	    StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
	    mySet.add(sha.getSessionId());
	}

	@EventListener
	private void onSessionDisconnectEvent(SessionDisconnectEvent event) {
	    StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
	    mySet.remove(sha.getSessionId());
	}
}