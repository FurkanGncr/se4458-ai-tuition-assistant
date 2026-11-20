package edu.se4458.tuition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A student, identified by student number. Unknown numbers are created without a name by Add Tuition. */
@Entity
@Table(name = "students")
public class Student {

    @Id
    @Column(name = "student_no", length = 20)
    private String studentNo;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Student() {
    }

    public Student(String studentNo, String fullName) {
        this.studentNo = studentNo;
        this.fullName = fullName;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public String getFullName() {
        return fullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
