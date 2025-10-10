package com.spring.ai.tutorial.rag.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

/**
 * 安全配置类
 * 集中管理所有安全相关的Bean配置
 */
@Configuration
public class SecurityConfiguration {
    
    /**
     * 配置提示注入过滤器
     * 使用最高优先级确保在其他处理之前执行
     */
    @Bean
    @Primary
    @Order(1)
    public PromptInjectionFilter promptInjectionFilter() {
        return new PromptInjectionFilter();
    }
    
    /**
     * 配置响应安全监控器
     * 用于检测AI响应中的异常模式
     */
    @Bean
    public ResponseSecurityMonitor responseSecurityMonitor() {
        return new ResponseSecurityMonitor();
    }
    
    /**
     * 配置安全审计日志记录器
     * 用于记录安全相关的事件和异常
     */
    @Bean
    public SecurityAuditLogger securityAuditLogger() {
        return new SecurityAuditLogger();
    }
    
    /**
     * 配置安全异常处理器
     * 统一处理安全相关的异常
     */
    @Bean
    public SecurityExceptionHandler securityExceptionHandler(SecurityAuditLogger auditLogger) {
        return new SecurityExceptionHandler(auditLogger);
    }
}