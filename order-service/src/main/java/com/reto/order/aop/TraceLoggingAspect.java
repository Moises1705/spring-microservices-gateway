package com.reto.order.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceLoggingAspect {

    @Around("execution(* com.reto.order.service.impl..*(..))")
    public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        long start = System.nanoTime();

        log.debug("{} iniciado", method);

        try {
            Object result = joinPoint.proceed();
            log.info("{} OK ({} ms)", method, elapsedMillis(start));
            return result;
        } catch (Throwable ex) {
            log.warn("{} FALLÓ con {}: {} ({} ms)", method,
                    ex.getClass().getSimpleName(), ex.getMessage(), elapsedMillis(start));
            throw ex;
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}