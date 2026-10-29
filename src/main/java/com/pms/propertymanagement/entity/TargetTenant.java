package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;


@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "target_tenants")
public class TargetTenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private String icon;

    @ManyToMany(mappedBy = "targetTenants")
    private Set<Property> properties_ = new HashSet<>();

    public TargetTenant(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }
}
