package edu.se4458.tuition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A login account. A {@link Role#STUDENT} account is linked to exactly one student number; other roles have none
 * (enforced by {@code ck_users_student_link}). Passwords are stored as BCrypt hashes only.
 */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "student_no", length = 20)
    private String studentNo;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, Role role, String studentNo) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.studentNo = studentNo;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    /** The linked student number, or {@code null} for BANK and ADMIN accounts. */
    public String getStudentNo() {
        return studentNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
