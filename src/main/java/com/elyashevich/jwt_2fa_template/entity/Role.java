package com.elyashevich.jwt_2fa_template.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Table(name = "roles")
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Role {

    @Id
    @ToString.Include
    @EqualsAndHashCode.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 32)
    private RoleCode code;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    public String asAuthority() {
        return code.asAuthority();
    }
}
