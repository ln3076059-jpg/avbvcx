package com.pms.propertymanagement.dto.response;

import com.pms.propertymanagement.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String citizenId;
    private Gender gender;
    private LocalDate birthday;
    private String career;
    private String formattedBirthday;
    private String permanentAddress;
    private String formattedCreatedAt;
}
