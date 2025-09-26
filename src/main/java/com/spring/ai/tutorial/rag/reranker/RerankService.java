package com.spring.ai.tutorial.rag.reranker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;

@Component
public class RerankService {

    private static final Logger logger = LoggerFactory.getLogger(RerankService.class);

    @Value("${spring.ai.rag.reranker.enabled}")
    private boolean enabled;

    @Value("${spring.ai.rag.reranker.endpoint}")
    private String endpoint;

    @Value("${spring.ai.rag.reranker.model}")
    private String model;

    @Value("${spring.ai.rag.reranker.api-key}")
    private String apiKey;

    @Value("${spring.ai.rag.reranker.timeout-ms}")
    private int timeoutMs;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RerankService() {
        this.webClient = WebClient.builder().build();
    }

    public boolean isEnabled() {
        return enabled && StringUtils.hasText(endpoint);
    }

    public List<ResultItem> rerank(String query, List<String> documents) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("documents", documents);
        payload.put("return_documents", false);
        payload.put("model", model);
        try {
            String response = webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (StringUtils.hasText(apiKey)) {
                            headers.set("Authorization", apiKey.startsWith("Bearer ") ? apiKey : ("Bearer " + apiKey));
                        }
                    })
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            if (!StringUtils.hasText(response)) {
                logger.warn(" reranker returned empty response");
                return Collections.emptyList();
            }
            RerankResponse rerankResponse = objectMapper.readValue(response, RerankResponse.class);
            if (rerankResponse.results == null) {
                logger.warn(" reranker response has no results");
                return Collections.emptyList();
            }
            List<ResultItem> items = new ArrayList<>();
            for (RerankResponse.Result r : rerankResponse.results) {
                items.add(new ResultItem(r.index, r.relevanceScore));
            }
            items.sort((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore));
            return items;
        } catch (WebClientResponseException wcre) {
            String body = wcre.getResponseBodyAsString();
            try {
                ErrorResponse error = new ObjectMapper().readValue(body, ErrorResponse.class);
                logger.error(" rerank error: {}", error);
            } catch (Exception ignore) {
                logger.error(" rerank error status={}, body={}", wcre.getStatusCode(), body);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error(" rerank failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RerankResponse {
        public String object;
        public List<Result> results;
        public String model;
        public Usage usage;
        public String id;
        public long created;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Result {
            @JsonProperty("relevance_score")
            public double relevanceScore;
            public int index;
            public String document;
        }
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Usage {
            @JsonProperty("prompt_tokens")
            public int promptTokens;
            @JsonProperty("total_tokens")
            public int totalTokens;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorResponse {
        public List<Detail> detail;
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Detail {
            public List<Object> loc;
            public String msg;
            public String type;
        }
        @Override
        public String toString() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "ErrorResponse{" + String.valueOf(detail) + '}';
            }
        }
    }

    public static class ResultItem {
        public final int index;
        public final double relevanceScore;
        public ResultItem(int index, double relevanceScore) {
            this.index = index;
            this.relevanceScore = relevanceScore;
        }
    }
}