package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.entity.Role;
import com.elyashevich.jwt_2fa_template.entity.RoleCode;

public interface RoleService {
    Role findByRoleCode(RoleCode roleCode);
}
