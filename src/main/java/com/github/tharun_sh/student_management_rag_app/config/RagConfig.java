package com.github.tharun_sh.student_management_rag_app.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    static final String SYSTEM_PROMPT = """
            You are an assistant for a student management system.
            Answer ONLY using the student and course records provided in the context.
            If the context does not contain the answer, say "I couldn't find that in the student records."
            Do not invent students, courses, grades or numbers.
            Be concise. When listing students, include their name and the relevant fields.
            """;

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    @PostConstruct
    void checkApiKey() {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY not set. Add it to ~/.bashrc and run 'source ~/.bashrc'.");
        }
    }

    @Bean
    SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore,
                          @Value("${app.rag.top-k}") int topK,
                          @Value("${app.rag.similarity-threshold}") double threshold) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                .topK(topK)
                                .similarityThreshold(threshold)
                                .build())
                        .build())
                .build();
    }
}
