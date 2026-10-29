package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.TenantRequest;
import com.pms.propertymanagement.dto.response.TenantResponse;
import com.pms.propertymanagement.entity.Tenant;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.TenantRepository;
import com.pms.propertymanagement.service.TenantService;
import com.pms.propertymanagement.utils.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final com.pms.propertymanagement.repository.ContractRepository contractRepository;

    @Override
    public List<TenantResponse> getAllTenantsByOwner(User owner) {
        List<Tenant> tenants = tenantRepository.findByOwner_Id(owner.getId());

        return tenants.stream().map(t -> TenantResponse.builder()
                .id(t.getId())
                .fullName(t.getFullName())
                .phone(t.getPhone())
                .email(t.getEmail())
                .citizenId(t.getCitizenId())
                .gender(t.getGender())
                .birthday(t.getBirthday())
                .career(t.getCareer())
                .permanentAddress(t.getPermanentAddress())
                .formattedBirthday(t.getBirthday() != null ? DateUtil.formatDate(t.getBirthday()) : "")
                .formattedCreatedAt(DateUtil.formatDateTime(t.getCreatedAt()))
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public void createTenant(TenantRequest request, User owner) {
        if (request.getCitizenId() == null || request.getCitizenId().isBlank()) {
            throw new IllegalArgumentException("Căn cước công dân (CCCD) không được để trống!");
        }

        // Kiểm tra CCCD trùng
        if (tenantRepository.existsByCitizenId(request.getCitizenId().trim())) {
            throw new IllegalArgumentException("Căn cước công dân (CCCD) " + request.getCitizenId() + " đã tồn tại trong hệ thống!");
        }

        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống!");
        }

        Tenant tenant = new Tenant();
        tenant.setFullName(request.getFullName());
        tenant.setPhone(request.getPhone().trim());
        tenant.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        tenant.setCitizenId(request.getCitizenId().trim());
        tenant.setGender(request.getGender());
        tenant.setCareer(request.getCareer());
        tenant.setPermanentAddress(request.getPermanentAddress());
        tenant.setPlaceOfIssue(request.getPlaceOfIssue());
        tenant.setBirthday(request.getBirthday());
        tenant.setIssueDate(request.getIssueDate());
        
        tenant.setOwner(owner);

        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    public void updateTenant(Long id, TenantRequest request, User owner) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách thuê với ID: " + id));

        if (owner == null || tenant.getOwner() == null || !tenant.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác trên hồ sơ khách thuê này!");
        }

        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Họ và tên khách thuê không được để trống!");
        }

        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống!");
        }

        if (request.getCitizenId() == null || request.getCitizenId().isBlank()) {
            throw new IllegalArgumentException("Căn cước công dân (CCCD) không được để trống!");
        }

        String newCitizenId = request.getCitizenId().trim();
        if (!newCitizenId.equals(tenant.getCitizenId())) {
            boolean collision = tenantRepository.findByOwner_Id(owner.getId()).stream()
                    .anyMatch(t -> !t.getId().equals(id) && newCitizenId.equals(t.getCitizenId()));
            if (collision) {
                throw new IllegalArgumentException("Căn cước công dân (CCCD) " + newCitizenId + " đã tồn tại trong danh sách khách thuê của bạn!");
            }
        }

        tenant.setFullName(request.getFullName().trim());
        tenant.setPhone(request.getPhone().trim());
        tenant.setEmail(request.getEmail() != null && !request.getEmail().isBlank() ? request.getEmail().trim() : null);
        tenant.setCitizenId(newCitizenId);
        tenant.setGender(request.getGender());
        tenant.setBirthday(request.getBirthday());
        tenant.setPermanentAddress(request.getPermanentAddress());
        tenant.setCareer(request.getCareer());
        tenant.setUpdatedAt(LocalDateTime.now());

        tenantRepository.save(tenant);
    }

    @Override
    public void deleteTenant(Long id) {
        deleteTenant(id, null);
    }

    @Override
    public void deleteTenant(Long id, Long ownerId) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách thuê với ID: " + id));

        if (ownerId != null && (tenant.getOwner() == null || !tenant.getOwner().getId().equals(ownerId))) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác trên hồ sơ khách thuê này!");
        }

        if (contractRepository.countActiveContractsByTenantId(id) > 0) {
            throw new IllegalStateException("Không thể xóa khách thuê đang tham gia hợp đồng có hiệu lực!");
        }

        tenantRepository.delete(tenant);
    }
}
