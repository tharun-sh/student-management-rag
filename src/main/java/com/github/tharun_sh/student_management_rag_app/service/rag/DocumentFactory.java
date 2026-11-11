package com.github.tharun_sh.student_management_rag_app.service.rag;

import com.github.tharun_sh.student_management_rag_app.entity.Course;
import com.github.tharun_sh.student_management_rag_app.entity.Student;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentFactory {

    public static String studentDocId(Long id) {
        return UUID.nameUUIDFromBytes(("student-" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String courseDocId(Long id) {
        return UUID.nameUUIDFromBytes(("course-" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public Document toDocument(Student s) {
        String courses = s.getCourses().isEmpty() ? "none"
                : s.getCourses().stream().sorted(Comparator.comparing(Course::getCode))
                        .map(c -> c.getCode() + " " + c.getTitle())
                        .collect(Collectors.joining(", "));
        String text = """
                Student: %s %s (ID %d)
                Email: %s
                Department: %s
                Year of study: %s
                GPA: %s
                Date of birth: %s
                Enrolled courses: %s""".formatted(s.getFirstName(), s.getLastName(), s.getId(),
                s.getEmail(), s.getDepartment(), s.getYearOfStudy(), s.getGpa(),
                s.getDateOfBirth() == null ? "unknown" : s.getDateOfBirth(), courses);
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("type", "student");
        meta.put("entityId", s.getId());
        meta.put("department", s.getDepartment());
        if (s.getYearOfStudy() != null) meta.put("yearOfStudy", s.getYearOfStudy());
        if (s.getGpa() != null) meta.put("gpa", s.getGpa());
        return new Document(studentDocId(s.getId()), text, meta);
    }

    public Document toDocument(Course c) {
        String students = c.getStudents().isEmpty() ? "none"
                : c.getStudents().stream().sorted(Comparator.comparing(Student::getId))
                        .map(s -> s.getFirstName() + " " + s.getLastName())
                        .collect(Collectors.joining(", "));
        String text = """
                Course: %s %s (ID %d)
                Credits: %s
                Instructor: %s
                Description: %s
                Enrolled students (%d): %s""".formatted(c.getCode(), c.getTitle(), c.getId(),
                c.getCredits(), c.getInstructor() == null ? "unknown" : c.getInstructor(),
                c.getDescription() == null ? "" : c.getDescription(), c.getStudents().size(), students);
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("type", "course");
        meta.put("entityId", c.getId());
        meta.put("code", c.getCode());
        return new Document(courseDocId(c.getId()), text, meta);
    }
}
