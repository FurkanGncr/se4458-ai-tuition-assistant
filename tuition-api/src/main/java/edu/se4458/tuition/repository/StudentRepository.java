package edu.se4458.tuition.repository;

import edu.se4458.tuition.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, String> {

    /** Creates the student (without a name) if the number is unknown. Safe under concurrent requests. */
    @Modifying
    @Query(value = "INSERT INTO students (student_no) VALUES (:studentNo) ON CONFLICT (student_no) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("studentNo") String studentNo);
}
