package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.entity.Tenant;
import com.pms.propertymanagement.repository.TenantRepository;
import com.pms.propertymanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantInitializer {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @org.springframework.transaction.annotation.Transactional
    public void init(){
        Optional<User> ownerOpt =  userRepository.findByUsername("owner1");
        if(ownerOpt.isPresent()){
            User owner = ownerOpt.get();

            createTenantIfNotExist(owner, "Nguyễn Văn An", "0901234567", "nva@gmail.com", "/images/avatars/tenant1.jpg", com.pms.propertymanagement.enums.Gender.MALE, "Sinh viên", "079201001234", LocalDate.of(2021, 5, 20), "Cục CS QLHC về TTXH", LocalDate.of(2001, 8, 15), "123 Lê Lợi, Phường 1, TP Vũng Tàu");
            createTenantIfNotExist(owner, "Trần Thị Bích", "0912345678", "ttbich@company.com", "/images/avatars/tenant2.jpg", com.pms.propertymanagement.enums.Gender.FEMALE, "Nhân viên văn phòng", "079202005678", LocalDate.of(2022, 1, 15), "Cục CS QLHC về TTXH", LocalDate.of(1998, 12, 10), "45 Nguyễn Huệ, Quận 1, TP HCM");
            createTenantIfNotExist(owner, "Lê Hoàng Nam", "0987654321", "nam.le@freelance.net", "/images/avatars/tenant3.jpg", com.pms.propertymanagement.enums.Gender.MALE, "Freelancer IT", "001095009999", LocalDate.of(2020, 10, 10), "Công an TP Hà Nội", LocalDate.of(1995, 3, 22), "Thôn 3, Xã Thạch Hòa, Thạch Thất, Hà Nội");
            createTenantIfNotExist(owner, "Phạm Văn Dũng", "0933445566", null, "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400", com.pms.propertymanagement.enums.Gender.MALE, "Công nhân may", "072200112233", LocalDate.of(2019, 6, 1), "Công an Tỉnh Tây Ninh", LocalDate.of(2000, 1, 1), "Ấp 1, Xã Trảng Bàng, Tây Ninh");
            createTenantIfNotExist(owner, "Hoàng Thị Mai", "0369852147", "mai.hoang@edu.vn", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=400", com.pms.propertymanagement.enums.Gender.FEMALE, "Thực tập sinh Marketing", "038199008877", LocalDate.of(2023, 2, 28), "Cục CS QLHC về TTXH", LocalDate.of(2002, 11, 20), "Xóm 5, Xã Nghi Lộc, Nghệ An");
        }
    }

    private void createTenantIfNotExist(User owner, String fullName, String phone, String email, String avatar, com.pms.propertymanagement.enums.Gender gender, String career, String citizenId, LocalDate issueDate, String placeOfIssue, LocalDate birthday, String permanentAddress) {
        if (tenantRepository.existsByCitizenId(citizenId)) {
            return;
        }

        Tenant t = new Tenant();
        t.setFullName(fullName);
        t.setPhone(phone);
        t.setEmail(email);
        t.setAvatar(avatar);
        t.setGender(gender);
        t.setCareer(career);
        t.setCitizenId(citizenId);
        t.setIssueDate(issueDate);
        t.setPlaceOfIssue(placeOfIssue);
        t.setBirthday(birthday);
        t.setPermanentAddress(permanentAddress);
        t.setOwner(owner);
        tenantRepository.save(t);
    }
}
