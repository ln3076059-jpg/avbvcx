package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.MeterReadingRequest;
import com.pms.propertymanagement.entity.MeterReading;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.MeterReadingRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.service.impl.MeterReadingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeterReadingServiceTest {

    @Mock
    private MeterReadingRepository meterReadingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private com.pms.propertymanagement.repository.InvoiceRepository invoiceRepository;

    @InjectMocks
    private MeterReadingServiceImpl meterReadingService;

    private Room room;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setUsername("owner1");

        Property property = new Property();
        property.setId(100L);
        property.setOwner(user);

        room = new Room();
        room.setId(1L);
        room.setName("Phòng 101");
        room.setProperty(property);
    }

    @Test
    @DisplayName("Test nhập chỉ số hợp lệ: Tự động tính đúng mức tiêu thụ điện và nước")
    void testSaveReading_Success_CalculatesUsage() {
        MeterReadingRequest request = new MeterReadingRequest();
        request.setRoomId(1L);
        request.setMonth(3);
        request.setYear(2026);
        request.setOldElectricityIndex(100.0);
        request.setNewElectricityIndex(180.0);
        request.setOldWaterIndex(20.0);
        request.setNewWaterIndex(28.0);
        request.setElectricityPrice(3500.0);
        request.setWaterPrice(25000.0);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(meterReadingRepository.existsByRoom_IdAndMonthAndYear(1L, 3, 2026)).thenReturn(false);
        when(meterReadingRepository.save(any(MeterReading.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeterReading result = meterReadingService.saveReading(request, 10L);

        assertNotNull(result);
        assertEquals(80.0, result.getElectricityUsage(), 0.001);
        assertEquals(8.0, result.getWaterUsage(), 0.001);
        verify(meterReadingRepository, times(1)).save(any(MeterReading.class));
    }

    @Test
    @DisplayName("Test validation: Chặn khi chỉ số điện mới < chỉ số điện cũ")
    void testSaveReading_ThrowsException_WhenElectricityNewLessThanOld() {
        MeterReadingRequest request = new MeterReadingRequest();
        request.setRoomId(1L);
        request.setMonth(3);
        request.setYear(2026);
        request.setOldElectricityIndex(150.0);
        request.setNewElectricityIndex(120.0); // Không hợp lệ
        request.setOldWaterIndex(20.0);
        request.setNewWaterIndex(25.0);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            meterReadingService.saveReading(request, 10L);
        });

        assertTrue(ex.getMessage().contains("không được nhỏ hơn chỉ số điện cũ"));
        verify(meterReadingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Test validation: Chặn khi chỉ số nước mới < chỉ số nước cũ")
    void testSaveReading_ThrowsException_WhenWaterNewLessThanOld() {
        MeterReadingRequest request = new MeterReadingRequest();
        request.setRoomId(1L);
        request.setMonth(3);
        request.setYear(2026);
        request.setOldElectricityIndex(100.0);
        request.setNewElectricityIndex(150.0);
        request.setOldWaterIndex(30.0);
        request.setNewWaterIndex(22.0); // Không hợp lệ

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            meterReadingService.saveReading(request, 10L);
        });

        assertTrue(ex.getMessage().contains("không được nhỏ hơn chỉ số nước cũ"));
        verify(meterReadingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Test validation: Không cho tạo trùng chỉ số cùng phòng cùng tháng")
    void testSaveReading_ThrowsException_WhenDuplicate() {
        MeterReadingRequest request = new MeterReadingRequest();
        request.setRoomId(1L);
        request.setMonth(3);
        request.setYear(2026);
        request.setOldElectricityIndex(100.0);
        request.setNewElectricityIndex(150.0);
        request.setOldWaterIndex(20.0);
        request.setNewWaterIndex(25.0);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(meterReadingRepository.existsByRoom_IdAndMonthAndYear(1L, 3, 2026)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            meterReadingService.saveReading(request, 10L);
        });

        assertTrue(ex.getMessage().contains("đã có chỉ số điện nước"));
        verify(meterReadingRepository, never()).save(any());
    }
}
