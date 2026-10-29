package com.pms.propertymanagement.dto.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertySlotUsageDTO {
    private Integer usedSlots;
    private Integer maxSlots;
    private Boolean isUnlimited;
    private List<PropertySlotDTO> activeSlots;
    private List<PropertySlotDTO> inactiveSlots;
    
    public Boolean hasAvailableSlot() {
        return isUnlimited || usedSlots < maxSlots;
    }
    
    public Integer getAvailableSlots() {
        return isUnlimited ? Integer.MAX_VALUE : (maxSlots - usedSlots);
    }
}