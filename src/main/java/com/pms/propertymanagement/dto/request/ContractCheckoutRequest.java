package com.pms.propertymanagement.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractCheckoutRequest {
    private Long contractId;
    
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkoutDate;
    
    // Chỉ số điện nước chốt ngày trả phòng
    private Double finalElectricityIndex;
    private Double finalWaterIndex;
    private Double utilityAmount = 0.0;
    
    // Tiền phòng lẻ ngày (nếu trả giữa tháng)
    private Double unpaidRentAmount = 0.0;
    
    // Hư hại tài sản & chi phí phạt/sửa chữa
    private Double damageAmount = 0.0;
    private String damageNote;
    
    // Cọc & quyết toán
    private Double deposit = 0.0;
    private Double totalDeduction = 0.0;
    private Double refundAmount = 0.0; // Hoàn cọc (nếu > 0)
    private Double additionalCharge = 0.0; // Thu thêm (nếu cọc ko đủ)
    
    private String finalNote;
    private Boolean markRoomForMaintenance = false;
}
