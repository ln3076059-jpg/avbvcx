package com.pms.propertymanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PropertyRequest {

    // === PROPERTY DATA (Real Estate Core) ===
    private String name; // Internal management name
    private Integer numberOfRooms;
    private Integer price;
    private Double acreage;
    private String addressNumber;

    // === POST DATA (Marketing Content) ===
    // NOTE: These fields are ONLY used for updating existing posts via property edit
    // When creating NEW properties, posts should be created separately via /owner/posts/create
    private String postTitle; // Marketing title for marketplace (edit only)
    private String postSlug; // SEO URL slug (edit only)
    private String postDescription; // Marketing description (edit only)

    // === RELATIONSHIPS ===
    // Các ID để mapping quan hệ
    private Long categoryId;
    private String wardCode;

    // Danh sách ID từ checkbox (Many-to-Many)
    private List<Long> amenityIds;
    private List<Long> surroundingIds;
    private List<Long> targetIds;

    // === IMAGES ===
    private List<String> imageUrls;
}
