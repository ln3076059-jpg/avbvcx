package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.TenantRequest;
import com.pms.propertymanagement.dto.response.TenantResponse;
import com.pms.propertymanagement.entity.User;

import java.util.List;

public interface TenantService {
    List<TenantResponse> getAllTenantsByOwner(User owner);
    default List<TenantResponse> getTenantsByOwner(User owner) {
        return getAllTenantsByOwner(owner);
    }
    void createTenant(TenantRequest request, User owner);
    void updateTenant(Long id, TenantRequest request, User owner);
    void deleteTenant(Long id);
    void deleteTenant(Long id, Long ownerId);
}
