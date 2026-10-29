package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.ContractRequest;
import com.pms.propertymanagement.entity.Contract;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.Tenant;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.ContractStatus;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.exception.ForbiddenException;
import com.pms.propertymanagement.repository.ContractRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.repository.TenantRepository;
import com.pms.propertymanagement.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final com.pms.propertymanagement.repository.InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public Contract createContract(ContractRequest dto) {
        return createContract(dto, null);
    }

    @Override
    @Transactional
    public Contract createContract(ContractRequest dto, Long ownerId) {
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và ngày kết thúc không được để trống");
        }
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu hợp đồng không thể sau ngày kết thúc");
        }
        if (dto.getRentPrice() == null || dto.getRentPrice() < 0) {
            throw new IllegalArgumentException("Giá thuê phòng không hợp lệ");
        }
        if (dto.getDeposit() == null || dto.getDeposit() < 0) {
            throw new IllegalArgumentException("Tiền đặt cọc không hợp lệ");
        }
        if (dto.getTenantIds() == null || dto.getTenantIds().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một khách thuê cho hợp đồng");
        }

        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với ID: " + dto.getRoomId()));

        Long roomOwnerId = (room.getProperty() != null && room.getProperty().getOwner() != null)
                ? room.getProperty().getOwner().getId() : null;
        if (ownerId != null && !ownerId.equals(roomOwnerId)) {
            throw new ForbiddenException("Bạn không có quyền lập hợp đồng cho phòng này");
        }

        // Ràng buộc nghiệp vụ: Chỉ cho phép tạo hợp đồng mới nếu phòng đang AVAILABLE
        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new IllegalStateException("Phòng " + room.getName() + " hiện không ở trạng thái sẵn sàng cho thuê (" + room.getStatus().getDisplayName() + ")!");
        }
        boolean hasActiveContract = contractRepository.findFirstByRoom_IdAndStatus(room.getId(), ContractStatus.ACTIVE).isPresent();
        if (hasActiveContract) {
            throw new IllegalStateException("Phòng " + room.getName() + " đang có hợp đồng hiệu lực! Vui lòng hoàn tất thanh lý hợp đồng cũ.");
        }

        if (room.getMaxOccupancy() != null && dto.getTenantIds().size() > room.getMaxOccupancy()) {
            throw new IllegalArgumentException("Số lượng khách thuê (" + dto.getTenantIds().size()
                    + ") vượt quá sức chứa tối đa của phòng (" + room.getMaxOccupancy() + " người)");
        }

        Contract contract = new Contract();
        contract.setRoom(room);
        contract.setStartDate(dto.getStartDate());
        contract.setEndDate(dto.getEndDate());
        contract.setRentPrice(dto.getRentPrice());
        contract.setDeposit(dto.getDeposit());
        contract.setPaymentCycle(dto.getPaymentCycle());
        contract.setIsElectricityWaterIncluded(dto.getIsElectricityWaterIncluded());
        contract.setBedCount(dto.getBedCount());
        contract.setNote(dto.getNote());
        contract.setStatus(ContractStatus.ACTIVE);

        List<Tenant> tenants = tenantRepository.findAllById(dto.getTenantIds());
        if (roomOwnerId != null) {
            boolean allBelongToOwner = tenants.stream()
                    .allMatch(t -> t.getOwner() != null && roomOwnerId.equals(t.getOwner().getId()));
            if (!allBelongToOwner) {
                throw new ForbiddenException("Một hoặc nhiều khách thuê được chọn không thuộc quyền quản lý của chủ nhà!");
            }
        }

        Map<Long, Tenant> tenantMap = tenants.stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        List<Tenant> orderedTenants = dto.getTenantIds().stream()
                .filter(tenantMap::containsKey)
                .map(tenantMap::get)
                .collect(Collectors.toList());

        if (orderedTenants.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy thông tin khách thuê hợp lệ");
        }

        contract.setRepresentative(orderedTenants.get(0));
        contract.setTenants(new HashSet<>(orderedTenants));
        
        // Copy services from room to contract
        if(room.getServices() != null){
             contract.setServices(new HashSet<>(room.getServices()));
        }

        // Update Room Status
        room.setStatus(RoomStatus.RENTED);
        roomRepository.save(room);

        return contractRepository.save(contract);
    }

    @Override
    public Page<Contract> getContractsByOwner(Long ownerId, ContractStatus status, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return contractRepository.searchByOwnerIdAndStatusAndKeyword(ownerId, status, keyword, pageable);
        }
        return contractRepository.findByOwnerIdAndStatus(ownerId, status, pageable);
    }

    @Override
    public Contract getContractById(Long id) {
        return contractRepository.findById(id).orElseThrow(() -> new RuntimeException("Contract not found"));
    }

    @Override
    @Transactional
    public void updateContractStatus(Long id, ContractStatus status) {
        Contract contract = getContractById(id);
        contract.setStatus(status);
        contract.setUpdatedAt(LocalDateTime.now());
        
        // If contract is terminated/expired, maybe room becomes AVAILABLE?
        if(status == ContractStatus.TERMINATED || status == ContractStatus.EXPIRED){
            Room room = contract.getRoom();
            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);
        }
        
        contractRepository.save(contract);
    }

    @Override
    @Transactional
    public void checkoutContract(com.pms.propertymanagement.dto.request.ContractCheckoutRequest request, Long ownerId) {
        Contract contract = getContractById(request.getContractId());
        if (!contract.getRoom().getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác trên hợp đồng này");
        }

        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new IllegalStateException("Hợp đồng này đã được thanh lý trả phòng trước đó!");
        }

        // 1. Kiểm tra hóa đơn chưa thanh toán thực tế trong hệ thống (Server-side validation)
        List<com.pms.propertymanagement.entity.Invoice> unpaidInvoices = invoiceRepository.findByContract_IdAndStatus(contract.getId(), com.pms.propertymanagement.enums.InvoiceStatus.UNPAID);
        double systemUnpaidTotal = unpaidInvoices.stream()
                .mapToDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount() : 0.0)
                .sum();

        double utilityAmt = request.getUtilityAmount() != null ? Math.max(0.0, request.getUtilityAmount()) : 0.0;
        double unpaidRentAmt = (request.getUnpaidRentAmount() != null && request.getUnpaidRentAmount() > 0)
                ? Math.max(0.0, request.getUnpaidRentAmount())
                : systemUnpaidTotal;
        double damageAmt = request.getDamageAmount() != null ? Math.max(0.0, request.getDamageAmount()) : 0.0;
        double depositAmt = contract.getDeposit() != null ? Math.max(0.0, contract.getDeposit()) : 0.0;
        double totalDeduction = utilityAmt + unpaidRentAmt + damageAmt;

        // Tính toán quyết toán độc lập từ phía server (Chống giả mạo số tiền hoàn cọc / thu thêm)
        double refundAmt = Math.max(0.0, depositAmt - totalDeduction);
        double extraChargeAmt = Math.max(0.0, totalDeduction - depositAmt);

        // Nếu số cọc đủ bù tiền nợ, cập nhật hóa đơn nợ thành đã thanh toán qua cọc
        if (depositAmt >= unpaidRentAmt && !unpaidInvoices.isEmpty()) {
            for (com.pms.propertymanagement.entity.Invoice inv : unpaidInvoices) {
                inv.setStatus(com.pms.propertymanagement.enums.InvoiceStatus.PAID);
                inv.setPaidAmount(inv.getTotalAmount());
                inv.setRemainingAmount(0.0);
                inv.setUpdatedAt(LocalDateTime.now());
                inv.setTransferNote("Tất toán qua khấu trừ cọc khi thanh lý HĐ #" + contract.getCode());
                invoiceRepository.save(inv);
            }
        }

        contract.setStatus(ContractStatus.TERMINATED);
        contract.setUpdatedAt(LocalDateTime.now());

        // Ghi lại biên bản thanh lý vào contract note
        StringBuilder sb = new StringBuilder();
        if (contract.getNote() != null && !contract.getNote().isEmpty()) {
            sb.append(contract.getNote()).append("\n");
        }
        sb.append("--- BIÊN BẢN THANH LÝ TRẢ PHÒNG & QUYẾT TOÁN CỌC (").append(request.getCheckoutDate() != null ? request.getCheckoutDate() : java.time.LocalDate.now()).append(") ---\n");
        sb.append("+ Tiền cọc gốc đang giữ: ").append(String.format("%,.0f", depositAmt)).append(" đ\n");
        if (utilityAmt > 0) {
            sb.append("+ Khấu trừ điện nước chốt: ").append(String.format("%,.0f", utilityAmt)).append(" đ\n");
        }
        if (unpaidRentAmt > 0) {
            sb.append("+ Khấu trừ tiền phòng lẻ: ").append(String.format("%,.0f", unpaidRentAmt)).append(" đ\n");
        }
        if (damageAmt > 0) {
            sb.append("+ Bồi thường hư hại (").append(request.getDamageNote() != null ? request.getDamageNote() : "Thiết bị").append("): ")
              .append(String.format("%,.0f", damageAmt)).append(" đ\n");
        }
        sb.append("=> KẾT QUẢ QUYẾT TOÁN: ");
        if (refundAmt > 0) {
            sb.append("Hoàn trả khách thuê ").append(String.format("%,.0f", refundAmt)).append(" đ");
        } else if (extraChargeAmt > 0) {
            sb.append("Khách thuê nộp thêm ").append(String.format("%,.0f", extraChargeAmt)).append(" đ");
        } else {
            sb.append("Quyết toán cọc vừa đủ 0 đ");
        }
        if (request.getFinalNote() != null && !request.getFinalNote().isEmpty()) {
            sb.append("\nGhi chú: ").append(request.getFinalNote());
        }
        contract.setNote(sb.toString());

        // Cập nhật trạng thái phòng
        Room room = contract.getRoom();
        if (Boolean.TRUE.equals(request.getMarkRoomForMaintenance()) || (request.getDamageAmount() != null && request.getDamageAmount() > 0)) {
            room.setStatus(RoomStatus.MAINTENANCE);
        } else {
            room.setStatus(RoomStatus.AVAILABLE);
        }
        roomRepository.save(room);
        contractRepository.save(contract);
    }

    @Override
    @Transactional
    public void renewContract(Long contractId, LocalDate newEndDate, Double newRentPrice, User owner) {
        Contract contract = getContractById(contractId);
        if (owner == null || contract.getRoom() == null || contract.getRoom().getProperty() == null
                || contract.getRoom().getProperty().getOwner() == null
                || !contract.getRoom().getProperty().getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenException("Bạn không có quyền gia hạn hợp đồng này");
        }
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new IllegalStateException("Không thể gia hạn hợp đồng đã thanh lý!");
        }
        if (newEndDate == null || (contract.getStartDate() != null && !newEndDate.isAfter(contract.getStartDate()))) {
            throw new IllegalArgumentException("Ngày kết thúc gia hạn phải sau ngày bắt đầu hợp đồng!");
        }
        if (contract.getEndDate() != null && !newEndDate.isAfter(contract.getEndDate())) {
            throw new IllegalArgumentException("Ngày kết thúc mới phải sau ngày kết thúc hiện tại (" + contract.getEndDate() + ")!");
        }

        contract.setEndDate(newEndDate);
        if (newRentPrice != null && newRentPrice > 0) {
            contract.setRentPrice(newRentPrice);
        }

        Room room = contract.getRoom();
        if (contract.getStatus() == ContractStatus.EXPIRED && room.getStatus() == RoomStatus.AVAILABLE) {
            contract.setStatus(ContractStatus.ACTIVE);
            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);
        } else if (contract.getStatus() == ContractStatus.EXPIRING_SOON || contract.getStatus() == ContractStatus.EXPIRED) {
            contract.setStatus(ContractStatus.ACTIVE);
        }

        contract.setUpdatedAt(LocalDateTime.now());

        String existingNote = (contract.getNote() != null && !contract.getNote().isBlank())
                ? contract.getNote() + "\n"
                : "";
        String priceInfo = (newRentPrice != null && newRentPrice > 0)
                ? String.format(", giá thuê mới: %,.0f đ/tháng", newRentPrice)
                : "";
        contract.setNote(existingNote + String.format("[Gia hạn %s] Gia hạn đến ngày %s%s.",
                LocalDate.now(), newEndDate, priceInfo));

        contractRepository.save(contract);
    }
}
