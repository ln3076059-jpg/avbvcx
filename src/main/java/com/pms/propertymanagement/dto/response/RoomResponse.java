package com.pms.propertymanagement.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomResponse {
    private Long id;
    private String name;
    private Double price;
    private Double deposit;
    private Double area;
    private Integer maxOccupancy;
    private String description;
    private Long propertyId;
    private String propertyName;
    private String status;
    private String statusStyle; // class css for status badge
    private String formattedPrice;
}
