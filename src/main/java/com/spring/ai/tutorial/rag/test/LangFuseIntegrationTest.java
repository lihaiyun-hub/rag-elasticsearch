package com.spring.ai.tutorial.rag.test;

import com.spring.ai.tutorial.rag.services.CustomerSupportAssistant;
import com.spring.ai.tutorial.rag.model.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * LangFuse 集成测试
 * 用于验证 LangFuse 是否正确集成并接收数据
 * 
 * @author AI Assistant
 * @since 2025/10/09
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.dev-tests", name = "enabled", havingValue = "true")
public class LangFuseIntegrationTest implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LangFuseIntegrationTest.class);

    @Autowired
    private CustomerSupportAssistant customerSupportAssistant;

    @Override
    public void run(String... args) throws Exception {
        logger.info("开始 LangFuse 集成测试...");
        
        try {
            String testChatId = "langfuse-test-chat-001";
            
            // 测试1: 简单的聊天请求
            logger.info("测试1: 发送简单聊天请求");
            String simpleResponse = customerSupportAssistant.chat(testChatId, "你好，这是一个 LangFuse 集成测试");
            logger.info("简单聊天响应: {}", simpleResponse);
            
            // 等待一下让数据发送到 LangFuse
            Thread.sleep(2000);
            
            // 测试2: RAG 查询（带有用户上下文）
            logger.info("测试2: 发送 RAG 查询请求");
            UserContext userContext = new UserContext(
                "测试用户",
                50000.0,
                "标准贷款计划",
                "正常",
                100000.0
            );
            String ragResponse = customerSupportAssistant.chat(testChatId, "什么是机器学习？请用简单的话解释一下", userContext);
            logger.info("RAG 查询响应: {}", ragResponse);
            
            // 等待数据发送
            Thread.sleep(2000);
            
            // 测试3: 继续对话（测试对话历史）
            logger.info("测试3: 继续对话测试");
            String followUpResponse = customerSupportAssistant.chat(testChatId, "那么深度学习又是什么？");
            logger.info("继续对话响应: {}", followUpResponse);
            
            // 等待最终数据发送
            Thread.sleep(3000);
            
            logger.info("LangFuse 集成测试完成！");
            logger.info("请检查 LangFuse Web 界面: http://localhost:3000");
            logger.info("查看是否收到了测试数据");
            logger.info("提示: 登录 LangFuse 后，可以在 'Traces' 页面查看详细的调用跟踪信息");
            
        } catch (Exception e) {
            logger.error("测试过程中出现错误", e);
        }
    }
}