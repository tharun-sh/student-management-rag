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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final EntityMapper mapper;
    private final ApplicationEventPublisher events;

    public CourseService(CourseRepository courses, EntityMapper mapper, ApplicationEventPublisher events) {
        this.courses = courses;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> findAll() {
        return courses.findAllWithStudents().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse findById(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<StudentSummary> findStudents(Long id) {
        return load(id).getStudents().stream().map(mapper::toSummary).toList();
    }

    @Transactional
    public CourseResponse create(CourseRequest req) {
        if (courses.existsByCode(req.code())) {
            throw new DuplicateResourceException("Course with code " + req.code() + " already exists");
        }
        Course c = new Course();
        mapper.apply(req, c);
        c = courses.saveAndFlush(c);
        events.publishEvent(new EntityChangedEvent(Type.COURSE, c.getId(), Action.CREATED, Set.of()));
        return mapper.toResponse(c);
    }

    @Transactional
    public CourseResponse update(Long id, CourseRequest req) {
        Course c = load(id);
        courses.findByCode(req.code()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new DuplicateResourceException("Course with code " + req.code() + " already exists");
        });
        mapper.apply(req, c);
        c = courses.saveAndFlush(c);
        Set<Long> studentIds = new HashSet<>();
        c.getStudents().forEach(s -> studentIds.add(s.getId()));
        events.publishEvent(new EntityChangedEvent(Type.COURSE, id, Action.UPDATED, studentIds));
        return mapper.toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        Course c = load(id);
        Set<Long> studentIds = new HashSet<>();
        for (Student s : Set.copyOf(c.getStudents())) {
            studentIds.add(s.getId());
            s.removeCourse(c);
        }
        courses.delete(c);
        courses.flush();
        events.publishEvent(new EntityChangedEvent(Type.COURSE, id, Action.DELETED, studentIds));
    }

    private Course load(Long id) {
        return courses.findByIdWithStudents(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course " + id + " not found"));
    }
}
