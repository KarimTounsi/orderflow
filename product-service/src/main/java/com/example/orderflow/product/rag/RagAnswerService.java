package com.example.orderflow.product.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnExpression("'${spring.ai.model.chat:none}' == 'openai'")
@RequiredArgsConstructor
@Slf4j
public class RagAnswerService {

    private final ChatClient chatClient;
    private final Advisor ragAdvisor;

    public RagAnswerResponse ask(String question) {
        ChatClientResponse response = chatClient.prompt()
                .advisors(ragAdvisor)
                .user(question)
                .call()
                .chatClientResponse();

        String answer = response.chatResponse() != null
                ? response.chatResponse().getResult().getOutput().getText()
                : "";

        @SuppressWarnings("unchecked")
        List<Document> retrieved = (List<Document>) response.context()
                .getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());

        List<RagAnswerResponse.Source> sources = retrieved.stream()
                .map(doc -> new RagAnswerResponse.Source(
                        (String) doc.getMetadata().get("productId"),
                        (String) doc.getMetadata().get("name"),
                        doc.getScore() != null ? doc.getScore() : 0.0))
                .toList();

        log.info("RAG answer generated for question='{}' using {} source document(s)",
                question, sources.size());
        return new RagAnswerResponse(answer, sources);
    }
}
