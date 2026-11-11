package com.github.tharun_sh.student_management_rag_app.repository;

import com.github.tharun_sh.student_management_rag_app.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    boolean existsByCode(String code);
    Optional<Course> findByCode(String code);

    @Query("select distinct c from Course c left join fetch c.students")
    List<Course> findAllWithStudents();

    @Query("select c from Course c left join fetch c.students where c.id = :id")
    Optional<Course> findByIdWithStudents(Long id);
}
