package com.pms.propertymanagement.dto.response;

import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.utils.DateUtil;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flattened view of Post + Property used by all public-facing Thymeleaf templates.
 * Replaces passing raw Post / Property entities to the view layer.
 */
@Getter
@Builder
public class PostPublicResponse {

    // ── Post fields ────────────────────────────────────────────────────────
    private Long id;            // Post ID
    private String title;       // Marketing title (from Post)
    private String slug;        // URL slug  (from Post)
    private String description; // Marketing description (from Post)
    private String formattedCreatedAt;

    // ── Property core fields ───────────────────────────────────────────────
    private int price;
    private double acreage;
    private int numberOfRooms;
    private String addressNumber;

    // ── Location ───────────────────────────────────────────────────────────
    private Long categoryId;
    private String categoryName;
    private String wardName;
    private String provinceName;

    // ── Images ────────────────────────────────────────────────────────────
    private String imageUrl;        // thumbnail (first image)
    private List<String> imageUrls; // all images for gallery

    // ── Owner ─────────────────────────────────────────────────────────────
    private String ownerName;
    private String ownerPhone;

    // ── Tags / Feature sets ───────────────────────────────────────────────
    private Set<Amenity>       amenities;
    private Set<Surrounding>   surroundings;
    private Set<TargetTenant>  targetTenants;

    // ── Factory ───────────────────────────────────────────────────────────

    private static final String[] FALLBACK_ROOM_IMAGES = {
            "/images/rooms/room-1.jpg",
            "/images/rooms/room-2.jpg",
            "/images/rooms/room-3.jpg",
            "/images/rooms/room-4.jpg",
            "/images/rooms/room-5.jpg",
            "/images/rooms/room-6.jpg",
            "/images/rooms/room-7.jpg",
            "/images/rooms/room-8.jpg",
            "/images/rooms/room-9.jpg",
            "/images/rooms/room-10.jpg",
            "/images/rooms/room-11.jpg",
            "/images/rooms/room-12.jpg"
    };

    private static boolean isUsableImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        if (trimmed.contains("picsum.photos")
                || trimmed.contains("photo-1502005229762")
                || trimmed.endsWith("/no-image.jpg")
                || trimmed.endsWith("/default.jpg")) {
            return false;
        }
        return true;
    }

    public static PostPublicResponse from(Post post) {
        Property prop = post.getProperty();

        java.util.List<String> urls = new java.util.ArrayList<>();
        if (prop.getImages() != null) {
            for (PropertyImage img : prop.getImages()) {
                if (img != null && isUsableImageUrl(img.getImageUrl())) {
                    urls.add(img.getImageUrl().trim());
                }
            }
        }

        int seed = Math.abs((prop.getId() != null ? prop.getId().intValue() : 1) * 3);
        int offset = 0;
        while (urls.size() < 4) {
            String fallback = FALLBACK_ROOM_IMAGES[(seed + offset) % FALLBACK_ROOM_IMAGES.length];
            if (!urls.contains(fallback) || offset >= FALLBACK_ROOM_IMAGES.length) {
                urls.add(fallback);
            }
            offset++;
        }

        String thumbnail = urls.get(0);

        String wardName     = (prop.getWard() != null) ? prop.getWard().getName() : "";
        String provinceName = (prop.getWard() != null && prop.getWard().getProvince() != null)
                ? prop.getWard().getProvince().getName() : "";

        String categoryName = (prop.getCategory() != null) ? prop.getCategory().getName() : "";
        Long   categoryId   = (prop.getCategory() != null) ? prop.getCategory().getId()   : null;

        String ownerName  = (prop.getOwner() != null) ? prop.getOwner().getFullName() : "";
        String ownerPhone = (prop.getOwner() != null) ? prop.getOwner().getPhone()    : "";

        return PostPublicResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .description(post.getDescription())
                .formattedCreatedAt(post.getCreatedAt() != null ? DateUtil.formatDateTime(post.getCreatedAt()) : "")
                .price(prop.getPrice())
                .acreage(prop.getAcreage())
                .numberOfRooms(prop.getNumberOfRooms())
                .addressNumber(prop.getAddressNumber())
                .categoryId(categoryId)
                .categoryName(categoryName)
                .wardName(wardName)
                .provinceName(provinceName)
                .imageUrl(thumbnail)
                .imageUrls(urls)
                .ownerName(ownerName)
                .ownerPhone(ownerPhone)
                .amenities(prop.getAmenities())
                .surroundings(prop.getSurroundings())
                .targetTenants(prop.getTargetTenants())
                .build();
    }
}
