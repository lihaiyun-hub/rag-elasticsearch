package com.spring.ai.app.rag.config;

import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.ObjectProperty;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义的ElasticsearchVectorStore实现，支持IK分词器
 */
public class CustomIKElasticsearchVectorStore extends ElasticsearchVectorStore {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomIKElasticsearchVectorStore.class);
    
    private final RestClient restClient;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchVectorStoreOptions options;
    
    public CustomIKElasticsearchVectorStore(RestClient restClient, EmbeddingModel embeddingModel, 
                                             ElasticsearchVectorStoreOptions options) {
        super(ElasticsearchVectorStore.builder(restClient, embeddingModel).options(options));
        this.restClient = restClient;
        this.embeddingModel = embeddingModel;
        this.options = options;
        
        // 初始化schema
        initializeSchema();
    }
    
    private void initializeSchema() {
        try {
            ElasticsearchClient client = new ElasticsearchClient(
                new co.elastic.clients.transport.rest_client.RestClientTransport(
                    restClient, new co.elastic.clients.json.jackson.JacksonJsonpMapper()
                )
            );
            
            String indexName = options.getIndexName();
            
            // 检查索引是否已存在
            boolean indexExists = client.indices().exists(existsRequest -> 
                existsRequest.index(indexName)).value();
            
            if (indexExists) {
                logger.info("索引 '{}' 已存在，跳过创建", indexName);
                return;
            }
            
            logger.info("创建索引 '{}' 并配置IK分词器", indexName);
            
            // 创建索引配置，包含IK分词器
            CreateIndexRequest createIndexRequest = CreateIndexRequest.of(builder -> {
                builder.index(indexName)
                    .settings(settingsBuilder -> {
                        settingsBuilder
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                            .analysis(analysisBuilder -> {
                                analysisBuilder
                                    .analyzer("ik_analyzer", analyzerBuilder -> {
                                        analyzerBuilder
                                            .custom(customBuilder -> {
                                                customBuilder.tokenizer("ik_max_word");
                                                return customBuilder;
                                            });
                                        return analyzerBuilder;
                                    })
                                    .analyzer("ik_smart_analyzer", analyzerBuilder -> {
                                        analyzerBuilder
                                            .custom(customBuilder -> {
                                                customBuilder.tokenizer("ik_smart");
                                                return customBuilder;
                                            });
                                        return analyzerBuilder;
                                    });
                                return analysisBuilder;
                            });
                        return settingsBuilder;
                    })
                    .mappings(mappingsBuilder -> {
                        mappingsBuilder
                            .properties("content", Property.of(propertyBuilder -> {
                                TextProperty textProperty = TextProperty.of(textBuilder -> {
                                    textBuilder
                                        .analyzer("ik_analyzer")
                                        .fields("keyword", Property.of(keywordBuilder -> {
                                            return keywordBuilder.keyword(keywordProp -> 
                                                keywordProp.ignoreAbove(256));
                                        }));
                                    return textBuilder;
                                });
                                return propertyBuilder.text(textProperty);
                            }))
                            .properties("embedding", Property.of(propertyBuilder -> {
                                DenseVectorProperty denseVectorProperty = DenseVectorProperty.of(denseBuilder -> {
                                    denseBuilder
                                        .dims(options.getDimensions())
                                        .similarity(options.getSimilarity() != null ? options.getSimilarity().toString() : "cosine");
                                    return denseBuilder;
                                });
                                return propertyBuilder.denseVector(denseVectorProperty);
                            }))
                            .properties("metadata", Property.of(propertyBuilder -> {
                                ObjectProperty objectProperty = ObjectProperty.of(objectBuilder -> objectBuilder);
                                return propertyBuilder.object(objectProperty);
                            }));
                        return mappingsBuilder;
                    });
                return builder;
            });
            
            CreateIndexResponse response = client.indices().create(createIndexRequest);
            
            if (response.acknowledged()) {
                logger.info("索引 '{}' 创建成功，已配置IK分词器", indexName);
            } else {
                logger.warn("索引 '{}' 创建未确认", indexName);
            }
            
        } catch (IOException e) {
            logger.error("创建索引失败", e);
            throw new RuntimeException("Failed to create index with IK analyzer", e);
        } catch (Exception e) {
            logger.error("初始化schema失败", e);
            throw new RuntimeException("Failed to initialize schema", e);
        }
    }
}