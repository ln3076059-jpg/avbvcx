package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.BatchInvoiceRequest;
import com.pms.propertymanagement.dto.request.InvoiceRequest;
import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.InvoiceDetailType;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.enums.ServiceType;
import com.pms.propertymanagement.repository.*;
import com.pms.propertymanagement.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final ContractRepository contractRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public Page<Invoice> searchInvoices(Long ownerId, Long propertyId, Long roomId, Long tenantId, Integer month, Integer year, InvoiceStatus status, Pageable pageable) {
        return invoiceRepository.searchInvoices(ownerId, propertyId, roomId, tenantId, month, year, status, pageable);
    }

    @Override
    public Page<Invoice> searchInvoicesForOwner(Long ownerId, Long propertyId, InvoiceStatus status, Integer month, Integer year, String keyword, Pageable pageable) {
        return invoiceRepository.searchInvoicesForOwner(ownerId, propertyId, status, month, year, keyword, pageable);
    }

    @Override
    public Invoice getInvoiceById(Long id, Long ownerId) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn có ID: " + id));

        if (!invoice.getRoom().getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền truy cập hóa đơn này");
        }
        return invoice;
    }

    @Override
    public Invoice getInvoiceByIdForTenant(Long id, User tenantUser) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn có ID: " + id));

        boolean isOwnerOfInvoice = false;
        String userPhoneNorm = tenantUser != null ? normalizePhone(tenantUser.getPhone()) : "";
        if (!userPhoneNorm.isEmpty() && invoice.getTenant() != null) {
            if (userPhoneNorm.equals(normalizePhone(invoice.getTenant().getPhone()))) {
                isOwnerOfInvoice = true;
            }
        }
        if (!isOwnerOfInvoice && !userPhoneNorm.isEmpty() && invoice.getContract() != null && invoice.getContract().getTenants() != null) {
            for (Tenant t : invoice.getContract().getTenants()) {
                if (userPhoneNorm.equals(normalizePhone(t.getPhone()))) {
                    isOwnerOfInvoice = true;
                    break;
                }
            }
        }

        if (!isOwnerOfInvoice) {
            isOwnerOfInvoice = getInvoicesForTenant(tenantUser).stream()
                    .anyMatch(i -> i.getId().equals(id));
        }

        if (!isOwnerOfInvoice) {
            throw new IllegalArgumentException("Bạn không có quyền xem hóa đơn này");
        }
        return invoice;
    }

    @Override
    @Transactional
    public Invoice createInvoice(InvoiceRequest request, Long ownerId) {
        if (request.getMonth() == null || request.getMonth() < 1 || request.getMonth() > 12) {
            throw new IllegalArgumentException("Tháng lập hóa đơn không hợp lệ (phải từ 1 đến 12)!");
        }
        if (request.getYear() == null || request.getYear() < 2000 || request.getYear() > 2100) {
            throw new IllegalArgumentException("Năm lập hóa đơn không hợp lệ!");
        }
        if (request.getRoomPrice() != null && request.getRoomPrice() < 0) {
            throw new IllegalArgumentException("Tiền thuê phòng không được nhỏ hơn 0!");
        }
        if (request.getOtherAmount() != null && request.getOtherAmount() < 0) {
            throw new IllegalArgumentException("Khoản thu khác không được nhỏ hơn 0!");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng có ID: " + request.getRoomId()));

        if (!room.getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền lập hóa đơn cho phòng này");
        }

        // 1. Kiểm tra hợp đồng ACTIVE
        Contract contract = contractRepository.findFirstByRoom_IdAndStatus(room.getId(), ContractStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Phòng " + room.getName() + " hiện không có hợp đồng thuê ACTIVE nào!"));

        // 2. Không cho tạo trùng hóa đơn: room + month + year
        if (invoiceRepository.existsByRoom_IdAndMonthAndYear(room.getId(), request.getMonth(), request.getYear())) {
            throw new IllegalArgumentException("Phòng " + room.getName() + " đã có hóa đơn cho tháng " +
                    request.getMonth() + "/" + request.getYear());
        }

        Tenant tenant = contract.getRepresentative();
        if (tenant == null && contract.getTenants() != null && !contract.getTenants().isEmpty()) {
            tenant = contract.getTenants().iterator().next();
        }

        Invoice invoice = new Invoice();
        invoice.setContract(contract);
        invoice.setRoom(room);
        invoice.setTenant(tenant);
        invoice.setMonth(request.getMonth());
        invoice.setYear(request.getYear());
        invoice.setDueDate(request.getDueDate() != null ? request.getDueDate() : LocalDate.now().plusDays(5));
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());

        // Tiền phòng (hỗ trợ tính tiền lẻ ngày nếu khách mới vào giữa tháng)
        Double baseRoomPrice = (request.getRoomPrice() != null && request.getRoomPrice() > 0)
                ? request.getRoomPrice()
                : contract.getRentPrice();

        Double roomPrice = baseRoomPrice;
        String roomDescription = "Tiền thuê phòng tháng " + request.getMonth() + "/" + request.getYear();

        if (Boolean.TRUE.equals(request.getIsProrated()) && request.getActualDays() != null) {
            int totalDays = java.time.YearMonth.of(request.getYear(), request.getMonth()).lengthOfMonth();
            if (request.getActualDays() <= 0 || request.getActualDays() > totalDays) {
                throw new IllegalArgumentException("Số ngày ở thực tế (" + request.getActualDays()
                        + ") phải nằm trong khoảng từ 1 đến " + totalDays + " ngày của tháng " + request.getMonth() + "/" + request.getYear() + "!");
            }
            roomPrice = (double) Math.round((baseRoomPrice / totalDays) * request.getActualDays());
            invoice.setIsProrated(true);
            invoice.setActualDays(request.getActualDays());
            roomDescription = "Tiền thuê phòng lẻ (" + request.getActualDays() + "/" + totalDays + " ngày - Giá gốc: "
                    + String.format("%,.0f", baseRoomPrice) + " đ)";
        }

        invoice.setRoomPrice(roomPrice);

        List<InvoiceDetail> details = new ArrayList<>();
        details.add(new InvoiceDetail(invoice, roomDescription, 1.0, roomPrice, roomPrice, InvoiceDetailType.ROOM));

        boolean isElecWaterIncluded = Boolean.TRUE.equals(contract.getIsElectricityWaterIncluded());
        double elecUsage = 0.0;
        double waterUsage = 0.0;

        if (isElecWaterIncluded) {
            invoice.setElectricityUsage(0.0);
            invoice.setElectricityPrice(0.0);
            invoice.setElectricityAmount(0.0);
            invoice.setWaterUsage(0.0);
            invoice.setWaterPrice(0.0);
            invoice.setWaterAmount(0.0);
        } else {
            // Lấy chỉ số điện nước tháng đó
            Optional<MeterReading> readingOpt = meterReadingRepository.findByRoom_IdAndMonthAndYear(room.getId(), request.getMonth(), request.getYear());
            double elecPrice = resolveDefaultElectricityPrice(room);
            double waterPrice = resolveDefaultWaterPrice(room);

            if (readingOpt.isPresent()) {
                MeterReading r = readingOpt.get();
                elecUsage = r.getElectricityUsage();
                if (r.getElectricityPrice() != null && r.getElectricityPrice() > 0) elecPrice = r.getElectricityPrice();
                waterUsage = r.getWaterUsage();
                if (r.getWaterPrice() != null && r.getWaterPrice() > 0) waterPrice = r.getWaterPrice();
            }

            invoice.setElectricityUsage(elecUsage);
            invoice.setElectricityPrice(elecPrice);
            double elecAmount = (double) Math.round(elecUsage * elecPrice);
            invoice.setElectricityAmount(elecAmount);
            details.add(new InvoiceDetail(invoice, "Tiền điện (" + elecUsage + " kWh x " + String.format("%,.0f", elecPrice) + " đ)", elecUsage, elecPrice, elecAmount, InvoiceDetailType.ELECTRICITY));

            invoice.setWaterUsage(waterUsage);
            invoice.setWaterPrice(waterPrice);
            double waterAmount = (double) Math.round(waterUsage * waterPrice);
            invoice.setWaterAmount(waterAmount);
            details.add(new InvoiceDetail(invoice, "Tiền nước (" + waterUsage + " m³ x " + String.format("%,.0f", waterPrice) + " đ)", waterUsage, waterPrice, waterAmount, InvoiceDetailType.WATER));
        }

        // Dịch vụ cố định từ hợp đồng hoặc phòng
        double serviceTotal = 0.0;
        Set<ServiceItem> services = contract.getServices();
        if (services == null || services.isEmpty()) {
            services = room.getServices();
        }
        if (services != null) {
            double tenantCount = (double) Math.max(1, contract.getTenants() != null ? contract.getTenants().size() : 1);
            for (ServiceItem s : services) {
                if (s.getType() == ServiceType.METERED) {
                    continue; // Đã tính theo công tơ riêng
                }
                if (isElectricityService(s) && (elecUsage > 0 || isElecWaterIncluded)) continue;
                if (isWaterService(s) && (waterUsage > 0 || isElecWaterIncluded)) continue;

                double price = s.getPrice() != null ? s.getPrice() : 0.0;
                String unit = s.getUnit() != null ? s.getUnit() : "tháng";
                double qty = unit.toLowerCase().contains("người") ? tenantCount : 1.0;
                double itemAmount = (double) Math.round(qty * price);
                serviceTotal += itemAmount;
                details.add(new InvoiceDetail(invoice, s.getName() + " (" + unit + ")", qty, price, itemAmount, InvoiceDetailType.SERVICE));
            }
        }
        invoice.setServiceAmount(serviceTotal);

        // Khoản thu khác
        double otherAmount = (request.getOtherAmount() != null) ? (double) Math.round(request.getOtherAmount()) : 0.0;
        invoice.setOtherAmount(otherAmount);
        if (otherAmount > 0) {
            String note = request.getNote() != null && !request.getNote().isEmpty() ? request.getNote() : "Khoản thu khác";
            details.add(new InvoiceDetail(invoice, note, 1.0, otherAmount, otherAmount, InvoiceDetailType.OTHER));
        }

        invoice.setPaidAmount(0.0);
        invoice.recalculateTotals();
        invoice.setDetails(details);

        return invoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public Invoice updateInvoice(Long id, InvoiceRequest request, Long ownerId) {
        Invoice invoice = getInvoiceById(id, ownerId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalArgumentException("Hóa đơn đã được thanh toán đầy đủ, không thể chỉnh sửa!");
        }
        if (request.getRoomPrice() != null && request.getRoomPrice() < 0) {
            throw new IllegalArgumentException("Tiền thuê phòng không được nhỏ hơn 0!");
        }
        if (request.getOtherAmount() != null && request.getOtherAmount() < 0) {
            throw new IllegalArgumentException("Khoản thu khác không được nhỏ hơn 0!");
        }

        double oldTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : 0.0;

        if (request.getRoomPrice() != null) {
            double newRoomPrice = (double) Math.round(request.getRoomPrice());
            invoice.setRoomPrice(newRoomPrice);
            if (invoice.getDetails() != null) {
                for (InvoiceDetail detail : invoice.getDetails()) {
                    if (detail.getType() == InvoiceDetailType.ROOM) {
                        detail.setUnitPrice(newRoomPrice);
                        detail.setAmount(newRoomPrice);
                    }
                }
            }
        }

        if (request.getOtherAmount() != null) {
            double newOtherAmount = (double) Math.round(request.getOtherAmount());
            invoice.setOtherAmount(newOtherAmount);
            if (invoice.getDetails() != null) {
                InvoiceDetail otherDetail = invoice.getDetails().stream()
                        .filter(d -> d.getType() == InvoiceDetailType.OTHER)
                        .findFirst()
                        .orElse(null);
                if (newOtherAmount > 0) {
                    String note = (request.getNote() != null && !request.getNote().isBlank()) ? request.getNote() : "Khoản thu khác";
                    if (otherDetail != null) {
                        otherDetail.setDescription(note);
                        otherDetail.setUnitPrice(newOtherAmount);
                        otherDetail.setAmount(newOtherAmount);
                    } else {
                        invoice.getDetails().add(new InvoiceDetail(invoice, note, 1.0, newOtherAmount, newOtherAmount, InvoiceDetailType.OTHER));
                    }
                } else if (otherDetail != null) {
                    invoice.getDetails().remove(otherDetail);
                }
            }
        }

        if (request.getDueDate() != null) invoice.setDueDate(request.getDueDate());

        invoice.recalculateTotals();
        if (invoice.getPaidAmount() != null && invoice.getTotalAmount() < invoice.getPaidAmount()) {
            throw new IllegalArgumentException("Tổng tiền hóa đơn mới không được nhỏ hơn số tiền khách đã thanh toán ("
                    + String.format("%,.0f", invoice.getPaidAmount()) + " đ)!");
        }

        double newTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : 0.0;
        if (Math.abs(newTotal - oldTotal) >= 1.0) {
            String auditStamp = String.format("[Audit %s] Điều chỉnh tổng tiền: %,.0f đ -> %,.0f đ",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")), oldTotal, newTotal);
            log.info("[InvoiceAudit] Invoice #{} (ownerId={}): {}", invoice.getInvoiceCode(), ownerId, auditStamp);
        }

        return invoiceRepository.save(invoice);
    }

    @Override
    public int generateBatchInvoices(BatchInvoiceRequest request, Long ownerId) {
        if (request.getMonth() == null || request.getMonth() < 1 || request.getMonth() > 12) {
            throw new IllegalArgumentException("Tháng lập hóa đơn hàng loạt không hợp lệ!");
        }
        if (request.getYear() == null || request.getYear() < 2000 || request.getYear() > 2100) {
            throw new IllegalArgumentException("Năm lập hóa đơn hàng loạt không hợp lệ!");
        }

        List<Contract> activeContracts;
        if (request.getPropertyId() != null) {
            Property prop = propertyRepository.findById(request.getPropertyId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khu trọ có ID: " + request.getPropertyId()));
            if (prop.getOwner() == null || !prop.getOwner().getId().equals(ownerId)) {
                throw new IllegalArgumentException("Bạn không có quyền lập hóa đơn hàng loạt cho khu trọ này!");
            }
            activeContracts = contractRepository.findByRoom_Property_IdAndStatus(request.getPropertyId(), ContractStatus.ACTIVE);
        } else {
            activeContracts = contractRepository.findByRoom_Property_Owner_IdAndStatus(ownerId, ContractStatus.ACTIVE);
        }

        TransactionTemplate requiresNewTx = new TransactionTemplate(transactionManager);
        requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int count = 0;
        for (Contract c : activeContracts) {
            Room r = c.getRoom();
            if (r == null) continue;

            if (!invoiceRepository.existsByRoom_IdAndMonthAndYear(r.getId(), request.getMonth(), request.getYear())) {
                try {
                    InvoiceRequest req = new InvoiceRequest();
                    req.setRoomId(r.getId());
                    req.setMonth(request.getMonth());
                    req.setYear(request.getYear());
                    req.setDueDate(request.getDueDate());
                    req.setRoomPrice(c.getRentPrice());
                    requiresNewTx.executeWithoutResult(status -> createInvoice(req, ownerId));
                    count++;
                } catch (Exception e) {
                    log.warn("Không thể tự động lập hóa đơn cho phòng {} (Hợp đồng #{}): {}",
                            r.getName(), c.getCode(), e.getMessage());
                }
            }
        }
        return count;
    }

    @Override
    @Transactional
    public void deleteInvoice(Long id, Long ownerId) {
        Invoice invoice = getInvoiceById(id, ownerId);
        if (invoice.getPaidAmount() > 0) {
            throw new IllegalArgumentException("Không thể xóa hóa đơn đã có giao dịch thanh toán!");
        }
        invoiceRepository.delete(invoice);
    }

    @Override
    public List<Invoice> getInvoicesForTenant(User tenantUser) {
        if (tenantUser == null || tenantUser.getPhone() == null || tenantUser.getPhone().isBlank()) return List.of();
        return invoiceRepository.findInvoicesForTenant(tenantUser.getId(), tenantUser.getPhone().trim());
    }

    @Override
    @Transactional
    public void notifyTransfer(Long invoiceId, User tenantUser, String transferNote) {
        Invoice invoice = getInvoiceByIdForTenant(invoiceId, tenantUser);
        invoice.setTransferNotifiedAt(LocalDateTime.now());
        invoice.setTransferNote(transferNote != null && !transferNote.isBlank()
                ? transferNote.trim()
                : "Đã chuyển khoản qua VietQR");
        invoiceRepository.save(invoice);
    }

    @Override
    public Optional<Invoice> getInvoiceByRoomAndMonthAndYear(Long roomId, Integer month, Integer year) {
        return invoiceRepository.findByRoom_IdAndMonthAndYear(roomId, month, year);
    }

    public static boolean isElectricityService(ServiceItem s) {
        if (s == null || s.getName() == null) return false;
        String name = s.getName().trim().toLowerCase();
        String unit = s.getUnit() != null ? s.getUnit().trim().toLowerCase() : "";
        if (name.contains("thang máy") || name.contains("sửa chữa") || name.contains("bảo trì")) {
            return false;
        }
        return name.contains("điện") || name.contains("evn") || unit.contains("kwh");
    }

    public static boolean isWaterService(ServiceItem s) {
        if (s == null || s.getName() == null) return false;
        String name = s.getName().trim().toLowerCase();
        String unit = s.getUnit() != null ? s.getUnit().trim().toLowerCase() : "";
        if (name.contains("nước uống") || name.contains("đóng bình") || name.contains("nước nóng")) {
            return false;
        }
        return name.contains("nước") || name.contains("thủy cục") || unit.contains("m3") || unit.contains("m³") || unit.contains("khối");
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    private Double resolveDefaultElectricityPrice(Room room) {
        if (room != null && room.getServices() != null) {
            for (ServiceItem s : room.getServices()) {
                if (isElectricityService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        if (room != null && room.getProperty() != null && room.getProperty().getServiceItems() != null) {
            for (ServiceItem s : room.getProperty().getServiceItems()) {
                if (isElectricityService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        log.warn("[Billing] Phòng {} chưa cài đặt đơn giá điện, hệ thống sử dụng đơn giá mặc định: 3,500 đ/kWh",
                room != null ? room.getName() : "N/A");
        return 3500.0;
    }

    private Double resolveDefaultWaterPrice(Room room) {
        if (room != null && room.getServices() != null) {
            for (ServiceItem s : room.getServices()) {
                if (isWaterService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        if (room != null && room.getProperty() != null && room.getProperty().getServiceItems() != null) {
            for (ServiceItem s : room.getProperty().getServiceItems()) {
                if (isWaterService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        log.warn("[Billing] Phòng {} chưa cài đặt đơn giá nước, hệ thống sử dụng đơn giá mặc định: 20,000 đ/m3",
                room != null ? room.getName() : "N/A");
        return 20000.0;
    }
}
