package com.zdb.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Slf4j
@Component
public class PreformanceTraceAdvice {

	
	@Pointcut("execution(public * com.zdb.core.controller..*(..))")
	private void profileTarget() {
	}

//	@Before("execution(* com.zdb.core.controller..*(..))")
//	public void onBeforeHandler(JoinPoint joinPoint) {
//		log.info("=============== onBeforeThing");
//	}
	
	@Around("profileTarget()")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable{
        Object result;
        //타겟 메서드의 signature 정보
        String signatureString = joinPoint.getSignature().toShortString();
        log.info(signatureString + "시작");
        
        //타겟의 메서드가 호출되기 전의 시간
        long start =System.currentTimeMillis();
        try {
            //타겟의 메서드 호출
            result = joinPoint.proceed();
            return result;
        } finally {
            // 타겟의 메서드가 호출된 후의 시간
            long finish = System.currentTimeMillis();
            log.info(signatureString + "종료; 실행 시간 : " + (finish - start) + " ms");
        }
    }

}
