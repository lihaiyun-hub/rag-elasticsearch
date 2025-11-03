package com.spring.ai.app.rag;

import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

public class SimpleEsTest {
    public static void main(String[] args) {
        System.out.println("=== 简单Elasticsearch连接测试 ===");
        
        try {
            // 创建RestClient
            RestClient restClient = RestClient.builder(
                new HttpHost("127.0.0.1", 9200, "http")
            ).build();
            
            // 发送简单的GET请求到根路径
            Request request = new Request("GET", "/");
            Response response = restClient.performRequest(request);
            
            System.out.println("✅ 连接成功！");
            System.out.println("状态码: " + response.getStatusLine().getStatusCode());
            System.out.println("响应: " + new String(response.getEntity().getContent().readAllBytes()));
            
            restClient.close();
            
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
        }
        
        System.out.println("\n=== 测试完成 ===");
    }
}