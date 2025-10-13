package com.spring.ai.app.rag.controller;

import com.spring.ai.app.rag.services.CustomerSupportAssistant;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.model.ChatRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RequestMapping("/api/assistant")
@RestController
public class AssistantController {

	private final CustomerSupportAssistant agent;
	private final com.spring.ai.app.rag.services.ConsumerCreditService consumerCreditService;

	private static final Logger logger = LoggerFactory.getLogger(AssistantController.class);

	public AssistantController(CustomerSupportAssistant agent, com.spring.ai.app.rag.services.ConsumerCreditService consumerCreditService) {
		this.agent = agent;
		this.consumerCreditService = consumerCreditService;
	}

	@PostMapping(path="/chat")
	public String chat(@RequestBody ChatRequest request) {
		try {
			// 构建用户上下文（允许为空，后端会使用默认值）
			UserContext userContext = null;
			if (request.getUserName() != null || request.getAvailableCredit() != null
					|| request.getCurrentLoanPlan() != null || request.getRecentRepaymentStatus() != null
					|| request.getMaxLoanAmount() != null) {
				userContext = new UserContext(
						request.getUserName() != null ? request.getUserName() : new UserContext().getUserName(),
						request.getAvailableCredit() != null ? request.getAvailableCredit() : new UserContext().getAvailableCredit(),
						request.getCurrentLoanPlan() != null ? request.getCurrentLoanPlan() : new UserContext().getCurrentLoanPlan(),
						request.getRecentRepaymentStatus() != null ? request.getRecentRepaymentStatus() : new UserContext().getRecentRepaymentStatus(),
						request.getMaxLoanAmount() != null ? request.getMaxLoanAmount() : new UserContext().getMaxLoanAmount()
				);
			}

            // 若前端显式传递了授信状态，则覆盖服务侧的记录，并注入到用户上下文
            if (request.getAuthorized() != null) {
                // 更新服务侧记录，保证状态机与授权状态一致
                consumerCreditService.setAuthorized(request.getChatId(), request.getAuthorized());
                // 将授权状态注入到用户上下文，供后续助手逻辑直接使用
                if (userContext == null) {
                    userContext = new UserContext();
                }
                userContext.setAuthorized(request.getAuthorized());
            }

			String response = agent.chat(request.getChatId(), request.getUserMessage(), userContext);
			return response;
		} catch (Exception e) {
			logger.error("AssistantController chat failed", e);
			return "抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。";
		}
	}

}
