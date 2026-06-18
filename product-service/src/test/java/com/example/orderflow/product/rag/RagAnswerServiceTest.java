package com.example.orderflow.product.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagAnswerService Unit Tests (full RAG pipeline, mocked edges)")
class RagAnswerServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private VectorStore vectorStore;

    private RagAnswerService ragAnswerService;

    @BeforeEach
    void setUp() {
        Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(5)
                        .similarityThreshold(0.20)
                        .build())
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ragAnswerService = new RagAnswerService(chatClient, ragAdvisor);
    }

    private Document productDoc(String productId, String name, double score) {
        return Document.builder()
                .id(ProductIndexService.vectorId(productId))
                .text(name + ". Category: Sports. Great for running.")
                .metadata(Map.of("productId", productId, "name", name, "price", 79.99))
                .score(score)
                .build();
    }

    @Test
    @DisplayName("ask() injects retrieved products into the LLM prompt and returns answer with sources")
    void askInjectsContextAndReturnsSources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(productDoc("p1", "Running Foam Roller", 0.41)));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture()))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("I recommend the Running Foam Roller (79.99).")))));

        RagAnswerResponse response = ragAnswerService.ask("what should I buy for running?");

        // Generation: odpowiedz LLM wraca do klienta
        assertThat(response.answer()).contains("Running Foam Roller");

        // Sources: dokumenty, ktore advisor wstrzyknal do prompta
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().productId()).isEqualTo("p1");
        assertThat(response.sources().getFirst().score()).isEqualTo(0.41);

        // Augmentation: prompt wyslany do LLM MUSI zawierac tekst pobranego produktu -
        // to jest istota RAG (model odpowiada z naszego kontekstu, nie "z glowy")
        String promptText = promptCaptor.getValue().getContents();
        assertThat(promptText)
                .contains("Running Foam Roller")
                .contains("what should I buy for running?");
    }

    @Test
    @DisplayName("ask() with no retrieval hits returns empty sources (augmenter instructs polite refusal)")
    void askWithNoHitsReturnsEmptySources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("Sorry, I could not find a matching product.")))));

        RagAnswerResponse response = ragAnswerService.ask("do you sell spaceships?");

        assertThat(response.answer()).isNotBlank();
        assertThat(response.sources()).isEmpty();
    }
}
