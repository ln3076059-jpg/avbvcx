package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.PaymentRequest;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.enums.PaymentMethod;
import com.pms.propertymanagement.enums.PaymentStatus;
import com.pms.propertymanagement.repository.InvoiceRepository;
import com.pms.propertymanagement.repository.PaymentRepository;
import com.pms.propertymanagement.repository.UserRepository;
import com.pms.propertymanagement.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User owner;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(10L);

        Property property = new Property();
        property.setOwner(owner);

        Room room = new Room();
        room.setId(1L);
        room.setProperty(property);

        invoice = new Invoice();
        invoice.setId(100L);
        invoice.setRoom(room);
        invoice.setTotalAmount(4000000.0);
        invoice.setPaidAmount(0.0);
        invoice.setRemainingAmount(4000000.0);
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoice.setDueDate(LocalDate.now().plusDays(5));
    }

    @Test
    @DisplayName("Test thanh toán đầy đủ 100%: remainingAmount về 0, chuyển trạng thái sang PAID")
    void testCreatePayment_Success_FullPayment() {
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(100L);
        request.setAmount(4000000.0);
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        request.setTransactionCode("MB-123456");

        when(invoiceRepository.findById(100L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.createPayment(request, 10L);

        assertNotNull(payment);
        assertEquals(4000000.0, payment.getAmount());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(PaymentMethod.BANK_TRANSFER, payment.getPaymentMethod());

        // Kiểm tra hóa đơn được cập nhật
        assertEquals(4000000.0, invoice.getPaidAmount());
        assertEquals(0.0, invoice.getRemainingAmount());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());

        verify(invoiceRepository, times(1)).save(invoice);
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Test thanh toán 1 phần: remainingAmount trừ bớt, chuyển trạng thái sang PARTIALLY_PAID")
    void testCreatePayment_Success_PartialPayment() {
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(100L);
        request.setAmount(1500000.0);
        request.setPaymentMethod(PaymentMethod.CASH);

        when(invoiceRepository.findById(100L)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.createPayment(request, 10L);

        assertNotNull(payment);
        assertEquals(1500000.0, payment.getAmount());

        // Kiểm tra hóa đơn
        assertEquals(1500000.0, invoice.getPaidAmount());
        assertEquals(2500000.0, invoice.getRemainingAmount());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());

        verify(invoiceRepository, times(1)).save(invoice);
    }

    @Test
    @DisplayName("Test validation: Chặn khi nộp số tiền lớn hơn số tiền còn nợ")
    void testCreatePayment_ThrowsException_WhenAmountExceedsRemaining() {
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(100L);
        request.setAmount(5000000.0); // Nợ 4.000.000, nộp 5.000.000

        when(invoiceRepository.findById(100L)).thenReturn(Optional.of(invoice));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.createPayment(request, 10L);
        });

        assertTrue(ex.getMessage().contains("không được lớn hơn số tiền còn nợ"));
        verify(paymentRepository, never()).save(any());
        verify(invoiceRepository, never()).save(invoice);
    }
}
