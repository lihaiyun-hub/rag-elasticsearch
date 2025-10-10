package com.spring.ai.tutorial.rag.config;

import com.spring.ai.tutorial.rag.retriever.HybridDocumentRetriever;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;

/**
 * @author LHY
 * @date 2025-09-22 16:08
 * @description
 */
@Configuration
public class RetrieveConfig {

    @Value("${spring.ai.rag.vector.top-k}")
    private int vectorTopK;

    @Value("${spring.ai.rag.vector.similarity-threshold}")
    private double vectorSimilarityThreshold;


    private final List<DocumentPostProcessor> documentPostProcessors;

    public RetrieveConfig(List<DocumentPostProcessor> documentPostProcessors) {
        this.documentPostProcessors = documentPostProcessors;
    }

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(HybridDocumentRetriever hybridDocumentRetriever,
                                                                     List<QueryTransformer> queryTransformers) {
        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(hybridDocumentRetriever)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
//                        .promptTemplate(PromptTemplate.builder()
//                                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
//                                .resource(retrievePromptResource)
//                                .build())
                        .build())
                .documentPostProcessors(documentPostProcessors)
                .queryTransformers(queryTransformers);

        return builder.build();
    }

    @Bean
    @Qualifier("vectorStoreDocumentRetriever")
    public DocumentRetriever vectorStoreDocumentRetriever(VectorStore vectorStore) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(vectorSimilarityThreshold)
                .topK(vectorTopK)
                .build();
    }


}
