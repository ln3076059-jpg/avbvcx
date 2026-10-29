package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.entity.MaintenanceRequest;
import com.pms.propertymanagement.entity.MaintenanceRequestLog;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.MaintenanceStatus;
import com.pms.propertymanagement.exception.ForbiddenException;
import com.pms.propertymanagement.exception.ResourceNotFoundException;
import com.pms.propertymanagement.repository.MaintenanceRequestLogRepository;
import com.pms.propertymanagement.repository.MaintenanceRequestRepository;
import com.pms.propertymanagement.repository.UserRepository;
import com.pms.propertymanagement.service.MaintenanceWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceWorkflowServiceImpl implements MaintenanceWorkflowService {
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final MaintenanceRequestLogRepository logRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceRequest> getRequestsForOwner(User owner) {
        if (owner == null) {
            throw new ForbiddenException("Bạn chưa đăng nhập");
        }
        return maintenanceRequestRepository.findByRoom_Property_Owner_IdOrderByCreatedAtDesc(owner.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceRequest getRequestDetailForOwner(Long id, User owner) {
        if (owner == null) {
            throw new ForbiddenException("Bạn chưa đăng nhập");
        }
        return maintenanceRequestRepository.findByIdAndRoom_Property_Owner_Id(id, owner.getId())
                .orElseThrow(() -> new ForbiddenException("Bạn không có quyền truy cập yêu cầu này"));
    }

    @Override
    @Transactional
    public MaintenanceRequest assignRequest(Long id, User owner, Long staffId) {
        MaintenanceRequest req = getRequestDetailForOwner(id, owner);
        if (req.getStatus() != MaintenanceStatus.PENDING && req.getStatus() != MaintenanceStatus.REOPENED) {
            throw new IllegalStateException("Chỉ phân công khi trạng thái là PENDING hoặc REOPENED");
        }
        if (staffId == null) {
            throw new IllegalArgumentException("Vui lòng chọn nhân viên");
        }
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên"));
        boolean isStaff = staff.getRoles().stream().anyMatch(r -> "STAFF".equals(r.getName()));
        if (!isStaff) {
            throw new IllegalArgumentException("Người được chọn không phải nhân viên kỹ thuật");
        }
        if (!staff.isActive()) {
            throw new IllegalStateException("Tài khoản nhân viên kỹ thuật đang bị khóa, không thể phân công!");
        }

        req.setStaff(staff);
        req.setAssignedAt(LocalDateTime.now());
        req.setStatus(MaintenanceStatus.ASSIGNED);
        req.setRejectedReason(null);
        req.setUpdatedAt(LocalDateTime.now());

        MaintenanceRequest saved = maintenanceRequestRepository.save(req);
        MaintenanceRequestLog log = new MaintenanceRequestLog();
        log.setRequest(saved);
        log.setActor(owner);
        log.setStatus(MaintenanceStatus.ASSIGNED);
        log.setNote("Owner phân công");
        logRepository.save(log);
        return saved;
    }

    @Override
    @Transactional
    public MaintenanceRequest rejectRequest(Long id, User owner, String reason) {
        MaintenanceRequest req = getRequestDetailForOwner(id, owner);
        if (req.getStatus() != MaintenanceStatus.PENDING && req.getStatus() != MaintenanceStatus.REOPENED) {
            throw new IllegalStateException("Chỉ từ chối khi trạng thái là PENDING hoặc REOPENED");
        }
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("Vui lòng nhập lý do từ chối");
        }
        req.setStatus(MaintenanceStatus.REJECTED);
        req.setRejectedReason(reason.trim());
        req.setStaff(null);
        req.setAssignedAt(null);
        req.setStartedAt(null);
        req.setCompletedAt(null);
        req.setUpdatedAt(LocalDateTime.now());

        MaintenanceRequest saved = maintenanceRequestRepository.save(req);
        MaintenanceRequestLog log = new MaintenanceRequestLog();
        log.setRequest(saved);
        log.setActor(owner);
        log.setStatus(MaintenanceStatus.REJECTED);
        log.setNote(reason.trim());
        logRepository.save(log);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceRequest> getRequestsForStaff(User staff) {
        if (staff == null) {
            throw new ForbiddenException("Bạn chưa đăng nhập");
        }
        boolean isAdmin = staff.getRoles() != null && staff.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        if (isAdmin) {
            return maintenanceRequestRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        }
        return maintenanceRequestRepository.findByStaff_IdOrderByCreatedAtDesc(staff.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceRequest getRequestDetailForStaff(Long id, User staff) {
        if (staff == null) {
            throw new ForbiddenException("Bạn chưa đăng nhập");
        }
        boolean isAdmin = staff.getRoles() != null && staff.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        if (isAdmin) {
            return maintenanceRequestRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu sửa chữa #" + id));
        }
        return maintenanceRequestRepository.findByIdAndStaff_Id(id, staff.getId())
                .orElseThrow(() -> new ForbiddenException("Bạn không có quyền truy cập yêu cầu này"));
    }

    @Override
    @Transactional
    public MaintenanceRequest startRequest(Long id, User staff) {
        MaintenanceRequest req = getRequestDetailForStaff(id, staff);
        if (req.getStatus() != MaintenanceStatus.ASSIGNED && req.getStatus() != MaintenanceStatus.REOPENED) {
            throw new IllegalStateException("Chỉ bắt đầu xử lý khi trạng thái là ASSIGNED hoặc REOPENED");
        }
        req.setStatus(MaintenanceStatus.IN_PROGRESS);
        req.setStartedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());

        MaintenanceRequest saved = maintenanceRequestRepository.save(req);
        MaintenanceRequestLog log = new MaintenanceRequestLog();
        log.setRequest(saved);
        log.setActor(staff);
        log.setStatus(MaintenanceStatus.IN_PROGRESS);
        log.setNote("Staff bắt đầu xử lý");
        logRepository.save(log);
        return saved;
    }

    @Override
    @Transactional
    public MaintenanceRequest completeRequest(Long id, User staff, String note) {
        MaintenanceRequest req = getRequestDetailForStaff(id, staff);
        if (req.getStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw new IllegalStateException("Chỉ hoàn thành khi trạng thái là IN_PROGRESS");
        }
        if (!StringUtils.hasText(note)) {
            throw new IllegalArgumentException("Vui lòng nhập ghi chú hoàn thành");
        }
        req.setStatus(MaintenanceStatus.COMPLETED);
        req.setCompletionNote(note.trim());
        req.setCompletedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());

        MaintenanceRequest saved = maintenanceRequestRepository.save(req);
        MaintenanceRequestLog log = new MaintenanceRequestLog();
        log.setRequest(saved);
        log.setActor(staff);
        log.setStatus(MaintenanceStatus.COMPLETED);
        log.setNote(note.trim());
        logRepository.save(log);
        return saved;
    }
}
