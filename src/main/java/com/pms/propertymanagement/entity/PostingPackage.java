package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "posting_packages")
@Getter
@Setter
public class PostingPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code; // POST_NEW

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price; // VND

    @Column(nullable = false)
    private int usageLimit; // Duration in days for post packages

    @Column(nullable = false)
    private int freeBoosts = 0; // Number of free boost uses

    @Column(nullable = false)
    private boolean hasVipBadge = false; // VIP badge feature

    @Column(nullable = false)
    private boolean hasSearchPriority = false; // Search priority feature

    @Column(nullable = false)
    private boolean isRecommended = false; // Recommended badge

    @Column(nullable = false)
    private boolean isActive = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}