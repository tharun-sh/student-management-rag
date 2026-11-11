package com.github.tharun_sh.student_management_rag_app.controller;

import com.github.tharun_sh.student_management_rag_app.dto.StudentResponse;
import com.github.tharun_sh.student_management_rag_app.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tharun_sh.student_management_rag_app.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentController.class)
@ActiveProfiles("test")
class StudentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StudentService studentService;

    private static final String VALID_STUDENT = """
            {
              "firstName": "Ravi",
              "lastName": "Kumar",
              "email": "ravi.kumar@example.com",
              "department": "Computer Science",
              "yearOfStudy": 3,
              "gpa": 3.8,
              "dateOfBirth": "2004-05-12"
            }
            """;

    @Test
    void createStudent_returns201WithLocationHeaderAndBody() throws Exception {
        when(studentService.create(any())).thenReturn(new StudentResponse(
                1L, "Ravi", "Kumar", "ravi.kumar@example.com", "Computer Science",
                3, 3.8, LocalDate.of(2004, 5, 12), Set.of()));

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STUDENT))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/students/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("ravi.kumar@example.com"));
    }

    @Test
    void createStudent_withInvalidInput_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "Kumar", "email": "not-an-email",
                                 "department": "CS", "yearOfStudy": 3, "gpa": 3.8}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

    @Test
    void getStudent_missingId_returns404() throws Exception {
        when(studentService.findById(99L)).thenThrow(new ResourceNotFoundException("Student 99 not found"));

        mockMvc.perform(get("/api/students/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Student 99 not found"));
    }
}
