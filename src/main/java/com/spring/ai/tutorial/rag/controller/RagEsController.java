package com.spring.ai.tutorial.rag.controller;

import com.spring.ai.tutorial.rag.tools.TimeTools;
import com.spring.ai.tutorial.rag.tools.WeatherTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;

/**
 * @author yingzi
 * @date 2025/5/26 22:39
 */
@RestController
@RequestMapping("/rag/es")
public class RagEsController {


    private static final Logger logger = LoggerFactory.getLogger(RagEsController.class);

    private ChatClient chatClient;


    public RagEsController(Resource systemPromptResource, ChatClient.Builder builder, RetrievalAugmentationAdvisor retrievalAugmentationAdvisor) throws IOException {


        this.chatClient = builder
                .defaultSystem(systemPromptResource)
                .defaultTools(new TimeTools(), new WeatherTools())
                .defaultToolNames("getBookingDetails", "changeBooking", "cancelBooking")
                .defaultAdvisors(retrievalAugmentationAdvisor)
                .defaultOptions(ToolCallingChatOptions.builder().internalToolExecutionEnabled(true).build())
                .build();
    }


    @GetMapping("/chat-rag-advisor")
    public String chatRagAdvisor(@RequestParam(value = "query", defaultValue = "你好，请告诉我影子这个人的身份信息") String query) {
        logger.info("start chat with rag-advisor");
        return chatClient.prompt(query).call().content();
    }


}
