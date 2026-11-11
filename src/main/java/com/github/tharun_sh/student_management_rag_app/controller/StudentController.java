package com.github.tharun_sh.student_management_rag_app.controller;

import com.github.tharun_sh.student_management_rag_app.dto.StudentRequest;
import com.github.tharun_sh.student_management_rag_app.dto.StudentResponse;
import com.github.tharun_sh.student_management_rag_app.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    @Operation(summary = "List all students (optional department filter)")
    @GetMapping
    public List<StudentResponse> list(@RequestParam(required = false) String department) {
        return service.findAll(department);
    }

    @Operation(summary = "Get a student with enrolled courses")
    @GetMapping("/{id}")
    public StudentResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @Operation(summary = "Create a student")
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        StudentResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Replace a student")
    @PutMapping("/{id}")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Delete a student")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Enroll a student in a course")
    @PostMapping("/{id}/courses/{courseId}")
    public StudentResponse enroll(@PathVariable Long id, @PathVariable Long courseId) {
        return service.enroll(id, courseId);
    }

    @Operation(summary = "Unenroll a student from a course")
    @DeleteMapping("/{id}/courses/{courseId}")
    public StudentResponse unenroll(@PathVariable Long id, @PathVariable Long courseId) {
        return service.unenroll(id, courseId);
    }
}
