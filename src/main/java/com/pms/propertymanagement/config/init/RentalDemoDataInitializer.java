package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.*;
import com.pms.propertymanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RentalDemoDataInitializer {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final ContractRepository contractRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceDetailRepository invoiceDetailRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;

    @Transactional
    public void init() {
        log.info("Initializing Rental Demo Data (Meters, Invoices, Payments, Maintenance)...");

        User owner = userRepository.findByUsername("owner1").orElse(null);
        if (owner == null) {
            log.warn("Owner 'owner1' not found, skip rental demo initialization.");
            return;
        }

        // Lấy hoặc chuẩn bị Property & Rooms
        List<Property> properties = propertyRepository.findByOwnerId(owner.getId());
        if (properties.isEmpty()) {
            log.warn("No properties found for owner1, skip.");
            return;
        }
        Property property = properties.get(0);

        List<Room> rooms = roomRepository.findByProperty_Id(property.getId());
        if (rooms.isEmpty()) {
            log.warn("No rooms found for property {}, skip.", property.getName());
            return;
        }

        // Lấy danh sách tenant
        List<Tenant> tenants = tenantRepository.findByOwner_Id(owner.getId());
        if (tenants.size() < 3) {
            log.warn("Less than 3 tenants found for owner1, skip.");
            return;
        }

        Tenant tenant1 = tenants.get(0);
        Tenant tenant2 = tenants.get(1);
        Tenant tenant3 = tenants.get(2);

        // Đảm bảo ít nhất 3 phòng có hợp đồng ACTIVE
        Room room1 = rooms.get(0);
        Room room2 = rooms.size() > 1 ? rooms.get(1) : room1;
        Room room3 = rooms.size() > 2 ? rooms.get(2) : room1;

        // Cập nhật trạng thái phòng
        room1.setStatus(RoomStatus.RENTED);
        room2.setStatus(RoomStatus.RENTED);
        room3.setStatus(RoomStatus.RENTED);
        if (rooms.size() > 3) rooms.get(3).setStatus(RoomStatus.AVAILABLE);
        if (rooms.size() > 4) rooms.get(4).setStatus(RoomStatus.MAINTENANCE);
        roomRepository.saveAll(rooms);

        // Hợp đồng
        Contract c1 = ensureContract(room1, tenant1, 3500000.0);
        Contract c2 = ensureContract(room2, tenant2, 4000000.0);
        Contract c3 = ensureContract(room3, tenant3, 3800000.0);

        // Chỉ số điện nước (MeterReading)
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();
        int prevMonth = currentMonth == 1 ? 12 : currentMonth - 1;
        int prevYear = currentMonth == 1 ? currentYear - 1 : currentYear;

        ensureMeterReading(room1, prevMonth, prevYear, 120.0, 185.0, 15.0, 22.0, owner);
        ensureMeterReading(room1, currentMonth, currentYear, 185.0, 260.0, 22.0, 31.0, owner);

        ensureMeterReading(room2, prevMonth, prevYear, 80.0, 140.0, 10.0, 16.0, owner);
        ensureMeterReading(room2, currentMonth, currentYear, 140.0, 215.0, 16.0, 23.0, owner);

        ensureMeterReading(room3, currentMonth, currentYear, 50.0, 110.0, 8.0, 14.0, owner);

        // Khởi tạo các kịch bản Hóa đơn (Invoices):
        // 1. Hóa đơn PAID (Kỳ tháng trước, Phòng 1)
        if (!invoiceRepository.existsByRoom_IdAndMonthAndYear(room1.getId(), prevMonth, prevYear)) {
            createPaidInvoice(room1, tenant1, c1, prevMonth, prevYear);
        }

        // 2. Hóa đơn PARTIALLY_PAID (Kỳ tháng này, Phòng 2)
        if (!invoiceRepository.existsByRoom_IdAndMonthAndYear(room2.getId(), currentMonth, currentYear)) {
            createPartiallyPaidInvoice(room2, tenant2, c2, currentMonth, currentYear);
        }

        // 3. Hóa đơn UNPAID (Kỳ tháng này, Phòng 3)
        if (!invoiceRepository.existsByRoom_IdAndMonthAndYear(room3.getId(), currentMonth, currentYear)) {
            createUnpaidInvoice(room3, tenant3, c3, currentMonth, currentYear);
        }

        // 4. Hóa đơn OVERDUE (Kỳ tháng 1 hoặc quá hạn, Phòng 1)
        int overdueMonth = prevMonth == 1 ? 12 : prevMonth - 1;
        int overdueYear = prevMonth == 1 ? prevYear - 1 : prevYear;
        if (!invoiceRepository.existsByRoom_IdAndMonthAndYear(room1.getId(), overdueMonth, overdueYear)) {
            createOverdueInvoice(room1, tenant1, c1, overdueMonth, overdueYear);
        }

        // Khởi tạo Yêu cầu sửa chữa (MaintenanceRequest)
        ensureMaintenanceRequests(room1, room2, rooms.size() > 4 ? rooms.get(4) : room3);

        log.info("Rental Demo Data successfully initialized!");
    }

    private Contract ensureContract(Room room, Tenant tenant, Double rentPrice) {
        Optional<Contract> existing = contractRepository.findFirstByRoom_IdAndStatus(room.getId(), ContractStatus.ACTIVE);
        if (existing.isPresent()) {
            return existing.get();
        }
        Contract c = new Contract();
        c.setCode("HD-" + room.getName().replaceAll("\\s+", "") + "-" + System.currentTimeMillis() % 10000);
        c.setRoom(room);
        c.setRepresentative(tenant);
        c.setRentPrice(rentPrice);
        c.setDeposit(rentPrice);
        c.setPaymentCycle(1);
        c.setStartDate(LocalDate.now().minusMonths(6));
        c.setEndDate(LocalDate.now().plusMonths(6));
        c.setStatus(ContractStatus.ACTIVE);
        return contractRepository.save(c);
    }

    private void ensureMeterReading(Room room, int month, int year, double oldE, double newE, double oldW, double newW, User recordedBy) {
        if (meterReadingRepository.existsByRoom_IdAndMonthAndYear(room.getId(), month, year)) {
            return;
        }
        MeterReading mr = new MeterReading();
        mr.setRoom(room);
        mr.setMonth(month);
        mr.setYear(year);
        mr.setOldElectricityIndex(oldE);
        mr.setNewElectricityIndex(newE);
        mr.setOldWaterIndex(oldW);
        mr.setNewWaterIndex(newW);
        mr.setElectricityUsage(newE - oldE);
        mr.setWaterUsage(newW - oldW);
        mr.setElectricityPrice(3500.0);
        mr.setWaterPrice(25000.0);
        mr.setCreatedAt(LocalDateTime.now());
        meterReadingRepository.save(mr);
    }

    private void createPaidInvoice(Room room, Tenant tenant, Contract contract, int month, int year) {
        Invoice inv = new Invoice();
        inv.setInvoiceCode(String.format("HD-%04d%02d-%s", year, month, room.getName().replaceAll("\\s+", "")));
        inv.setMonth(month);
        inv.setYear(year);
        inv.setRoom(room);
        inv.setTenant(tenant);
        inv.setContract(contract);
        inv.setCreatedAt(LocalDateTime.of(year, month, 1, 8, 0));
        inv.setDueDate(LocalDate.of(year, month, 10));

        inv.setRoomPrice(3500000.0);
        inv.setElectricityUsage(65.0);
        inv.setElectricityPrice(3500.0);
        inv.setElectricityAmount(227500.0);
        inv.setWaterUsage(7.0);
        inv.setWaterPrice(25000.0);
        inv.setWaterAmount(175000.0);
        inv.setServiceAmount(150000.0);
        inv.setTotalAmount(4052500.0);
        inv.setPaidAmount(4052500.0);
        inv.setRemainingAmount(0.0);
        inv.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(inv);

        // Chi tiết
        addDetails(inv, 3500000.0, 65.0, 3500.0, 7.0, 25000.0, 150000.0);

        // Phiếu thu (Payment) BANK_TRANSFER
        Payment payment = new Payment();
        payment.setOwner(room.getProperty().getOwner());
        payment.setInvoice(inv);
        payment.setContract(contract);
        payment.setAmount(4052500.0);
        payment.setPaymentDate(LocalDateTime.of(year, month, 5, 14, 30));
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setTransactionCode("MB-TRANS-0982348");
        payment.setNote("Chuyển khoản thanh toán tiền phòng " + room.getName());
        paymentRepository.save(payment);
    }

    private void createPartiallyPaidInvoice(Room room, Tenant tenant, Contract contract, int month, int year) {
        Invoice inv = new Invoice();
        inv.setInvoiceCode(String.format("HD-%04d%02d-%s", year, month, room.getName().replaceAll("\\s+", "")));
        inv.setMonth(month);
        inv.setYear(year);
        inv.setRoom(room);
        inv.setTenant(tenant);
        inv.setContract(contract);
        inv.setCreatedAt(LocalDateTime.of(year, month, 1, 8, 0));
        inv.setDueDate(LocalDate.of(year, month, 15));

        inv.setRoomPrice(4000000.0);
        inv.setElectricityUsage(75.0);
        inv.setElectricityPrice(3500.0);
        inv.setElectricityAmount(262500.0);
        inv.setWaterUsage(7.0);
        inv.setWaterPrice(25000.0);
        inv.setWaterAmount(175000.0);
        inv.setServiceAmount(180000.0);
        inv.setTotalAmount(4617500.0);
        inv.setPaidAmount(2000000.0);
        inv.setRemainingAmount(2617500.0);
        inv.setStatus(InvoiceStatus.PARTIALLY_PAID);
        invoiceRepository.save(inv);

        addDetails(inv, 4000000.0, 75.0, 3500.0, 7.0, 25000.0, 180000.0);

        // Phiếu thu CASH
        Payment payment = new Payment();
        payment.setOwner(room.getProperty().getOwner());
        payment.setInvoice(inv);
        payment.setContract(contract);
        payment.setAmount(2000000.0);
        payment.setPaymentDate(LocalDateTime.now().minusDays(2));
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setTransactionCode("CASH-" + System.currentTimeMillis() % 100000);
        payment.setNote("Nộp tiền mặt tạm ứng đợt 1");
        paymentRepository.save(payment);
    }

    private void createUnpaidInvoice(Room room, Tenant tenant, Contract contract, int month, int year) {
        Invoice inv = new Invoice();
        inv.setInvoiceCode(String.format("HD-%04d%02d-%s", year, month, room.getName().replaceAll("\\s+", "")));
        inv.setMonth(month);
        inv.setYear(year);
        inv.setRoom(room);
        inv.setTenant(tenant);
        inv.setContract(contract);
        inv.setCreatedAt(LocalDateTime.of(year, month, 1, 8, 0));
        inv.setDueDate(LocalDate.now().plusDays(5));

        inv.setRoomPrice(3800000.0);
        inv.setElectricityUsage(60.0);
        inv.setElectricityPrice(3500.0);
        inv.setElectricityAmount(210000.0);
        inv.setWaterUsage(6.0);
        inv.setWaterPrice(25000.0);
        inv.setWaterAmount(150000.0);
        inv.setServiceAmount(160000.0);
        inv.setTotalAmount(4320000.0);
        inv.setPaidAmount(0.0);
        inv.setRemainingAmount(4320000.0);
        inv.setStatus(InvoiceStatus.UNPAID);
        invoiceRepository.save(inv);

        addDetails(inv, 3800000.0, 60.0, 3500.0, 6.0, 25000.0, 160000.0);
    }

    private void createOverdueInvoice(Room room, Tenant tenant, Contract contract, int month, int year) {
        Invoice inv = new Invoice();
        inv.setInvoiceCode(String.format("HD-%04d%02d-%s-OD", year, month, room.getName().replaceAll("\\s+", "")));
        inv.setMonth(month);
        inv.setYear(year);
        inv.setRoom(room);
        inv.setTenant(tenant);
        inv.setContract(contract);
        inv.setCreatedAt(LocalDateTime.of(year, month, 1, 8, 0));
        inv.setDueDate(LocalDate.now().minusDays(10)); // Đã quá hạn 10 ngày

        inv.setRoomPrice(3500000.0);
        inv.setElectricityUsage(70.0);
        inv.setElectricityPrice(3500.0);
        inv.setElectricityAmount(245000.0);
        inv.setWaterUsage(6.0);
        inv.setWaterPrice(25000.0);
        inv.setWaterAmount(150000.0);
        inv.setServiceAmount(150000.0);
        inv.setTotalAmount(4045000.0);
        inv.setPaidAmount(0.0);
        inv.setRemainingAmount(4045000.0);
        inv.setStatus(InvoiceStatus.OVERDUE);
        invoiceRepository.save(inv);

        addDetails(inv, 3500000.0, 70.0, 3500.0, 6.0, 25000.0, 150000.0);
    }

    private void addDetails(Invoice inv, double roomPrice, double eUsage, double ePrice, double wUsage, double wPrice, double servicePrice) {
        List<InvoiceDetail> list = new ArrayList<>();
        list.add(new InvoiceDetail(inv, "Tiền thuê phòng", 1.0, roomPrice, roomPrice, InvoiceDetailType.ROOM));
        list.add(new InvoiceDetail(inv, "Tiền điện sinh hoạt", eUsage, ePrice, eUsage * ePrice, InvoiceDetailType.ELECTRICITY));
        list.add(new InvoiceDetail(inv, "Tiền nước sinh hoạt", wUsage, wPrice, wUsage * wPrice, InvoiceDetailType.WATER));
        list.add(new InvoiceDetail(inv, "Phí dịch vụ chung (Wifi + Rác)", 1.0, servicePrice, servicePrice, InvoiceDetailType.SERVICE));
        invoiceDetailRepository.saveAll(list);
    }

    private void ensureMaintenanceRequests(Room room1, Room room2, Room roomMaintenance) {
        if (maintenanceRequestRepository.count() > 0) {
            return;
        }

        User userTenant1 = userRepository.findByUsername("tenant1").orElse(null);
        User userStaff1 = userRepository.findByUsername("staff1").orElse(null);

        if (userTenant1 != null) {
            // Request 1: PENDING
            MaintenanceRequest req1 = new MaintenanceRequest();
            req1.setCode("MR-" + System.currentTimeMillis() % 100000);
            req1.setRoom(room1);
            req1.setTenant(userTenant1);
            req1.setCategory(MaintenanceCategory.PLUMBING);
            req1.setDescription("Điều hòa phòng không mát, cần kiểm tra gas và vệ sinh lưới lọc.");
            req1.setImageUrl("https://images.unsplash.com/photo-1585704032915-c3400ca199e7?w=800");
            req1.setStatus(MaintenanceStatus.PENDING);
            req1.setCreatedAt(LocalDateTime.now().minusDays(1));
            req1.setUpdatedAt(LocalDateTime.now().minusDays(1));
            maintenanceRequestRepository.save(req1);

            // Request 2: IN_PROGRESS
            MaintenanceRequest req2 = new MaintenanceRequest();
            req2.setCode("MR-" + (System.currentTimeMillis() + 1) % 100000);
            req2.setRoom(roomMaintenance != null ? roomMaintenance : room2);
            req2.setTenant(userTenant1);
            req2.setStaff(userStaff1);
            req2.setCategory(MaintenanceCategory.ELECTRICAL);
            req2.setDescription("Aptomat tổng tự ngắt liên tục khi bật điều hòa, cần kiểm tra đường dây nguồn.");
            req2.setImageUrl("https://images.unsplash.com/photo-1621905251189-08b45d6a269e?w=800");
            req2.setStatus(MaintenanceStatus.IN_PROGRESS);
            req2.setAssignedAt(LocalDateTime.now().minusHours(5));
            req2.setStartedAt(LocalDateTime.now().minusHours(2));
            req2.setCreatedAt(LocalDateTime.now().minusDays(2));
            req2.setUpdatedAt(LocalDateTime.now().minusHours(2));
            maintenanceRequestRepository.save(req2);
        }
    }
}
