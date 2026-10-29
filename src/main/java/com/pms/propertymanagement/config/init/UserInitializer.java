package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.RoleRepository;
import com.pms.propertymanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserInitializer {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public void init() {
        createAdmin();
        createOwner1();
        createOwner2();
        createOwner3();
        createOwner4();
        createTenant1();
        createTenant2();
        createTenant3();
        createStaff1();
    }

    private void createAdmin() {
        var existing = userRepository.findByUsername("admin");
        User admin;
        if (existing.isPresent()) {
            admin = existing.get();
        } else {
            admin = new User();
            admin.setUsername("admin");
            admin.setPassword("123");
            admin.setFullName("Quản trị viên hệ thống");
            admin.setPhone("0999999999");
            admin.setEmail("admin@tromoi.com");
            admin.setEkycVerified(true);
            admin = userRepository.save(admin);
        }

        // Admin có toàn quyền (Full quyền) trên hệ thống
        ensureRole(admin, "ADMIN");
        ensureRole(admin, "OWNER");
        ensureRole(admin, "USER");
    }

    private void createOwner1() {
        var existing = userRepository.findByUsername("owner1");
        if (existing.isPresent()) {
            ensureRole(existing.get(), "OWNER");
            return;
        }

        User owner = new User();
        owner.setUsername("owner1");
        owner.setPassword("123");
        owner.setFullName("owner1");
        owner.setPhone("0975893248");
        owner.setEmail("don@gmail.com");
        owner.setEkycVerified(true);
        owner.getRoles().add(roleRepository.findByName("OWNER").get());

        userRepository.save(owner);
    }

    private void createTenant1() {
        var existing = userRepository.findByUsername("tenant1");
        if (existing.isPresent()) {
            ensureRole(existing.get(), "USER");
            return;
        }

        User tenant = new User();
        tenant.setUsername("tenant1");
        tenant.setPassword("123");
        tenant.setFullName("Nguyễn Văn An");
        tenant.setPhone("0901234567");
        tenant.setEmail("nva@gmail.com");
        tenant.setEkycVerified(true);
        tenant.getRoles().add(roleRepository.findByName("USER").get());

        userRepository.save(tenant);
    }

    private void createTenant2() {
        var existing = userRepository.findByUsername("tenant2");
        if (existing.isPresent()) {
            ensureRole(existing.get(), "USER");
            return;
        }

        User tenant = new User();
        tenant.setUsername("tenant2");
        tenant.setPassword("123");
        tenant.setFullName("Trần Thị Bích");
        tenant.setPhone("0912345678");
        tenant.setEmail("ttbich@company.com");
        tenant.setEkycVerified(true);
        tenant.getRoles().add(roleRepository.findByName("USER").get());

        userRepository.save(tenant);
    }

    private void createTenant3() {
        var existing = userRepository.findByUsername("tenant3");
        if (existing.isPresent()) {
            ensureRole(existing.get(), "USER");
            return;
        }

        User tenant = new User();
        tenant.setUsername("tenant3");
        tenant.setPassword("123");
        tenant.setFullName("Lê Hoàng Nam");
        tenant.setPhone("0987654321");
        tenant.setEmail("nam.le@freelance.net");
        tenant.setEkycVerified(true);
        tenant.getRoles().add(roleRepository.findByName("USER").get());

        userRepository.save(tenant);
    }

    private void ensureRole(User user, String roleName) {
        boolean hasRole = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> roleName.equals(r.getName()));
        if (hasRole) return;
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        userRepository.save(user);
    }

    private void createOwner2() {
        if (userRepository.findByUsername("owner2").isPresent()) return;

        User owner = new User();
        owner.setUsername("owner2");
        owner.setPassword("123");
        owner.setFullName("owner2");
        owner.setPhone("0909090909");
        owner.setEmail("kiet@gmail.com");
        owner.setEkycVerified(true);
        owner.getRoles().add(roleRepository.findByName("OWNER").get());

        userRepository.save(owner);
    }

    private void createOwner3() {
        if (userRepository.findByUsername("owner3").isPresent()) return;

        User owner = new User();
        owner.setUsername("owner3");
        owner.setPassword("123");
        owner.setFullName("owner3");
        owner.setPhone("0707070707");
        owner.setEmail("thach@gmail.com");
        owner.setEkycVerified(true);
        owner.getRoles().add(roleRepository.findByName("OWNER").get());

        userRepository.save(owner);
    }

    private void createOwner4() {
        if (userRepository.findByUsername("owner4").isPresent()) return;

        User owner = new User();
        owner.setUsername("owner4");
        owner.setPassword("123");
        owner.setFullName("owner4");
        owner.setPhone("0808080808");
        owner.setEmail("an@gmail.com");
        owner.setEkycVerified(true);
        owner.getRoles().add(roleRepository.findByName("OWNER").get());

        userRepository.save(owner);
    }

    private void createStaff1() {
        var existing = userRepository.findByUsername("staff1");
        if (existing.isPresent()) {
            ensureRole(existing.get(), "STAFF");
            return;
        }

        User staff = new User();
        staff.setUsername("staff1");
        staff.setPassword("123");
        staff.setFullName("Kỹ thuật viên 1");
        staff.setPhone("0981112233");
        staff.setEmail("staff1@tromoi.com");
        staff.setEkycVerified(true);
        staff.getRoles().add(roleRepository.findByName("STAFF").get());

        userRepository.save(staff);
    }
}
