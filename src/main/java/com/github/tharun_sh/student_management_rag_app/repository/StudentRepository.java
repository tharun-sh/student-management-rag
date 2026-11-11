package com.github.tharun_sh.student_management_rag_app.repository;

import com.github.tharun_sh.student_management_rag_app.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    boolean existsByEmail(String email);
    Optional<Student> findByEmail(String email);
    List<Student> findByDepartmentIgnoreCase(String department);

    @Query("select distinct s from Student s left join fetch s.courses")
    List<Student> findAllWithCourses();

    @Query("select s from Student s left join fetch s.courses where s.id = :id")
    Optional<Student> findByIdWithCourses(Long id);
}
