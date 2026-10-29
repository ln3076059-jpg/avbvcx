package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.Role;
import com.pms.propertymanagement.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleInitializer {

    private final RoleRepository roleRepository;

    public void init() {
        // Hệ thống vận hành theo 3 vai trò chính:
        // 1. ADMIN  - Quản trị viên (Toàn quyền hệ thống)
        // 2. OWNER  - Chủ nhà trọ
        // 3. USER   - Khách hàng (Khách tìm phòng & Khách thuê)
        createIfNotExists("ADMIN");
        createIfNotExists("OWNER");
        createIfNotExists("USER");
        createIfNotExists("STAFF");
    }

    private void createIfNotExists(String name) {
        roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(name);
                    return roleRepository.save(role);
                });
    }
}
