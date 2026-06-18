package com.example.orderflow.product.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${spring.ai.model.chat:none}' == 'openai'")
public class RagConfig {

    private static final String SYSTEM_PROMPT = """
            You are OrderFlow's shopping assistant for an e-commerce store.
            Answer ONLY based on the product context provided in the user message.
            Recommend specific products by exact name and price from the context.
            If the context does not contain a suitable product, say so honestly - never invent products.
            Be concise (2-4 sentences). Answer in the same language as the question.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    @Bean
    Advisor ragAdvisor(VectorStore vectorStore) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(5)
                        .similarityThreshold(0.20)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .build())
                .build();
    }
}
