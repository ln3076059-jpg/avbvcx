package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.InvoiceRequest;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.repository.*;
import com.pms.propertymanagement.service.impl.InvoiceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private MeterReadingRepository meterReadingRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private User owner;
    private Property property;
    private Room room;
    private Contract contract;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(10L);

        property = new Property();
        property.setId(100L);
        property.setOwner(owner);

        room = new Room();
        room.setId(1L);
        room.setName("Phòng 101");
        room.setProperty(property);

        tenant = new Tenant();
        tenant.setId(20L);
        tenant.setFullName("Nguyễn Văn An");

        contract = new Contract();
        contract.setId(200L);
        contract.setRoom(room);
        contract.setRepresentative(tenant);
        contract.setRentPrice(3500000.0);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setServices(new HashSet<>());
    }

    @Test
    @DisplayName("Test lập hóa đơn thành công: Tự động cộng tổng tiền phòng + điện + nước + dịch vụ và khởi tạo remainingAmount")
    void testCreateInvoice_Success_CalculatesTotal() {
        InvoiceRequest request = new InvoiceRequest();
        request.setRoomId(1L);
        request.setMonth(3);
        request.setYear(2026);
        request.setRoomPrice(3500000.0);
        request.setDueDate(LocalDate.now().plusDays(5));

        MeterReading reading = new MeterReading();
        reading.setElectricityUsage(100.0); // 100 * 3500 = 350,000
        reading.setWaterUsage(5.0);         // 5 * 20000 = 100,000
        reading.setElectricityPrice(3500.0);
        reading.setWaterPrice(20000.0);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(contractRepository.findFirstByRoom_IdAndStatus(1L, ContractStatus.ACTIVE)).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByRoom_IdAndMonthAndYear(1L, 3, 2026)).thenReturn(false);
        when(meterReadingRepository.findByRoom_IdAndMonthAndYear(1L, 3, 2026)).thenReturn(Optional.of(reading));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = invoiceService.createInvoice(request, 10L);

        assertNotNull(invoice);
        assertEquals(3500000.0, invoice.getRoomPrice());
        assertEquals(350000.0, invoice.getElectricityAmount());
        assertEquals(100000.0, invoice.getWaterAmount());
        // Tổng = 3,500,000 + 350,000 + 100,000 = 3,950,000
        assertEquals(3950000.0, invoice.getTotalAmount());
        assertEquals(3950000.0, invoice.getRemainingAmount());
        assertEquals(0.0, invoice.getPaidAmount());
        assertEquals(InvoiceStatus.UNPAID, invoice.getStatus());

        verify(invoiceRepository, times(1)).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Test validation: Chặn lập hóa đơn trùng phòng và cùng tháng/năm")
    void testCreateInvoice_ThrowsException_WhenDuplicate() {
        InvoiceRequest request = new InvoiceRequest();
        request.setRoomId(1L);
        request.setMonth(3);
        request.setYear(2026);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(contractRepository.findFirstByRoom_IdAndStatus(1L, ContractStatus.ACTIVE)).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByRoom_IdAndMonthAndYear(1L, 3, 2026)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            invoiceService.createInvoice(request, 10L);
        });

        assertTrue(ex.getMessage().contains("đã có hóa đơn cho tháng"));
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Test chuyển trạng thái quá hạn (OVERDUE) khi ngày hết hạn trong quá khứ và chưa thanh toán")
    void testInvoice_StatusTransitionsToOverdue() {
        Invoice invoice = new Invoice();
        invoice.setRoomPrice(3000000.0);
        invoice.setElectricityAmount(200000.0);
        invoice.setWaterAmount(100000.0);
        invoice.setPaidAmount(0.0);
        invoice.setDueDate(LocalDate.now().minusDays(3)); // Quá hạn 3 ngày

        invoice.recalculateTotals();

        assertEquals(3300000.0, invoice.getTotalAmount());
        assertEquals(InvoiceStatus.OVERDUE, invoice.getStatus());
    }

    @Test
    @DisplayName("Test tính tiền phòng lẻ ngày (Prorated Rent) khi khách vào ở giữa tháng")
    void testCreateInvoice_Success_ProratedRent() {
        InvoiceRequest request = new InvoiceRequest();
        request.setRoomId(1L);
        request.setMonth(4); // Tháng 4 có 30 ngày
        request.setYear(2026);
        request.setRoomPrice(3000000.0);
        request.setIsProrated(true);
        request.setActualDays(10); // Ở 10 ngày -> Tiền phòng = (3,000,000 / 30) * 10 = 1,000,000
        request.setDueDate(LocalDate.now().plusDays(5));

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(contractRepository.findFirstByRoom_IdAndStatus(1L, ContractStatus.ACTIVE)).thenReturn(Optional.of(contract));
        when(invoiceRepository.existsByRoom_IdAndMonthAndYear(1L, 4, 2026)).thenReturn(false);
        when(meterReadingRepository.findByRoom_IdAndMonthAndYear(1L, 4, 2026)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = invoiceService.createInvoice(request, 10L);

        assertNotNull(invoice);
        assertTrue(invoice.getIsProrated());
        assertEquals(10, invoice.getActualDays());
        assertEquals(1000000.0, invoice.getRoomPrice());
        assertEquals(1000000.0, invoice.getTotalAmount());
    }
}
