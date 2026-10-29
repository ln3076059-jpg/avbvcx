package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.MeterReadingRequest;
import com.pms.propertymanagement.entity.MeterReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface MeterReadingService {

    Page<MeterReading> searchReadings(Long ownerId, Long propertyId, Long roomId, Integer month, Integer year, Pageable pageable);

    MeterReading getReadingById(Long id, Long ownerId);

    MeterReading saveReading(MeterReadingRequest request, Long ownerId);

    void deleteReading(Long id, Long ownerId);

    Optional<MeterReading> getLatestReading(Long roomId);

    Optional<MeterReading> getReadingByRoomAndMonthAndYear(Long roomId, Integer month, Integer year);
}
