package com.spring.ai.tutorial.rag.controller;

import com.spring.ai.tutorial.rag.services.CustomerSupportAssistant;
import com.spring.ai.tutorial.rag.model.UserContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RequestMapping("/api/assistant")
@RestController
public class AssistantController {

	private final CustomerSupportAssistant agent;

	private static final Logger logger = LoggerFactory.getLogger(AssistantController.class);

	public AssistantController(CustomerSupportAssistant agent) {
		this.agent = agent;
	}

	@RequestMapping(path="/chat")
	public String chat(@RequestParam(name = "chatId") String chatId,
					 @RequestParam(name = "userMessage") String userMessage,
					 @RequestParam(name = "userName", required = false) String userName,
					 @RequestParam(name = "availableCredit", required = false, defaultValue = "10000") Double availableCredit,
					 @RequestParam(name = "currentLoanPlan", required = false, defaultValue = "无") String currentLoanPlan,
					 @RequestParam(name = "recentRepaymentStatus", required = false, defaultValue = "正常") String recentRepaymentStatus,
					 @RequestParam(name = "maxLoanAmount", required = false, defaultValue = "50000") Double maxLoanAmount) {
		
		try {
			// 构建用户上下文
			UserContext userContext = null;
			if (userName != null) {
				userContext = new UserContext(userName, availableCredit, currentLoanPlan, 
										  recentRepaymentStatus, maxLoanAmount);
			}
			
			String response = agent.chat(chatId, userMessage, userContext);
			
			return response;
		} catch (Exception e) {
			logger.error("AssistantController chat failed", e);
			
			return "抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。";
		}
	}

}
