package com.github.tharun_sh.student_management_rag_app.dto;

import java.util.List;

public record ChatResponse(String answer, List<Source> sources) {
    public record Source(String type, Object entityId, Double score) {
    }
}
