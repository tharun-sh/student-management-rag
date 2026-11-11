package com.github.tharun_sh.student_management_rag_app.dto;

import java.time.LocalDate;
import java.util.Set;

public record StudentResponse(
        Long id, String firstName, String lastName, String email, String department,
        Integer yearOfStudy, Double gpa, LocalDate dateOfBirth, Set<CourseSummary> courses) {
}
