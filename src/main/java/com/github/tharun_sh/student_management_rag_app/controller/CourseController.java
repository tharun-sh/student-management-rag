package com.github.tharun_sh.student_management_rag_app.controller;

import com.github.tharun_sh.student_management_rag_app.dto.*;
import com.github.tharun_sh.student_management_rag_app.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @Operation(summary = "List all courses")
    @GetMapping
    public List<CourseResponse> list() {
        return service.findAll();
    }

    @Operation(summary = "Get a course with enrolled students")
    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @Operation(summary = "Create a course")
    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        CourseResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Replace a course")
    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Delete a course (unenrolls students first)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List students enrolled in a course")
    @GetMapping("/{id}/students")
    public List<StudentSummary> students(@PathVariable Long id) {
        return service.findStudents(id);
    }
}
