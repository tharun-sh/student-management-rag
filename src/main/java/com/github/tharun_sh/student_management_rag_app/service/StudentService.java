package com.github.tharun_sh.student_management_rag_app.service;

import com.github.tharun_sh.student_management_rag_app.dto.*;
import com.github.tharun_sh.student_management_rag_app.entity.Course;
import com.github.tharun_sh.student_management_rag_app.entity.Student;
import com.github.tharun_sh.student_management_rag_app.event.EntityChangedEvent;
import com.github.tharun_sh.student_management_rag_app.event.EntityChangedEvent.Action;
import com.github.tharun_sh.student_management_rag_app.event.EntityChangedEvent.Type;
import com.github.tharun_sh.student_management_rag_app.exception.*;
import com.github.tharun_sh.student_management_rag_app.mapper.EntityMapper;
import com.github.tharun_sh.student_management_rag_app.repository.CourseRepository;
import com.github.tharun_sh.student_management_rag_app.repository.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StudentService {

    private final StudentRepository students;
    private final CourseRepository courses;
    private final EntityMapper mapper;
    private final ApplicationEventPublisher events;

    public StudentService(StudentRepository students, CourseRepository courses,
                          EntityMapper mapper, ApplicationEventPublisher events) {
        this.students = students;
        this.courses = courses;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> findAll(String department) {
        List<Student> list = (department == null || department.isBlank())
                ? students.findAllWithCourses()
                : students.findByDepartmentIgnoreCase(department);
        return list.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse findById(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    public StudentResponse create(StudentRequest req) {
        if (students.existsByEmail(req.email())) {
            throw new DuplicateResourceException("Student with email " + req.email() + " already exists");
        }
        Student s = new Student();
        mapper.apply(req, s);
        s = students.saveAndFlush(s);
        events.publishEvent(new EntityChangedEvent(Type.STUDENT, s.getId(), Action.CREATED, Set.of()));
        return mapper.toResponse(s);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest req) {
        Student s = load(id);
        students.findByEmail(req.email()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new DuplicateResourceException("Student with email " + req.email() + " already exists");
        });
        mapper.apply(req, s);
        s = students.saveAndFlush(s);
        events.publishEvent(new EntityChangedEvent(Type.STUDENT, id, Action.UPDATED, Set.of()));
        return mapper.toResponse(s);
    }

    @Transactional
    public void delete(Long id) {
        Student s = load(id);
        Set<Long> courseIds = new HashSet<>();
        for (Course c : Set.copyOf(s.getCourses())) {
            courseIds.add(c.getId());
            s.removeCourse(c);
        }
        students.delete(s);
        students.flush();
        events.publishEvent(new EntityChangedEvent(Type.STUDENT, id, Action.DELETED, courseIds));
    }

    @Transactional
    public StudentResponse enroll(Long studentId, Long courseId) {
        Student s = load(studentId);
        Course c = courses.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course " + courseId + " not found"));
        if (!s.getCourses().contains(c)) {
            s.addCourse(c);
            students.flush();
            events.publishEvent(new EntityChangedEvent(Type.STUDENT, studentId, Action.ENROLLMENT_CHANGED, Set.of(courseId)));
        }
        return mapper.toResponse(s);
    }

    @Transactional
    public StudentResponse unenroll(Long studentId, Long courseId) {
        Student s = load(studentId);
        Course c = courses.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course " + courseId + " not found"));
        if (s.getCourses().contains(c)) {
            s.removeCourse(c);
            students.flush();
            events.publishEvent(new EntityChangedEvent(Type.STUDENT, studentId, Action.ENROLLMENT_CHANGED, Set.of(courseId)));
        }
        return mapper.toResponse(s);
    }

    private Student load(Long id) {
        return students.findByIdWithCourses(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student " + id + " not found"));
    }
}
