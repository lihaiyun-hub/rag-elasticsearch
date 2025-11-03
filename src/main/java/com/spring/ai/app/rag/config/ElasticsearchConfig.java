package com.spring.ai.app.rag.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.spring.ai.app.rag.vectorstore.ElasticsearchVectorStore;

@Configuration
public class ElasticsearchConfig {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Value("${spring.elasticsearch.uris}")
    private String url;
    @Value("${spring.elasticsearch.username:}")
    private String username;
    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.ai.retrieval.vector.index-name}")
    private String indexName;
    @Value("${spring.ai.retrieval.vector.similarity}")
    private String similarity;
    @Value("${spring.ai.retrieval.vector.dimensions}")
    private int dimensions;

    @Bean
    public RestClient restClient() {
        // 解析URL
        String[] urlParts = url.split("://");
        String protocol = urlParts[0];
        String hostAndPort = urlParts[1];
        String[] hostPortParts = hostAndPort.split(":");
        String host = hostPortParts[0];
        int port = Integer.parseInt(hostPortParts[1]);

        logger.info("create elasticsearch rest client");
        
        // 构建RestClient
        org.elasticsearch.client.RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, protocol));
        
        // 只有在提供了用户名密码时才添加认证
        if (username != null && !username.trim().isEmpty() && 
            password != null && !password.trim().isEmpty()) {
            logger.info("使用用户名密码认证连接ES");
            // 创建凭证提供者
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));
            
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                return httpClientBuilder;
            });
        } else {
            logger.info("无认证模式连接ES");
        }
        
        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @Bean
    public ElasticsearchVectorStore vectorStore(ElasticsearchClient elasticsearchClient) {
        return new ElasticsearchVectorStore(elasticsearchClient, indexName, dimensions, similarity);
    }
}
