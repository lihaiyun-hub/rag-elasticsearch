package com.spring.ai.app.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ElasticsearchTestApplication implements CommandLineRunner {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ElasticsearchTestApplication.class);
        app.setAdditionalProfiles("dev");
        app.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Elasticsearch连接测试开始 ===");
        
        try {
            // 获取ES信息
            InfoResponse info = elasticsearchClient.info();
            System.out.println("✅ 连接成功！");
            System.out.println("集群名称: " + info.clusterName());
            System.out.println("ES版本: " + info.version().number());
            System.out.println("节点名称: " + info.name());
            
            System.out.println("\n📊 测试结果:");
            System.out.println("你的ES实例: 不需要用户名密码（无认证模式）");
            System.out.println("配置建议: 保持当前配置，不要添加用户名密码");
            
        } catch (Exception e) {
            System.err.println("❌ 连接失败！");
            System.err.println("错误信息: " + e.getMessage());
            
            if (e.getMessage().contains("security_exception") || 
                e.getMessage().contains("authentication") ||
                e.getMessage().contains("Unauthorized")) {
                System.err.println("\n📊 测试结果:");
                System.err.println("你的ES实例: 需要用户名密码认证");
                System.err.println("配置建议: 在配置文件中添加正确的用户名密码");
            } else if (e.getMessage().contains("Connection refused")) {
                System.err.println("\n📊 测试结果:");
                System.err.println("你的ES实例: 可能未启动或地址错误");
                System.err.println("配置建议: 检查ES是否在运行，地址是否正确");
            } else {
                System.err.println("\n📊 测试结果:");
                System.err.println("你的ES实例: 其他连接问题");
                System.err.println("配置建议: 检查网络连接和ES配置");
            }
            
            System.exit(1);
        }
        
        System.out.println("\n=== 测试完成，应用即将退出 ===");
        System.exit(0);
    }
}