package com.elyashevich.jwt_2fa_template.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

@Entity
@Getter
@Setter
@Builder
@FieldNameConstants
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserProfile {

    @Id
    @ToString.Include
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_id_seq_gen")
    @SequenceGenerator(name = "users_id_seq_gen", sequenceName = "users_id_seq", allocationSize = 50)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ToString.Include
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Default
    @Enumerated(EnumType.STRING)
    private UserLocale locale = UserLocale.RU;

    @ToString.Exclude
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    private Instant lastLoginAt;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_code")
    )
    private Set<Role> roles = new LinkedHashSet<>();

    @PrePersist
    public void manageStatus() {
        this.status = this.emailVerifiedAt == null ? UserStatus.VERIFICATION_REQUIRE : UserStatus.ACTIVE;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void addRole(Role role) {
        roles.add(role);
    }
}
