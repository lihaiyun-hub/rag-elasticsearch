package com.spring.ai.tutorial.rag.controller;

import com.spring.ai.tutorial.rag.services.CustomerSupportAssistant;
import org.springframework.http.MediaType;
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
						 @RequestParam(name = "userMessage") String userMessage) {
		try {
			return agent.chat(chatId, userMessage);
		} catch (Exception e) {
			logger.error("AssistantController chat failed", e);
			return "抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。";
		}
	}

}
