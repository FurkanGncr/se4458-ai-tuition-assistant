package edu.se4458.tuition.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Guarantees that the demo passwords documented in V3__seed_users.sql (and the README) really work. */
class SeedUsersMigrationTest {

    private static final Map<String, String> DEMO_PASSWORDS = Map.of(
            "STUDENT", "Student123!",
            "BANK", "Bank123!",
            "ADMIN", "Admin123!");

    private static final Pattern ROW = Pattern.compile(
            "\\('([^']+)',\\s*'(\\$2a\\$10\\$[^']+)',\\s*'(STUDENT|BANK|ADMIN)',\\s*(?:'([^']+)'|NULL)\\)");

    private record SeedUser(String username, String hash, String role, String studentNo) {
    }

    @Test
    void everySeededUser_matchesItsDocumentedPassword() throws IOException {
        List<SeedUser> users = readSeedUsers();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        assertThat(users).extracting(SeedUser::username)
                .containsExactly("20230001", "20230002", "20230003", "20230004", "bank", "admin");
        for (SeedUser u : users) {
            assertThat(encoder.matches(DEMO_PASSWORDS.get(u.role()), u.hash()))
                    .as("password of %s", u.username()).isTrue();
        }
    }

    @Test
    void studentUsernames_areTheirStudentNumbers_andOtherRolesHaveNone() throws IOException {
        for (SeedUser u : readSeedUsers()) {
            if (u.role().equals("STUDENT")) {
                assertThat(u.studentNo()).isEqualTo(u.username());
            } else {
                assertThat(u.studentNo()).isNull();
            }
        }
    }

    private static List<SeedUser> readSeedUsers() throws IOException {
        String sql;
        try (InputStream in = SeedUsersMigrationTest.class.getResourceAsStream("/db/migration/V3__seed_users.sql")) {
            assertThat(in).as("V3__seed_users.sql on the classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<SeedUser> users = new ArrayList<>();
        Matcher m = ROW.matcher(sql);
        while (m.find()) {
            users.add(new SeedUser(m.group(1), m.group(2), m.group(3), m.group(4)));
        }
        return users;
    }
}
