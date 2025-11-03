package com.spring.ai.app.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;

@SpringBootTest
@ActiveProfiles("dev")
public class ElasticsearchConnectionTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    public void testElasticsearchConnection() {
        try {
            // 测试ES连接
            InfoResponse info = elasticsearchClient.info();
            System.out.println("ES连接成功！");
            System.out.println("集群名称: " + info.clusterName());
            System.out.println("ES版本: " + info.version().number());
            System.out.println("节点名称: " + info.name());
            
            // 检查是否需要认证
            System.out.println("连接配置使用了认证信息");
            
        } catch (Exception e) {
            System.err.println("ES连接失败: " + e.getMessage());
            e.printStackTrace();
            
            // 如果是认证错误，建议移除认证配置
            if (e.getMessage().contains("security_exception") || 
                e.getMessage().contains("authentication")) {
                System.err.println("建议：移除配置文件中的用户名密码配置");
            }
        }
    }
}