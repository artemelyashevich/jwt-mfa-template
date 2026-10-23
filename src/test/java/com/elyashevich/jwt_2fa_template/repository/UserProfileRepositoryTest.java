package com.elyashevich.jwt_2fa_template.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.elyashevich.jwt_2fa_template.TestcontainersConfiguration;
import com.elyashevich.jwt_2fa_template.entity.Role;
import com.elyashevich.jwt_2fa_template.entity.RoleCode;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.repository.specification.UserProfileSpecifications;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

@DataJpaTest(properties = "ADMIN_PASSWORD_HASH=test-admin-password-hash")
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserProfileRepositoryTest {

    private static final Sort BY_EMAIL = Sort.by(UserProfile.Fields.email);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserProfileRepository userProfileRepository;

    @BeforeEach
    void setUp() {
        Role admin = entityManager.find(Role.class, RoleCode.ADMIN);
        UserProfile bob = user("Bob@Example.com");
        bob.addRole(admin);
        userProfileRepository.saveAll(List.of(user("alice@example.com"), bob, user("a_b@test.org"), user("axb@test.org")));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void emailContainsIsCaseInsensitiveAndSortedByEmail() {
        List<UserProfile> found = userProfileRepository.findAll(UserProfileSpecifications.emailContains("EXAMPLE"), BY_EMAIL);

        assertThat(found).extracting(UserProfile::getEmail).containsExactly("Bob@Example.com", "alice@example.com");
    }

    @Test
    void emailContainsMatchesLikeWildcardsLiterally() {
        List<UserProfile> found = userProfileRepository.findAll(UserProfileSpecifications.emailContains("a_b"), BY_EMAIL);

        assertThat(found).extracting(UserProfile::getEmail).containsExactly("a_b@test.org");
    }

    @Test
    void searchLoadsRolesEagerly() {
        List<UserProfile> found = userProfileRepository.findAll(UserProfileSpecifications.emailContains("bob@"), BY_EMAIL);
        entityManager.clear();

        assertThat(found).singleElement()
            .satisfies(user -> assertThat(user.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.ADMIN));
    }

    private static UserProfile user(String email) {
        return UserProfile.builder()
            .email(email)
            .name("Test")
            .passwordHash("hash")
            .emailVerifiedAt(Instant.now())
            .build();
    }
}
