package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.dto.cache.CacheType;
import com.elyashevich.jwt_2fa_template.entity.Role;
import com.elyashevich.jwt_2fa_template.entity.RoleCode;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.repositroy.RoleRepository;
import com.elyashevich.jwt_2fa_template.service.CacheService;
import com.elyashevich.jwt_2fa_template.service.RoleService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final CacheService cacheService;

    @Override
    public Role findByRoleCode(RoleCode roleCode) {
        return cacheService.get(CacheType.ROLE, roleCode, () -> loadRoleFromDbByCode(roleCode)
            .orElseThrow(() -> new AppException(AppExceptionCode.EX404)));
    }

    private Optional<Role> loadRoleFromDbByCode(RoleCode roleCode) {
        return roleRepository.findById(roleCode);
    }
}
