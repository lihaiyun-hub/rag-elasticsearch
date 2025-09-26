package com.spring.ai.tutorial.rag.controller;

import com.spring.ai.tutorial.rag.services.CustomerSupportAssistant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RequestMapping("/api/assistant")
@RestController
public class AssistantController {

	private final CustomerSupportAssistant agent;

	public AssistantController(CustomerSupportAssistant agent) {
		this.agent = agent;
	}

	@RequestMapping(path="/chat")
	public String chat(@RequestParam(name = "chatId") String chatId,
						 @RequestParam(name = "userMessage") String userMessage) {
		return agent.chat(chatId, userMessage);
	}

}
