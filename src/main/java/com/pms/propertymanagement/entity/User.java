package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phone;
    
    @Column(name = "card_id")
    private String cardId;
    
    @Column(name = "dob")
    private String dob;
    
    @Column(name = "address", length = 500)
    private String address;
    
    @Column(name = "hometown", length = 500)
    private String hometown;
    
    @Column(name = "gender")
    private String gender;

    @Column(name = "bank_code", length = 50)
    private String bankCode; // VD: MB, VCB, TCB, ACB, VPB, TPB, BIDV, CTG...

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_name", length = 255)
    private String bankAccountName;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "is_ekyc_verified")
    private Boolean ekycVerified = false;

    @Column(name = "ekyc_verified_at")
    private LocalDateTime ekycVerifiedAt;

    public String getEffectiveBankCode() {
        return (bankCode != null && !bankCode.isBlank()) ? bankCode.trim().toUpperCase() : "MB";
    }

    public String getEffectiveBankAccountNumber() {
        if (bankAccountNumber != null && !bankAccountNumber.isBlank()) {
            return bankAccountNumber.trim();
        }
        return (phone != null && !phone.isBlank()) ? phone.trim() : "";
    }

    public String getEffectiveBankAccountName() {
        if (bankAccountName != null && !bankAccountName.isBlank()) {
            return bankAccountName.trim().toUpperCase();
        }
        return (fullName != null && !fullName.isBlank()) ? fullName.trim().toUpperCase() : "CHU NHA TRO";
    }

    @Column(name="create_at", updatable = false)
    private LocalDateTime createAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "owner")
    private Set<Property> properties = new HashSet<>();

    @OneToMany(mappedBy = "owner")
    private Set<Contact> contacts = new HashSet<>();


    @PrePersist
    protected void onCreate(){
        this.createAt = LocalDateTime.now();
    }

    public boolean isEkycVerified() {
        return Boolean.TRUE.equals(ekycVerified);
    }
}
