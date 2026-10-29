package com.pms.propertymanagement.repository;

import com.pms.propertymanagement.entity.MeterReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    Optional<MeterReading> findByRoom_IdAndMonthAndYear(Long roomId, Integer month, Integer year);

    boolean existsByRoom_IdAndMonthAndYear(Long roomId, Integer month, Integer year);

    Optional<MeterReading> findTopByRoom_IdOrderByYearDescMonthDesc(Long roomId);

    List<MeterReading> findByRoom_IdOrderByYearDescMonthDesc(Long roomId);

    @Query("SELECT m FROM MeterReading m WHERE m.room.id = :roomId " +
           "AND (m.year < :year OR (m.year = :year AND m.month < :month)) " +
           "ORDER BY m.year DESC, m.month DESC")
    List<MeterReading> findPreviousReadings(@Param("roomId") Long roomId, @Param("month") Integer month, @Param("year") Integer year);

    default Optional<MeterReading> findLatestBefore(Long roomId, Integer month, Integer year) {
        return findPreviousReadings(roomId, month, year).stream().findFirst();
    }

    @Query("SELECT m FROM MeterReading m WHERE m.room.property.owner.id = :ownerId " +
           "AND (:propertyId IS NULL OR m.room.property.id = :propertyId) " +
           "AND (:roomId IS NULL OR m.room.id = :roomId) " +
           "AND (:month IS NULL OR m.month = :month) " +
           "AND (:year IS NULL OR m.year = :year) " +
           "ORDER BY m.year DESC, m.month DESC, m.room.name ASC")
    Page<MeterReading> searchReadings(
            @Param("ownerId") Long ownerId,
            @Param("propertyId") Long propertyId,
            @Param("roomId") Long roomId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );
}
