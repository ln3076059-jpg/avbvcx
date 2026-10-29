package com.pms.propertymanagement.mapper;

import com.pms.propertymanagement.dto.ManagementPlanDTO;
import com.pms.propertymanagement.entity.ManagementPlan;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ManagementPlanMapper {

    public ManagementPlanDTO toDTO(ManagementPlan entity) {
        if (entity == null) {
            return null;
        }

        ManagementPlanDTO dto = new ManagementPlanDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setMonthlyPrice(entity.getMonthlyPrice());
        dto.setMaxProperties(entity.getMaxProperties());
        dto.setMaxPosts(entity.getMaxPosts());
        dto.setMaxRoomsPerProperty(entity.getMaxRoomsPerProperty());
        dto.setPostDurationDays(entity.getPostDurationDays());
        dto.setHasContracts(entity.isHasContracts());
        dto.setHasInvoices(entity.isHasInvoices());
        dto.setHasAdvancedReports(entity.isHasAdvancedReports());
        dto.setHasAutoReminders(entity.isHasAutoReminders());
        dto.setHasStaffManagement(entity.isHasStaffManagement());
        dto.setHasApiAccess(entity.isHasApiAccess());
        dto.setHasExcelExport(entity.isHasExcelExport());
        dto.setIsActive(entity.isActive());
        
        // Set computed properties
        dto.setIsRecommended("PREMIUM".equals(entity.getCode()));
        
        return dto;
    }

    public ManagementPlan toEntity(ManagementPlanDTO dto) {
        if (dto == null) {
            return null;
        }

        ManagementPlan entity = new ManagementPlan();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setMonthlyPrice(dto.getMonthlyPrice());
        entity.setMaxProperties(dto.getMaxProperties());
        entity.setMaxRoomsPerProperty(dto.getMaxRoomsPerProperty());
        entity.setHasContracts(dto.getHasContracts());
        entity.setHasInvoices(dto.getHasInvoices());
        entity.setHasAdvancedReports(dto.getHasAdvancedReports());
        entity.setHasAutoReminders(dto.getHasAutoReminders());
        entity.setHasStaffManagement(dto.getHasStaffManagement());
        entity.setHasApiAccess(dto.getHasApiAccess());
        entity.setHasExcelExport(dto.getHasExcelExport());
        entity.setActive(dto.getIsActive());
        
        return entity;
    }

    public List<ManagementPlanDTO> toDTOList(List<ManagementPlan> entities) {
        if (entities == null) {
            return null;
        }
        
        return entities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
