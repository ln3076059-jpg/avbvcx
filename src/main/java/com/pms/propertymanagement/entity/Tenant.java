package com.pms.propertymanagement.entity;

import com.pms.propertymanagement.enums.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_owner_citizen", columnNames = {"owner_id", "citizen_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Thông tin liên lạc cơ bản ---
    @Column(nullable = false, length = 255)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String avatar; // Ảnh đại diện hoặc ảnh chụp chân dung

    // --- Thông tin định danh chi tiết ---
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 255)
    private String career; // Nghề nghiệp (Sinh viên, NVVP...)

    @Column(name = "citizen_id", nullable = false, length = 20)
    private String citizenId; // Căn cước công dân (CCCD/CMND)

    @Column(name = "issue_date")
    private LocalDate issueDate; // Ngày cấp

    @Column(length = 255)
    private String placeOfIssue; // Nơi cấp

    private LocalDate birthday; // Ngày sinh

    @Column(length = 500)
    private String permanentAddress; // Địa chỉ thường trú (theo CCCD)

    // --- Quan hệ ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner; // Khách này thuộc danh sách quản lý của Chủ trọ nào

    // Liên kết khóa ngoại trực tiếp với tài khoản hệ thống (users.id) của cư dân
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private User account;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
