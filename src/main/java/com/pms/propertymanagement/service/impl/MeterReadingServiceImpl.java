package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.MeterReadingRequest;
import com.pms.propertymanagement.entity.Invoice;
import com.pms.propertymanagement.entity.InvoiceDetail;
import com.pms.propertymanagement.entity.MeterReading;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.ServiceItem;
import com.pms.propertymanagement.enums.InvoiceDetailType;
import com.pms.propertymanagement.enums.InvoiceStatus;
import com.pms.propertymanagement.repository.InvoiceRepository;
import com.pms.propertymanagement.repository.MeterReadingRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.service.MeterReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MeterReadingServiceImpl implements MeterReadingService {

    private final MeterReadingRepository meterReadingRepository;
    private final RoomRepository roomRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    public Page<MeterReading> searchReadings(Long ownerId, Long propertyId, Long roomId, Integer month, Integer year, Pageable pageable) {
        return meterReadingRepository.searchReadings(ownerId, propertyId, roomId, month, year, pageable);
    }

    @Override
    public MeterReading getReadingById(Long id, Long ownerId) {
        MeterReading reading = meterReadingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi chỉ số điện nước (ID: " + id + ")"));

        if (!reading.getRoom().getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền truy cập chỉ số điện nước này");
        }
        return reading;
    }

    @Override
    @Transactional
    public MeterReading saveReading(MeterReadingRequest request, Long ownerId) {
        if (request.getMonth() == null || request.getMonth() < 1 || request.getMonth() > 12) {
            throw new IllegalArgumentException("Tháng ghi chỉ số không hợp lệ (1-12)!");
        }
        if (request.getYear() == null || request.getYear() < 2000 || request.getYear() > 2100) {
            throw new IllegalArgumentException("Năm ghi chỉ số không hợp lệ!");
        }
        if (request.getOldElectricityIndex() == null || request.getNewElectricityIndex() == null
                || request.getOldElectricityIndex() < 0 || request.getNewElectricityIndex() < 0) {
            throw new IllegalArgumentException("Chỉ số điện đầu kỳ và cuối kỳ không được nhỏ hơn 0!");
        }
        if (request.getOldWaterIndex() == null || request.getNewWaterIndex() == null
                || request.getOldWaterIndex() < 0 || request.getNewWaterIndex() < 0) {
            throw new IllegalArgumentException("Chỉ số nước đầu kỳ và cuối kỳ không được nhỏ hơn 0!");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng với ID: " + request.getRoomId()));

        if (!room.getProperty().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác trên phòng này");
        }

        boolean isReset = Boolean.TRUE.equals(request.getIsReset());

        // 1. Validation logic: không cho chỉ số mới nhỏ hơn chỉ số cũ trừ khi thay công tơ/quay vòng
        if (!isReset) {
            if (request.getNewElectricityIndex() < request.getOldElectricityIndex()) {
                throw new IllegalArgumentException("Chỉ số điện mới (" + request.getNewElectricityIndex() +
                        ") không được nhỏ hơn chỉ số điện cũ (" + request.getOldElectricityIndex() + ")");
            }

            if (request.getNewWaterIndex() < request.getOldWaterIndex()) {
                throw new IllegalArgumentException("Chỉ số nước mới (" + request.getNewWaterIndex() +
                        ") không được nhỏ hơn chỉ số nước cũ (" + request.getOldWaterIndex() + ")");
            }
        }

        // Kiểm tra nếu tháng đó đã có hóa đơn PAID thì không cho phép sửa chỉ số điện nước
        Optional<Invoice> existingInvoiceOpt = invoiceRepository.findByRoom_IdAndMonthAndYear(
                request.getRoomId(), request.getMonth(), request.getYear());
        if (existingInvoiceOpt.isPresent() && existingInvoiceOpt.get().getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Hóa đơn tháng " + request.getMonth() + "/" + request.getYear()
                    + " của phòng " + room.getName() + " đã thanh toán hoàn tất, không thể thay đổi chỉ số điện nước!");
        }

        MeterReading reading;
        if (request.getId() != null) {
            reading = meterReadingRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi cần chỉnh sửa"));
            if (!reading.getRoom().getProperty().getOwner().getId().equals(ownerId)) {
                throw new IllegalArgumentException("Bạn không có quyền chỉnh sửa bản ghi này");
            }
            if (!reading.getRoom().getId().equals(request.getRoomId()) ||
                !reading.getMonth().equals(request.getMonth()) ||
                !reading.getYear().equals(request.getYear())) {
                if (meterReadingRepository.existsByRoom_IdAndMonthAndYear(request.getRoomId(), request.getMonth(), request.getYear())) {
                    throw new IllegalArgumentException("Phòng đã có chỉ số cho tháng " + request.getMonth() + "/" + request.getYear());
                }
            }
        } else {
            if (meterReadingRepository.existsByRoom_IdAndMonthAndYear(request.getRoomId(), request.getMonth(), request.getYear())) {
                throw new IllegalArgumentException("Phòng " + room.getName() + " đã có chỉ số điện nước cho tháng " +
                        request.getMonth() + "/" + request.getYear());
            }

            if (!isReset) {
                Optional<MeterReading> latestOpt = meterReadingRepository.findLatestBefore(
                        request.getRoomId(), request.getMonth(), request.getYear());
                if (latestOpt.isPresent()) {
                    MeterReading latest = latestOpt.get();
                    if (request.getOldElectricityIndex() < latest.getNewElectricityIndex()) {
                        throw new IllegalArgumentException("Chỉ số điện đầu kỳ (" + request.getOldElectricityIndex() +
                                ") không được nhỏ hơn chỉ số cuối kỳ trước đó (" + latest.getNewElectricityIndex() + ")");
                    }
                    if (request.getOldWaterIndex() < latest.getNewWaterIndex()) {
                        throw new IllegalArgumentException("Chỉ số nước đầu kỳ (" + request.getOldWaterIndex() +
                                ") không được nhỏ hơn chỉ số cuối kỳ trước đó (" + latest.getNewWaterIndex() + ")");
                    }
                }
            }

            reading = new MeterReading();
            reading.setCreatedAt(LocalDateTime.now());
        }

        reading.setRoom(room);
        reading.setMonth(request.getMonth());
        reading.setYear(request.getYear());
        reading.setIsReset(isReset);
        reading.setOldElectricityIndex(request.getOldElectricityIndex());
        reading.setNewElectricityIndex(request.getNewElectricityIndex());

        double elecUsage = (isReset && request.getNewElectricityIndex() < request.getOldElectricityIndex())
                ? request.getNewElectricityIndex()
                : (request.getNewElectricityIndex() - request.getOldElectricityIndex());
        reading.setElectricityUsage(elecUsage);

        reading.setOldWaterIndex(request.getOldWaterIndex());
        reading.setNewWaterIndex(request.getNewWaterIndex());

        double waterUsage = (isReset && request.getNewWaterIndex() < request.getOldWaterIndex())
                ? request.getNewWaterIndex()
                : (request.getNewWaterIndex() - request.getOldWaterIndex());
        reading.setWaterUsage(waterUsage);

        Double electricityPrice = request.getElectricityPrice();
        if (electricityPrice == null || electricityPrice <= 0) {
            electricityPrice = resolveElectricityPrice(room);
        }
        reading.setElectricityPrice(electricityPrice);

        Double waterPrice = request.getWaterPrice();
        if (waterPrice == null || waterPrice <= 0) {
            waterPrice = resolveWaterPrice(room);
        }
        reading.setWaterPrice(waterPrice);

        reading.setUpdatedAt(LocalDateTime.now());

        MeterReading savedReading = meterReadingRepository.save(reading);

        // Đồng bộ tự động sang hóa đơn chưa thanh toán hết (UNPAID / PARTIALLY_PAID / OVERDUE) nếu đã lập
        if (existingInvoiceOpt.isPresent()) {
            Invoice inv = existingInvoiceOpt.get();
            boolean isElecWaterIncluded = inv.getContract() != null && Boolean.TRUE.equals(inv.getContract().getIsElectricityWaterIncluded());
            if (inv.getStatus() != InvoiceStatus.CANCELLED && !isElecWaterIncluded) {
                double newElecAmount = (double) Math.round(elecUsage * electricityPrice);
                double newWaterAmount = (double) Math.round(waterUsage * waterPrice);
                inv.setElectricityUsage(elecUsage);
                inv.setElectricityPrice(electricityPrice);
                inv.setElectricityAmount(newElecAmount);
                inv.setWaterUsage(waterUsage);
                inv.setWaterPrice(waterPrice);
                inv.setWaterAmount(newWaterAmount);

                if (inv.getDetails() != null) {
                    for (InvoiceDetail d : inv.getDetails()) {
                        if (d.getType() == InvoiceDetailType.ELECTRICITY) {
                            d.setDescription("Tiền điện (" + elecUsage + " kWh x " + String.format("%,.0f", electricityPrice) + " đ)");
                            d.setQuantity(elecUsage);
                            d.setUnitPrice(electricityPrice);
                            d.setAmount(newElecAmount);
                        } else if (d.getType() == InvoiceDetailType.WATER) {
                            d.setDescription("Tiền nước (" + waterUsage + " m³ x " + String.format("%,.0f", waterPrice) + " đ)");
                            d.setQuantity(waterUsage);
                            d.setUnitPrice(waterPrice);
                            d.setAmount(newWaterAmount);
                        }
                    }
                }
                inv.recalculateTotals();
                invoiceRepository.save(inv);
            }
        }

        return savedReading;
    }

    @Override
    @Transactional
    public void deleteReading(Long id, Long ownerId) {
        MeterReading reading = getReadingById(id, ownerId);
        Optional<Invoice> invoiceOpt = invoiceRepository.findByRoom_IdAndMonthAndYear(
                reading.getRoom().getId(), reading.getMonth(), reading.getYear());
        if (invoiceOpt.isPresent() && invoiceOpt.get().getStatus() != InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Không thể xóa chỉ số điện nước vì phòng " + reading.getRoom().getName()
                    + " đã phát sinh hóa đơn (" + invoiceOpt.get().getInvoiceCode() + ") cho tháng "
                    + reading.getMonth() + "/" + reading.getYear() + ". Vui lòng xóa hóa đơn trước khi xóa chỉ số!");
        }
        meterReadingRepository.delete(reading);
    }

    @Override
    public Optional<MeterReading> getLatestReading(Long roomId) {
        return meterReadingRepository.findTopByRoom_IdOrderByYearDescMonthDesc(roomId);
    }

    @Override
    public Optional<MeterReading> getReadingByRoomAndMonthAndYear(Long roomId, Integer month, Integer year) {
        return meterReadingRepository.findByRoom_IdAndMonthAndYear(roomId, month, year);
    }

    private Double resolveElectricityPrice(Room room) {
        if (room.getServices() != null) {
            for (ServiceItem s : room.getServices()) {
                if (InvoiceServiceImpl.isElectricityService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        if (room.getProperty() != null && room.getProperty().getServiceItems() != null) {
            for (ServiceItem s : room.getProperty().getServiceItems()) {
                if (InvoiceServiceImpl.isElectricityService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        return 3500.0; // Mặc định 3.500 đ/kWh
    }

    private Double resolveWaterPrice(Room room) {
        if (room.getServices() != null) {
            for (ServiceItem s : room.getServices()) {
                if (InvoiceServiceImpl.isWaterService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        if (room.getProperty() != null && room.getProperty().getServiceItems() != null) {
            for (ServiceItem s : room.getProperty().getServiceItems()) {
                if (InvoiceServiceImpl.isWaterService(s) && s.getPrice() != null && s.getPrice() > 0) {
                    return s.getPrice();
                }
            }
        }
        return 20000.0; // Mặc định 20.000 đ/m3
    }
}
