package com.pms.propertymanagement.service;

import com.pms.propertymanagement.dto.request.RoomRequest;
import com.pms.propertymanagement.dto.response.RoomResponse;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.enums.RoomStatus;

import java.util.List;

public interface RoomService {
    List<RoomResponse> getAllRoomsByOwner(User owner);
    void createRoom(RoomRequest request, User owner);
    void deleteRoom(Long id);
    void deleteRoom(Long id, User owner);
    void updateRoom(Long id, String name, Double price, Double deposit, Double area, Integer maxOccupancy, String description, User owner);
    void updateRoomStatus(Long id, RoomStatus status, User owner);
    List<RoomResponse> getAvailableRoomsByProperty(Long propertyId);
    List<com.pms.propertymanagement.entity.Room> getRoomsByPropertyId(Long propertyId);
    com.pms.propertymanagement.entity.Room getRoomById(Long id);
}
