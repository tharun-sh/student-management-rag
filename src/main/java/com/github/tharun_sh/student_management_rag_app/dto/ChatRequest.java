package com.github.tharun_sh.student_management_rag_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 500) String question,
        @Pattern(regexp = "student|course", message = "must be 'student' or 'course'") String type) {
}
