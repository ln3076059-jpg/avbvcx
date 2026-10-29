package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.ServiceItemRequest;
import com.pms.propertymanagement.dto.response.ServiceItemResponse;
import com.pms.propertymanagement.entity.User;

import java.util.List;

public interface ServiceItemService {
    List<ServiceItemResponse> getAllServicesByOwner(User owner);
    List<ServiceItemResponse> getServicesByPropertyId(Long propertyId);
    void createService(ServiceItemRequest request);
    void createService(ServiceItemRequest request, User owner);
    void updateService(Long id, String name, Double price, String unit, String type, User owner);
    void deleteService(Long id);
    void deleteService(Long id, User owner);
}
