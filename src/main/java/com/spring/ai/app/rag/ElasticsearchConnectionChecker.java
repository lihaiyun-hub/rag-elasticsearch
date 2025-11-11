package com.spring.ai.app.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchConnectionChecker {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @PostConstruct
    public void checkConnection() {
        try {
            System.out.println("=== 开始测试Elasticsearch连接 ===");
            
            // 获取ES信息
            InfoResponse info = elasticsearchClient.info();
            System.out.println("✅ ES连接成功！");
            System.out.println("集群名称: " + info.clusterName());
            System.out.println("ES版本: " + info.version().number());
            System.out.println("节点名称: " + info.name());
            
            // 检查当前认证配置
            if (username == null || username.trim().isEmpty()) {
                System.out.println("当前配置: 无认证模式");
            } else {
                System.out.println("当前配置: 使用用户名认证 - " + username);
            }
            
            // 尝试简单的搜索操作
            try {
                SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("_all")
                    .size(0)
                );
                SearchResponse<?> response = elasticsearchClient.search(searchRequest, Object.class);
                System.out.println("搜索测试: 成功");
            } catch (Exception searchEx) {
                System.out.println("搜索测试失败: " + searchEx.getMessage());
            }
            
            System.out.println("=== ES连接测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("❌ ES连接失败！");
            System.err.println("错误信息: " + e.getMessage());
            
            if (e.getMessage().contains("security_exception") || 
                e.getMessage().contains("authentication") ||
                e.getMessage().contains("Unauthorized")) {
                System.err.println("💡 建议：你的ES需要认证，请在配置文件中添加用户名密码");
            } else if (e.getMessage().contains("Connection refused")) {
                System.err.println("💡 建议：检查ES是否在运行，地址是否正确");
            } else {
                System.err.println("💡 建议：检查ES配置和网络连接");
            }
            
            e.printStackTrace();
        }
    }
}