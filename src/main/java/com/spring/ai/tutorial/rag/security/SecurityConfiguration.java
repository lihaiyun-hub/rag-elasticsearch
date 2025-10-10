package com.spring.ai.tutorial.rag.security;

import org.springframework.context.annotation.Configuration;

/**
 * 安全配置类
 * 仅保留占位，不再声明冗余 Bean；
 * 各安全组件已通过 @Component 自动扫描注册。
 */
@Configuration
public class SecurityConfiguration {
    // 所有安全 Bean 均已通过类级 @Component 注入容器，
    // 此处不再使用 @Bean 重复声明，避免定义冲突。
}