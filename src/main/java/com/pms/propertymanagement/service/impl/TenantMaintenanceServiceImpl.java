package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.response.CloudinaryResponse;
import com.pms.propertymanagement.entity.MaintenanceRequest;
import com.pms.propertymanagement.entity.MaintenanceRequestLog;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.Tenant;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.MaintenanceCategory;
import com.pms.propertymanagement.enums.MaintenanceStatus;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.repository.ContractRepository;
import com.pms.propertymanagement.repository.MaintenanceRequestLogRepository;
import com.pms.propertymanagement.repository.MaintenanceRequestRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.repository.TenantRepository;
import com.pms.propertymanagement.service.CloudinaryService;
import com.pms.propertymanagement.service.TenantMaintenanceService;
import lombok.RequiredArgsConstructor;
import com.pms.propertymanagement.exception.ForbiddenException;
import com.pms.propertymanagement.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantMaintenanceServiceImpl implements TenantMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(TenantMaintenanceServiceImpl.class);

    private final RoomRepository roomRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final MaintenanceRequestLogRepository logRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    public List<Room> getRoomsForTenant(User tenant) {
        if (tenant == null) return List.of();
        Optional<Tenant> tenantProfile = findTenantProfile(tenant);
        Long tenantId = tenantProfile.map(Tenant::getId).orElse(null);
        String phone = tenant.getPhone() != null ? tenant.getPhone().trim() : null;
        if (tenantId == null && (phone == null || phone.isBlank())) {
            return List.of();
        }
        return contractRepository.findActiveRoomsByTenantIdOrPhone(tenantId, phone);
    }

    @Override
    public List<Room> getAvailableRooms() {
        return roomRepository.findByStatus(RoomStatus.AVAILABLE);
    }

    @Override
    public Room getRoomDetail(Long roomId) {
        return roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    @Override
    public boolean isRoomRentedByTenant(Long roomId, User tenant) {
        if (tenant == null || roomId == null) {
            return false;
        }
        Optional<Tenant> tenantProfile = findTenantProfile(tenant);
        Long tenantId = tenantProfile.map(Tenant::getId).orElse(null);
        String phone = tenant.getPhone() != null ? tenant.getPhone().trim() : null;
        if (tenantId == null && (phone == null || phone.isBlank())) {
            return false;
        }
        return contractRepository.countActiveContractsByTenantOrPhoneAndRoomId(tenantId, phone, roomId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceRequest> getRequestsForTenant(User tenant) {
        return maintenanceRequestRepository.findByTenant_IdOrderByCreatedAtDesc(tenant.getId());
    }

    @Override
    @Transactional
    public MaintenanceRequest createRequest(Long roomId, User tenant, MaintenanceCategory category,
            String description, MultipartFile image) {
        if (tenant == null) {
            throw new IllegalArgumentException("Bạn chưa đăng nhập");
        }
        if (roomId == null) {
            throw new IllegalArgumentException("Phòng không hợp lệ");
        }
        if (category == null || !StringUtils.hasText(description)) {
            throw new IllegalArgumentException("Danh mục và mô tả không được trống");
        }
        if (!isRoomRentedByTenant(roomId, tenant)) {
            throw new ForbiddenException("Bạn chỉ có thể tạo yêu cầu cho phòng đang thuê");
        }
        if (hasUnresolvedRequestForRoom(roomId)) {
            throw new IllegalStateException("Phòng này hiện đã có tối đa 3 yêu cầu đang chờ xử lý. Vui lòng liên hệ trực tiếp chủ trọ nếu có sự cố khẩn cấp!");
        }
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        MaintenanceRequest req = new MaintenanceRequest();
        req.setCode(generateRequestCode());
        req.setRoom(room);
        req.setTenant(tenant);
        req.setCategory(category);
        req.setDescription(description);
        req.setStatus(MaintenanceStatus.PENDING);
        req.setCreatedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());

        // Upload ảnh lên Cloudinary nếu có
        if (image != null && !image.isEmpty()) {
            try {
                CloudinaryResponse uploaded = cloudinaryService.uploadImage(image, "maintenance");
                req.setImageUrl(uploaded.getUrl());
                req.setImagePublicId(uploaded.getPublicId());
                log.info("[Maintenance] Upload ảnh thành công: {}", uploaded.getUrl());
            } catch (Exception e) {
                log.error("[Maintenance] Upload ảnh thất bại, yêu cầu vẫn được lưu không có ảnh", e);
            }
        }

        MaintenanceRequest saved = maintenanceRequestRepository.save(req);

        MaintenanceRequestLog log = new MaintenanceRequestLog();
        log.setRequest(saved);
        log.setActor(tenant);
        log.setStatus(MaintenanceStatus.PENDING);
        log.setNote("Tenant tạo yêu cầu");
        logRepository.save(log);

        return saved;
    }

    private String generateRequestCode() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "MR-" + ts + "-" + rand;
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceRequest getTenantRequestDetail(Long id, User tenant) {
        return maintenanceRequestRepository.findByIdAndTenant_Id(id, tenant.getId())
                .orElseThrow(() -> new ForbiddenException("Bạn không có quyền truy cập yêu cầu này"));
    }

    @Override
    @Transactional
    public MaintenanceRequest confirmCompletion(Long id, User tenant) {
        MaintenanceRequest req = getTenantRequestDetail(id, tenant);
        if (req.getStatus() != MaintenanceStatus.COMPLETED) {
            throw new IllegalStateException("Chỉ xác nhận khi trạng thái là COMPLETED");
        }
        req.setStatus(MaintenanceStatus.CONFIRMED);
        req.setUpdatedAt(LocalDateTime.now());
        MaintenanceRequest saved = maintenanceRequestRepository.save(req);

        MaintenanceRequestLog log = new MaintenanceRequestLog();
        log.setRequest(saved);
        log.setActor(tenant);
        log.setStatus(MaintenanceStatus.CONFIRMED);
        log.setNote("Tenant xác nhận hoàn thành");
        logRepository.save(log);

        return saved;
    }

    @Override
    @Transactional
    public MaintenanceRequest reopenRequest(Long id, String reason, User tenant) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("Lý do không được trống");
        }
        MaintenanceRequest req = getTenantRequestDetail(id, tenant);
        if (req.getStatus() != MaintenanceStatus.COMPLETED) {
            throw new IllegalStateException("Chỉ mở lại khi trạng thái là COMPLETED");
        }
        if (countReopens(id) >= 3) {
            throw new IllegalStateException("Bạn đã yêu cầu làm lại quá số lần cho phép, vui lòng liên hệ trực tiếp Owner");
        }
        req.setStatus(MaintenanceStatus.REOPENED);
        req.setReopenedReason(reason);
        req.setUpdatedAt(LocalDateTime.now());
        MaintenanceRequest saved = maintenanceRequestRepository.save(req);

        MaintenanceRequestLog log = new MaintenanceRequestLog();
        log.setRequest(saved);
        log.setActor(tenant);
        log.setStatus(MaintenanceStatus.REOPENED);
        log.setNote(reason);
        logRepository.save(log);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUnresolvedRequestForRoom(Long roomId) {
        if (roomId == null) {
            return false;
        }
        List<MaintenanceStatus> statuses = List.of(
                MaintenanceStatus.PENDING,
                MaintenanceStatus.ASSIGNED,
                MaintenanceStatus.IN_PROGRESS,
                MaintenanceStatus.REOPENED
        );
        // Cho phép tối đa 3 yêu cầu chưa xử lý cùng lúc để tránh chặn các sự cố khẩn cấp phát sinh
        return maintenanceRequestRepository.countByRoom_IdAndStatusIn(roomId, statuses) >= 3;
    }

    @Override
    @Transactional(readOnly = true)
    public long countReopens(Long requestId) {
        if (requestId == null) {
            return 0;
        }
        return logRepository.countByRequest_IdAndStatus(requestId, MaintenanceStatus.REOPENED);
    }

    private Optional<Tenant> findTenantProfile(User tenantUser) {
        if (tenantUser == null) {
            return Optional.empty();
        }
        if (tenantUser.getId() != null) {
            Optional<Tenant> byAccount = tenantRepository.findFirstByAccount_Id(tenantUser.getId());
            if (byAccount.isPresent()) {
                return byAccount;
            }
        }
        if (StringUtils.hasText(tenantUser.getPhone())) {
            Optional<Tenant> byPhone = tenantRepository.findFirstByPhone(tenantUser.getPhone().trim());
            if (byPhone.isPresent()) {
                Tenant t = byPhone.get();
                if (t.getAccount() == null && tenantUser.getId() != null) {
                    t.setAccount(tenantUser);
                    tenantRepository.save(t);
                }
                return Optional.of(t);
            }
        }
        if (StringUtils.hasText(tenantUser.getEmail())) {
            Optional<Tenant> byEmail = tenantRepository.findFirstByEmail(tenantUser.getEmail().trim());
            if (byEmail.isPresent()) {
                Tenant t = byEmail.get();
                if (t.getAccount() == null && tenantUser.getId() != null) {
                    t.setAccount(tenantUser);
                    tenantRepository.save(t);
                }
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
