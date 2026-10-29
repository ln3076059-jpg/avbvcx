package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.utils.DateUtil;
import com.pms.propertymanagement.dto.request.ServiceItemRequest;
import com.pms.propertymanagement.dto.response.ServiceItemResponse;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.ServiceItem;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.ServiceType;
import com.pms.propertymanagement.exception.ForbiddenException;
import com.pms.propertymanagement.exception.ResourceNotFoundException;
import com.pms.propertymanagement.repository.PropertyRepository;
import com.pms.propertymanagement.repository.ServiceItemRepository;
import com.pms.propertymanagement.service.ServiceItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceItemServiceImpl implements ServiceItemService {

    private final ServiceItemRepository serviceItemRepository;
    private final PropertyRepository propertyRepository;

    @Override
    public List<ServiceItemResponse> getAllServicesByOwner(User owner) {
        List<ServiceItem> services = serviceItemRepository.findByProperty_Owner_Username(owner.getUsername());

        return services.stream().map(s -> ServiceItemResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .price(s.getPrice())
                .unit(s.getUnit())
                .type(s.getType())
                .propertyName(s.getProperty().getName())
                .formattedCreatedAt(DateUtil.formatDateTime(s.getCreatedAt()))
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public List<ServiceItemResponse> getServicesByPropertyId(Long propertyId) {
        List<ServiceItem> services = serviceItemRepository.findByProperty_Id(propertyId);
        return services.stream().map(s -> ServiceItemResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .price(s.getPrice())
                .unit(s.getUnit())
                .type(s.getType())
                .propertyName(s.getProperty().getName())
                .formattedCreatedAt(DateUtil.formatDateTime(s.getCreatedAt()))
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public void createService(ServiceItemRequest request) {
        createService(request, null);
    }

    @Override
    public void createService(ServiceItemRequest request, User owner) {
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        if (owner != null && (property.getOwner() == null || !property.getOwner().getId().equals(owner.getId()))) {
            throw new ForbiddenException("Bạn không có quyền thêm dịch vụ cho nhà trọ này");
        }

        ServiceItem serviceItem = new ServiceItem();
        serviceItem.setName(request.getName());
        serviceItem.setPrice(request.getPrice());
        serviceItem.setUnit(request.getUnit());
        serviceItem.setType(request.getType());
        serviceItem.setProperty(property);

        serviceItemRepository.save(serviceItem);
    }

    @Override
    public void updateService(Long id, String name, Double price, String unit, String type, User owner) {
        ServiceItem serviceItem = serviceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ"));
        if (owner == null || serviceItem.getProperty() == null
                || serviceItem.getProperty().getOwner() == null
                || !serviceItem.getProperty().getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenException("Bạn không có quyền cập nhật dịch vụ này");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên dịch vụ không được để trống!");
        }
        if (price == null || price < 0) {
            throw new IllegalArgumentException("Đơn giá dịch vụ không hợp lệ!");
        }
        serviceItem.setName(name.trim());
        serviceItem.setPrice(price);
        if (unit != null && !unit.isBlank()) {
            serviceItem.setUnit(unit.trim());
        }
        if (type != null && !type.isBlank()) {
            serviceItem.setType(ServiceType.valueOf(type.trim().toUpperCase()));
        }
        serviceItemRepository.save(serviceItem);
    }

    @Override
    public void deleteService(Long id) {
        deleteService(id, null);
    }

    @Override
    public void deleteService(Long id, User owner) {
        ServiceItem serviceItem = serviceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service item not found"));
        if (owner != null && (serviceItem.getProperty() == null
                || serviceItem.getProperty().getOwner() == null
                || !serviceItem.getProperty().getOwner().getId().equals(owner.getId()))) {
            throw new ForbiddenException("Bạn không có quyền xóa dịch vụ này");
        }
        serviceItemRepository.delete(serviceItem);
    }
}
