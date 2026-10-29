package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.PropertyRequest;
import com.pms.propertymanagement.dto.response.IconResponse;
import com.pms.propertymanagement.dto.response.PropertyDetailResponse;
import com.pms.propertymanagement.dto.response.PropertyOwnerResponse;
import com.pms.propertymanagement.dto.response.PropertyResponse;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.exception.ForbiddenException;
import com.pms.propertymanagement.exception.ResourceNotFoundException;
import com.pms.propertymanagement.repository.*;
import com.pms.propertymanagement.service.PropertyManagementService;
import com.pms.propertymanagement.service.PropertyService;
import com.pms.propertymanagement.utils.DateUtil;
import com.pms.propertymanagement.utils.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final CategoryRepository categoryRepository;
    private final WardRepository wardRepository;
    private final AmenityRepository amenityRepository;
    private final SurroundingRepository surroundingRepository;
    private final TargetTenantsRepository targetRepository;
    private final ProvinceRepository provinceRepository;
    private final PropertyManagementService propertyManagementService;
    private final PostRepository postRepository;

    @Override
    public List<Amenity> getAllAmenities() {
        return amenityRepository.findAll();
    }

    @Override
    public List<Surrounding> getAllSurroundings() {
        return surroundingRepository.findAll();
    }

    @Override
    public List<TargetTenant> getAllTargetTenants() {
        return targetRepository.findAll();
    }

    @Override
    public List<Province> getAllProvinces() {
        return provinceRepository.findAll();
    }

    @Override
    public List<PropertyOwnerResponse> getPropertiesByOwner(User owner) {
        List<Property> properties = propertyRepository.findByOwnerUsername(owner.getUsername());

        return properties.stream()
                .map(p -> {
                    PropertyOwnerResponse dto = new PropertyOwnerResponse();
                    dto.setId(p.getId());
                    dto.setName(p.getName());
                    
                    // Get marketing title from Post (if exists)
                    postRepository.findByPropertyId(p.getId()).ifPresent(post -> {
                        dto.setTitle(post.getTitle());
                    });
                    
                    dto.setAddressNumber(p.getAddressNumber());

                    if (p.getCategory() != null) dto.setCategoryName(p.getCategory().getName());
                    if (p.getCreatedAt() != null) dto.setFormattedCreatedAt(DateUtil.formatDateTime(p.getCreatedAt()));

                    if (p.getImages() != null && !p.getImages().isEmpty()) {
                        dto.setImg_url(p.getImages().get(0).getImageUrl());
                    }

                    dto.setStatus(p.getStatus() != null ? p.getStatus().name() : "ACTIVE");
                    dto.setTotalRooms(p.getNumberOfRooms());

                    int rentedCount = 0;
                    if (p.getRooms() != null) {
                        rentedCount = (int) p.getRooms().stream()
                                .filter(r -> r.getStatus() == RoomStatus.RENTED)
                                .count();
                    }
                    dto.setRentedRooms(rentedCount);
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional
    public void createProperty(PropertyRequest request, User owner) {
        // NEW ARCHITECTURE: Check if user can create property based on management plan
        if (!propertyManagementService.canCreateProperty(owner.getId())) {
            throw new IllegalStateException("Bạn cần có gói quản lý đang hoạt động để tạo nhà trọ.");
        }

        Property property = new Property();

        // Set property real estate data only
        property.setName(request.getName());
        if (request.getNumberOfRooms() != null) property.setNumberOfRooms(request.getNumberOfRooms());
        if (request.getAcreage() != null) property.setAcreage(request.getAcreage());
        property.setAddressNumber(request.getAddressNumber());
        if (request.getPrice() != null) property.setPrice(request.getPrice());
        property.setOwner(owner);

        if (request.getCategoryId() != null) categoryRepository.findById(request.getCategoryId()).ifPresent(property::setCategory);
        if (request.getWardCode() != null && !request.getWardCode().isEmpty()) wardRepository.findById(request.getWardCode()).ifPresent(property::setWard);

        if (request.getAmenityIds() != null) {
            property.setAmenities(new HashSet<>(amenityRepository.findAllById(request.getAmenityIds())));
        }
        if (request.getSurroundingIds() != null) {
            property.setSurroundings(new HashSet<>(surroundingRepository.findAllById(request.getSurroundingIds())));
        }
        if (request.getTargetIds() != null) {
            property.setTargetTenants(new HashSet<>(targetRepository.findAllById(request.getTargetIds())));
        }

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            List<PropertyImage> images = new ArrayList<>();
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                PropertyImage img = new PropertyImage();
                img.setImageUrl(request.getImageUrls().get(i));
                img.setIsPrimary(i == 0);
                img.setProperty(property);
                images.add(img);
            }
            property.setImages(images);
        }

        // Save property only — post creation is handled separately via /owner/posts/create
        propertyRepository.save(property);
    }

    @Override
    public PropertyRequest getPropertyForEdit(Long id) {
        return getPropertyForEdit(id, null);
    }

    @Override
    public PropertyRequest getPropertyForEdit(Long id, Long ownerId) {
        Property p = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        if (ownerId != null && (p.getOwner() == null || !p.getOwner().getId().equals(ownerId))) {
            throw new ForbiddenException("Bạn không có quyền chỉnh sửa nhà trọ này!");
        }

        PropertyRequest req = new PropertyRequest();
        req.setName(p.getName());
        req.setNumberOfRooms(p.getNumberOfRooms());
        req.setPrice(p.getPrice());
        req.setAcreage(p.getAcreage());
        req.setAddressNumber(p.getAddressNumber());
        
        // Get marketing fields from Post
        postRepository.findByPropertyId(id).ifPresent(post -> {
            req.setPostTitle(post.getTitle());
            req.setPostSlug(post.getSlug());
            req.setPostDescription(post.getDescription());
        });

        if (p.getCategory() != null) req.setCategoryId(p.getCategory().getId());
        if (p.getWard() != null) req.setWardCode(p.getWard().getCode());

        req.setAmenityIds(p.getAmenities().stream().map(Amenity::getId).toList());
        req.setSurroundingIds(p.getSurroundings().stream().map(Surrounding::getId).toList());
        req.setTargetIds(p.getTargetTenants().stream().map(TargetTenant::getId).toList());

        if (p.getImages() != null) {
            req.setImageUrls(p.getImages().stream().map(PropertyImage::getImageUrl).toList());
        }
        return req;
    }

    @Override
    @Transactional
    public void updateProperty(Long id, PropertyRequest request) {
        updateProperty(id, request, null);
    }

    @Override
    @Transactional
    public void updateProperty(Long id, PropertyRequest request, Long ownerId) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        if (ownerId != null && (property.getOwner() == null || !property.getOwner().getId().equals(ownerId))) {
            throw new ForbiddenException("Bạn không có quyền cập nhật nhà trọ này!");
        }

        // Update property real estate data
        property.setName(request.getName());
        if (request.getNumberOfRooms() != null) property.setNumberOfRooms(request.getNumberOfRooms());
        if (request.getPrice() != null) property.setPrice(request.getPrice());
        if (request.getAcreage() != null) property.setAcreage(request.getAcreage());
        property.setAddressNumber(request.getAddressNumber());

        if (request.getCategoryId() != null) categoryRepository.findById(request.getCategoryId()).ifPresent(property::setCategory);
        if (request.getWardCode() != null && !request.getWardCode().isEmpty()) wardRepository.findById(request.getWardCode()).ifPresent(property::setWard);

        if (request.getAmenityIds() != null) {
            property.setAmenities(new HashSet<>(amenityRepository.findAllById(request.getAmenityIds())));
        }
        if (request.getSurroundingIds() != null) {
            property.setSurroundings(new HashSet<>(surroundingRepository.findAllById(request.getSurroundingIds())));
        }
        if (request.getTargetIds() != null) {
            property.setTargetTenants(new HashSet<>(targetRepository.findAllById(request.getTargetIds())));
        }

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            property.getImages().clear();
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                String url = request.getImageUrls().get(i);
                if (url != null && !url.trim().isEmpty()) {
                    PropertyImage img = new PropertyImage();
                    img.setImageUrl(url);
                    img.setIsPrimary(i == 0);
                    img.setProperty(property);
                    property.getImages().add(img);
                }
            }
        }
    
        propertyRepository.save(property);
        
        // NEW ARCHITECTURE: Update Post marketing fields separately
        updatePostMarketingFields(property.getId(), request);
    }
    
    /**
     * Update Post marketing fields when Property is updated
     */
    private void updatePostMarketingFields(Long propertyId, PropertyRequest request) {
        postRepository.findByPropertyId(propertyId).ifPresent(post -> {
            boolean changed = false;
            
            // Update title
            if (request.getPostTitle() != null && !request.getPostTitle().trim().isEmpty() 
                    && !request.getPostTitle().equals(post.getTitle())) {
                post.setTitle(request.getPostTitle());
                
                // Auto-regenerate slug if title changed and slug not explicitly provided
                if (request.getPostSlug() == null || request.getPostSlug().trim().isEmpty()) {
                    String newSlug = SlugUtil.makeSlug(request.getPostTitle() + "-" + propertyId);
                    if (!postRepository.existsBySlugExcludingId(newSlug, post.getId())) {
                        post.setSlug(newSlug);
                    }
                }
                changed = true;
            }
            
            // Update slug if explicitly provided
            if (request.getPostSlug() != null && !request.getPostSlug().trim().isEmpty() 
                    && !request.getPostSlug().equals(post.getSlug())) {
                // Check slug uniqueness
                if (!postRepository.existsBySlugExcludingId(request.getPostSlug(), post.getId())) {
                    post.setSlug(request.getPostSlug());
                    changed = true;
                } else {
                    log.warn("Slug {} already exists, skipping update", request.getPostSlug());
                }
            }
            
            // Update description
            if (request.getPostDescription() != null && !request.getPostDescription().trim().isEmpty()
                    && !request.getPostDescription().equals(post.getDescription())) {
                post.setDescription(request.getPostDescription());
                changed = true;
            }
            
            if (changed) {
                postRepository.save(post);
                log.info("Updated marketing fields for post {}", post.getId());
            }
        });
    }

    @Override
    public void deleteProperty(Long id) {
        deleteProperty(id, null);
    }

    @Override
    @Transactional
    public void deleteProperty(Long id, Long ownerId) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bất động sản có ID: " + id));

        if (ownerId != null && (property.getOwner() == null || !property.getOwner().getId().equals(ownerId))) {
            throw new IllegalArgumentException("Bạn không có quyền xóa bất động sản này!");
        }

        // Ràng buộc nghiệp vụ: Không cho phép xóa nhà trọ khi vẫn còn phòng đang có khách thuê
        if (property.getRooms() != null) {
            boolean hasRentedRoom = property.getRooms().stream()
                    .anyMatch(r -> r.getStatus() == RoomStatus.RENTED);
            if (hasRentedRoom) {
                throw new IllegalStateException("Không thể xóa nhà trọ khi vẫn còn phòng đang có người thuê!");
            }
        }

        propertyRepository.delete(property);
    }

    @Override
    public List<PropertyResponse> getPropertiesByCategory(Long categoryId) {
        List<Property> properties = propertyRepository.findByCategory_Id(categoryId);

        return properties.stream().map(p -> {
            PropertyResponse.PropertyResponseBuilder builder = PropertyResponse.builder()
                .id(p.getId())
                .price(p.getPrice())
                .categoryName(p.getCategory().getName())
                .acreage(p.getAcreage())
                .wardName(p.getWard().getName())
                .provinceName(p.getWard().getProvince().getName())
                .imageUrl(p.getImages().isEmpty() ? "/images/no-image.jpg" : p.getImages().get(0).getImageUrl());
            
            // NEW ARCHITECTURE: Get title/slug from Post
            postRepository.findByPropertyId(p.getId()).ifPresent(post -> {
                builder.title(post.getTitle());
                builder.slug(post.getSlug());
            });
            
            return builder.build();
        }).collect(Collectors.toList());
    }

    @Override
    public PropertyDetailResponse getPropertyDetailBySlug(String slug) {
        // NEW ARCHITECTURE: Slug belongs to Post, not Property
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài đăng!"));
        
        Property p = post.getProperty();
        if (p == null) {
            throw new ResourceNotFoundException("Property not found for this post");
        }

        return PropertyDetailResponse.builder()
                .id(p.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .price(p.getPrice())
                .description(post.getDescription())
                .addressNumber(p.getAddressNumber())
                .wardName(p.getWard().getName())
                .provinceName(p.getWard().getProvince().getName())
                .ownerName(p.getOwner().getFullName())
                .ownerPhone(p.getOwner().getPhone())
                .imageUrls(p.getImages().stream().map(PropertyImage::getImageUrl).toList())
                .amenities(p.getAmenities().stream().map(a -> new IconResponse(a.getName(), a.getIcon())).toList())
                .surroundings(p.getSurroundings().stream().map(s -> new IconResponse(s.getName(), s.getIcon())).toList())
                .targetTenants(p.getTargetTenants().stream().map(t -> new IconResponse(t.getName(), t.getIcon())).toList())
                .formattedCreatedAt(DateUtil.formatDateTime(p.getCreatedAt()))
                .categoryName(p.getCategory().getName())
                .acreage(p.getAcreage())
                .numberOfRooms(p.getNumberOfRooms())
                .build();
    }

    @Override
    public User getOwnerByPropertySlug(String propertySlug) {
        // NEW ARCHITECTURE: Slug belongs to Post
        return postRepository.findBySlug(propertySlug)
                .map(post -> post.getProperty().getOwner())
                .orElse(null);
    }
    
    @Override
    public String getProvinceCodeFromWard(String wardCode) {
        return wardRepository.findById(wardCode)
                .map(ward -> ward.getProvince().getCode())
                .orElse(null);
    }

    @Override
    public List<PropertyResponse> getAll() {
        return propertyRepository.findAll().stream().map(p -> {
            PropertyResponse.PropertyResponseBuilder builder = PropertyResponse.builder()
                .id(p.getId())
                .price(p.getPrice())
                .categoryName(p.getCategory().getName())
                .acreage(p.getAcreage())
                .wardName(p.getWard().getName())
                .provinceName(p.getWard().getProvince().getName())
                .imageUrl(p.getImages().isEmpty() ? "/images/default.jpg" : p.getImages().get(0).getImageUrl());
            
            // NEW ARCHITECTURE: Get title/slug from Post
            postRepository.findByPropertyId(p.getId()).ifPresent(post -> {
                builder.title(post.getTitle());
                builder.slug(post.getSlug());
            });
            
            return builder.build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<Property> getActivePropertiesWithoutPost(Long ownerId) {
        return propertyRepository.findActivePropertiesWithoutPost(ownerId);
    }
}
