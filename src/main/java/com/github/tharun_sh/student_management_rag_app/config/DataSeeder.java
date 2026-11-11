package com.github.tharun_sh.student_management_rag_app.config;

import com.github.tharun_sh.student_management_rag_app.entity.Course;
import com.github.tharun_sh.student_management_rag_app.entity.Student;
import com.github.tharun_sh.student_management_rag_app.repository.CourseRepository;
import com.github.tharun_sh.student_management_rag_app.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Inserts sample data once, when the database is empty. Runs before the RAG rebuild. */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StudentRepository students;
    private final CourseRepository courses;

    public DataSeeder(StudentRepository students, CourseRepository courses) {
        this.students = students;
        this.courses = courses;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (students.count() > 0 || courses.count() > 0) return;

        Course cs101 = course("CS101", "Intro to Programming", "Basics of programming with Java: variables, control flow, methods.", 4, "Dr. Meena");
        Course cs201 = course("CS201", "Data Structures", "Arrays, linked lists, trees, graphs and complexity analysis.", 4, "Dr. Meena");
        Course ma101 = course("MA101", "Calculus", "Limits, derivatives, integrals and series.", 3, "Dr. Iyer");
        Course ph101 = course("PH101", "Physics", "Mechanics, waves and thermodynamics.", 3, "Dr. Nair");
        Course ec101 = course("EC101", "Economics", "Introduction to micro and macroeconomics.", 3, "Dr. Sharma");

        Student[] list = {
            student("Ravi", "Kumar", "ravi.kumar@example.com", "Computer Science", 3, 3.8, "2004-05-12", cs201, ma101),
            student("Anita", "Rao", "anita.rao@example.com", "Computer Science", 3, 3.6, "2004-08-03", cs201, ma101),
            student("John", "Paul", "john.paul@example.com", "Computer Science", 2, 3.2, "2005-01-21", cs101, cs201),
            student("Priya", "Nair", "priya.nair@example.com", "Computer Science", 1, 3.9, "2006-02-14", cs101, ma101),
            student("Arjun", "Mehta", "arjun.mehta@example.com", "Physics", 2, 3.1, "2005-11-30", ph101, ma101),
            student("Sneha", "Iyer", "sneha.iyer@example.com", "Physics", 4, 3.7, "2003-03-09", ph101),
            student("Karthik", "Reddy", "karthik.reddy@example.com", "Mathematics", 3, 2.9, "2004-07-19", ma101, cs101),
            student("Divya", "Menon", "divya.menon@example.com", "Mathematics", 4, 3.5, "2003-12-25", ma101, ph101),
            student("Rahul", "Verma", "rahul.verma@example.com", "Economics", 2, 2.7, "2005-06-02", ec101, ma101),
            student("Meera", "Joshi", "meera.joshi@example.com", "Economics", 5, 3.4, "2002-09-17", ec101),
        };
        students.saveAll(java.util.List.of(list));
        log.info("Seeded {} courses and {} students", courses.count(), students.count());
    }

    private Course course(String code, String title, String description, int credits, String instructor) {
        Course c = new Course();
        c.setCode(code);
        c.setTitle(title);
        c.setDescription(description);
        c.setCredits(credits);
        c.setInstructor(instructor);
        return courses.save(c);
    }

    private Student student(String first, String last, String email, String dept, int year,
                            double gpa, String dob, Course... enrolled) {
        Student s = new Student();
        s.setFirstName(first);
        s.setLastName(last);
        s.setEmail(email);
        s.setDepartment(dept);
        s.setYearOfStudy(year);
        s.setGpa(gpa);
        s.setDateOfBirth(LocalDate.parse(dob));
        for (Course c : enrolled) s.addCourse(c);
        return s;
    }
}
