package com.github.tharun_sh.student_management_rag_app.service.rag;

import com.github.tharun_sh.student_management_rag_app.dto.ChatRequest;
import com.github.tharun_sh.student_management_rag_app.dto.ChatResponse;
import com.github.tharun_sh.student_management_rag_app.dto.RagStatus;
import com.github.tharun_sh.student_management_rag_app.exception.RagUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorSyncService sync;
    private final String chatModel;
    private final String embeddingModel;

    public ChatService(ChatClient chatClient, VectorSyncService sync,
                       @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String chatModel,
                       @Value("${spring.ai.google.genai.embedding.text.options.model:gemini-embedding-001}") String embeddingModel) {
        this.chatClient = chatClient;
        this.sync = sync;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    public ChatResponse ask(ChatRequest request) {
        try {
            var spec = chatClient.prompt().user(request.question());
            if (request.type() != null) {
                spec = spec.advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION,
                        "type == '" + request.type() + "'"));
            }
            ChatClientResponse r = spec.call().chatClientResponse();
            String answer = r.chatResponse().getResult().getOutput().getText();
            @SuppressWarnings("unchecked")
            List<Document> docs = (List<Document>) r.context()
                    .getOrDefault(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of());
            List<ChatResponse.Source> sources = docs.stream()
                    .map(d -> new ChatResponse.Source(
                            String.valueOf(d.getMetadata().get("type")),
                            d.getMetadata().get("entityId"),
                            d.getScore()))
                    .toList();
            return new ChatResponse(answer, sources);
        } catch (RuntimeException e) {
            throw new RagUnavailableException(e.getMessage(), e);
        }
    }

    public RagStatus status() {
        return new RagStatus(sync.documentCount(), sync.lastReindexAt(), chatModel, embeddingModel);
    }
}
