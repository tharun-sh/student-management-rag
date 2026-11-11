package com.github.tharun_sh.student_management_rag_app.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record StudentRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Email String email,
        @NotBlank String department,
        @NotNull @Min(1) @Max(5) Integer yearOfStudy,
        @NotNull @DecimalMin("0.0") @DecimalMax("4.0") Double gpa,
        @Past LocalDate dateOfBirth) {
}
