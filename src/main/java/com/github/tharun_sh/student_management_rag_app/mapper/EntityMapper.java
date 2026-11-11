package com.github.tharun_sh.student_management_rag_app.mapper;

import com.github.tharun_sh.student_management_rag_app.dto.*;
import com.github.tharun_sh.student_management_rag_app.entity.Course;
import com.github.tharun_sh.student_management_rag_app.entity.Student;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Component
public class EntityMapper {

    public StudentResponse toResponse(Student s) {
        return new StudentResponse(s.getId(), s.getFirstName(), s.getLastName(), s.getEmail(),
                s.getDepartment(), s.getYearOfStudy(), s.getGpa(), s.getDateOfBirth(),
                s.getCourses().stream()
                        .sorted(Comparator.comparing(Course::getCode))
                        .map(c -> new CourseSummary(c.getId(), c.getCode(), c.getTitle()))
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public CourseResponse toResponse(Course c) {
        return new CourseResponse(c.getId(), c.getCode(), c.getTitle(), c.getDescription(),
                c.getCredits(), c.getInstructor(),
                c.getStudents().stream()
                        .sorted(Comparator.comparing(Student::getId))
                        .map(this::toSummary)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public StudentSummary toSummary(Student s) {
        return new StudentSummary(s.getId(), s.getFirstName(), s.getLastName());
    }

    public void apply(StudentRequest r, Student s) {
        s.setFirstName(r.firstName());
        s.setLastName(r.lastName());
        s.setEmail(r.email());
        s.setDepartment(r.department());
        s.setYearOfStudy(r.yearOfStudy());
        s.setGpa(r.gpa());
        s.setDateOfBirth(r.dateOfBirth());
    }

    public void apply(CourseRequest r, Course c) {
        c.setCode(r.code());
        c.setTitle(r.title());
        c.setDescription(r.description());
        c.setCredits(r.credits());
        c.setInstructor(r.instructor());
    }
}
