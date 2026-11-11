package com.github.tharun_sh.student_management_rag_app.controller;

import com.github.tharun_sh.student_management_rag_app.dto.*;
import com.github.tharun_sh.student_management_rag_app.service.rag.ChatService;
import com.github.tharun_sh.student_management_rag_app.service.rag.VectorSyncService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final ChatService chat;
    private final VectorSyncService sync;

    public RagController(ChatService chat, VectorSyncService sync) {
        this.chat = chat;
        this.sync = sync;
    }

    @Operation(summary = "Ask a question about students and courses")
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chat.ask(request);
    }

    @Operation(summary = "Rebuild the vector store from the database")
    @PostMapping("/reindex")
    public ReindexResult reindex() {
        try {
            return sync.reindexAll();
        } catch (com.github.tharun_sh.student_management_rag_app.exception.ReindexInProgressException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new com.github.tharun_sh.student_management_rag_app.exception.RagUnavailableException(e.getMessage(), e);
        }
    }

    @Operation(summary = "Vector store status")
    @GetMapping("/status")
    public RagStatus status() {
        return chat.status();
    }
}
