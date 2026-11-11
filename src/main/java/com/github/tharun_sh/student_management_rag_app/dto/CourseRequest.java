package com.github.tharun_sh.student_management_rag_app.dto;

import jakarta.validation.constraints.*;

public record CourseRequest(
        @NotBlank String code,
        @NotBlank String title,
        @Size(max = 1000) String description,
        @NotNull @Min(1) @Max(10) Integer credits,
        String instructor) {
}
