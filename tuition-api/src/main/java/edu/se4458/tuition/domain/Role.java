package edu.se4458.tuition.domain;

/** Login roles. Stored as text in {@code users.role}. */
public enum Role {
    /** A student; may only read and pay their own tuition. */
    STUDENT,
    /** The banking app; may query and pay for any student. */
    BANK,
    /** The university admin web site; may use the admin endpoints. */
    ADMIN
}
