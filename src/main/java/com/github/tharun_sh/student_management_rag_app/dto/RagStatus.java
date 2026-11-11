package com.github.tharun_sh.student_management_rag_app.dto;

import java.time.Instant;

public record RagStatus(int documentCount, Instant lastReindexAt, String chatModel, String embeddingModel) {
}
