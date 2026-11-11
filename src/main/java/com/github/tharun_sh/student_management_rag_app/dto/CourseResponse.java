package com.github.tharun_sh.student_management_rag_app.dto;

import java.util.Set;

public record CourseResponse(
        Long id, String code, String title, String description, Integer credits,
        String instructor, Set<StudentSummary> students) {
}
