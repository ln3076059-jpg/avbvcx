package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.dto.request.RoomRequest;
import com.pms.propertymanagement.dto.response.RoomResponse;
import com.pms.propertymanagement.entity.ManagementPlan;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.Room;
import com.pms.propertymanagement.entity.ServiceItem;
import com.pms.propertymanagement.entity.Subscription;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.RoomStatus;
import com.pms.propertymanagement.exception.ForbiddenException;
import com.pms.propertymanagement.repository.PropertyRepository;
import com.pms.propertymanagement.repository.RoomRepository;
import com.pms.propertymanagement.repository.ServiceItemRepository;
import com.pms.propertymanagement.service.ManagementPlanService;
import com.pms.propertymanagement.service.RoomService;
import com.pms.propertymanagement.service.SubscriptionManagementService;
import com.pms.propertymanagement.utils.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final SubscriptionManagementService subscriptionManagementService;
    private final ManagementPlanService managementPlanService;

    @Override
    public List<RoomResponse> getAllRoomsByOwner(User owner) {
        List<Room> rooms = roomRepository.findAllByOwnerUsername(owner.getUsername());
        return rooms.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createRoom(RoomRequest request, User owner) {
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new RuntimeException("Property not found"));

        // Validate owner owns the property
        if (owner == null || property.getOwner() == null || !property.getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenException("Bạn không có quyền thêm phòng cho nhà trọ này");
        }

        if (property.isPlanLocked()) {
            throw new IllegalStateException("Nhà trọ đang bị khóa theo gói quản lý, không thể thêm phòng mới");
        }

        Subscription activeSub = subscriptionManagementService.getActiveManagementSubscription(property.getOwner().getId());
        if (activeSub != null && activeSub.getManagementPlanId() != null) {
            ManagementPlan plan = managementPlanService.getById(activeSub.getManagementPlanId());
            if (plan != null && plan.getMaxRoomsPerProperty() != -1) {
                int currentRoomCount = roomRepository.findByProperty_Id(property.getId()).size();
                if (currentRoomCount >= plan.getMaxRoomsPerProperty()) {
                    throw new IllegalStateException("Nhà trọ đã đạt giới hạn tối đa " + plan.getMaxRoomsPerProperty()
                            + " phòng theo gói quản lý " + plan.getName());
                }
            }
        }

        Room room = new Room();
        room.setName(request.getName());
        room.setPrice(request.getPrice());
        room.setDeposit(request.getDeposit());
        room.setArea(request.getArea());
        room.setMaxOccupancy(request.getMaxOccupancy());
        room.setBedCount(request.getBedCount());
        room.setPaymentCycle(request.getPaymentCycle());
        room.setIsElectricityWaterIncluded(request.getIsElectricityWaterIncluded());
        room.setDescription(request.getDescription());
        room.setProperty(property);
        room.setStatus(RoomStatus.AVAILABLE);

        if (request.getServiceIds() != null && !request.getServiceIds().isEmpty()) {
            List<ServiceItem> services = serviceItemRepository.findAllById(request.getServiceIds()).stream()
                    .filter(s -> s.getProperty() != null && s.getProperty().getId().equals(property.getId()))
                    .collect(Collectors.toList());
            room.setServices(new HashSet<>(services));
        }

        roomRepository.save(room);
        
        property.setNumberOfRooms(property.getNumberOfRooms() + 1);
        propertyRepository.save(property);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        deleteRoom(id, null);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id, User owner) {
        Room room = roomRepository.findById(id).orElseThrow(() -> new RuntimeException("Room not found"));
        if (owner != null && (room.getProperty() == null || room.getProperty().getOwner() == null
                || !room.getProperty().getOwner().getId().equals(owner.getId()))) {
            throw new ForbiddenException("Bạn không có quyền xóa phòng này");
        }
        if (room.getStatus() == RoomStatus.RENTED) {
            throw new IllegalStateException("Không thể xóa phòng " + room.getName() + " vì đang có khách thuê!");
        }
        Property property = room.getProperty();
        roomRepository.deleteById(id);
        
        // Update count
        if (property != null && property.getNumberOfRooms() > 0) {
            property.setNumberOfRooms(property.getNumberOfRooms() - 1);
            propertyRepository.save(property);
        }
    }

    @Override
    @Transactional
    public void updateRoom(Long id, String name, Double price, Double deposit, Double area, Integer maxOccupancy, String description, User owner) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng"));
        if (owner == null || room.getProperty() == null || room.getProperty().getOwner() == null
                || !room.getProperty().getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenException("Bạn không có quyền cập nhật thông tin phòng này");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên phòng không được để trống!");
        }
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("Giá thuê phòng phải lớn hơn 0!");
        }
        room.setName(name.trim());
        room.setPrice(price);
        room.setDeposit(deposit);
        room.setArea(area);
        room.setMaxOccupancy(maxOccupancy);
        room.setDescription(description != null ? description.trim() : null);
        roomRepository.save(room);
    }

    @Override
    @Transactional
    public void updateRoomStatus(Long id, RoomStatus status, User owner) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng"));
        if (owner == null || room.getProperty() == null || room.getProperty().getOwner() == null
                || !room.getProperty().getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenException("Bạn không có quyền cập nhật trạng thái phòng này");
        }
        if (room.getStatus() == RoomStatus.RENTED) {
            throw new IllegalStateException("Không thể thay đổi trạng thái phòng đang có khách thuê!");
        }
        if (status == RoomStatus.RENTED) {
            throw new IllegalArgumentException("Trạng thái Đã thuê chỉ được thiết lập thông qua hợp đồng!");
        }
        room.setStatus(status);
        roomRepository.save(room);
    }

    @Override
    public List<RoomResponse> getAvailableRoomsByProperty(Long propertyId) {
        List<Room> rooms = roomRepository.findByPropertyIdAndStatus(propertyId, RoomStatus.AVAILABLE);
        return rooms.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private RoomResponse mapToResponse(Room room) {
        String statusStyle = switch (room.getStatus()) {
            case AVAILABLE -> "bg-green-100 text-green-700";
            case RENTED -> "bg-red-100 text-red-700";
            case MAINTENANCE -> "bg-yellow-100 text-yellow-700";
        };

        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .price(room.getPrice())
                .formattedPrice(CurrencyUtil.formatVND(room.getPrice()))
                .deposit(room.getDeposit())
                .area(room.getArea())
                .maxOccupancy(room.getMaxOccupancy())
                .description(room.getDescription())
                .propertyId(room.getProperty() != null ? room.getProperty().getId() : null)
                .propertyName(room.getProperty().getName())
                .status(room.getStatus().getDisplayName())
                .statusStyle(statusStyle)
                .build();
    }

    @Override
    public List<Room> getRoomsByPropertyId(Long propertyId) {
        return roomRepository.findByProperty_Id(propertyId);
    }

    @Override
    public Room getRoomById(Long id) {
        return roomRepository.findById(id).orElse(null);
    }
}
