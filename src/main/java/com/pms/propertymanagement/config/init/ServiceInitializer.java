package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.ServiceItem;
import com.pms.propertymanagement.enums.ServiceType;
import com.pms.propertymanagement.repository.PropertyRepository;
import com.pms.propertymanagement.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceInitializer {

    private final PropertyRepository propertyRepository;
    private final ServiceItemRepository serviceItemRepository;

    @Transactional
    public void init() {
        if (serviceItemRepository.count() > 0) return;

        List<Property> properties = propertyRepository.findAll();
        if (properties.isEmpty()) return;

        Random random = new Random();
        List<ServiceItem> services = new ArrayList<>();

        for (Property p : properties) {
            if (p.getCategory() != null &&
               (p.getCategory().getName().equalsIgnoreCase("Nhà trọ") ||
                p.getCategory().getName().equalsIgnoreCase("Ký túc xá") ||
                p.getCategory().getName().equalsIgnoreCase("Căn hộ"))) {

                services.add(createService(p, "Tiền điện", 3500.0 + random.nextInt(500), "Kwh", ServiceType.METERED));

                if (random.nextBoolean()) {
                    services.add(createService(p, "Tiền nước", 15000.0 + random.nextInt(5000), "Khối", ServiceType.METERED));
                } else {
                    services.add(createService(p, "Tiền nước", 100000.0, "Người/Tháng", ServiceType.FIXED));
                }

                services.add(createService(p, "Internet/Wifi", 50000.0 + random.nextInt(50000), "Phòng/Tháng", ServiceType.FIXED));
                services.add(createService(p, "Vệ sinh + Rác", 20000.0 + random.nextInt(10000), "Phòng/Tháng", ServiceType.FIXED));

                if (random.nextBoolean()) {
                    services.add(createService(p, "Giữ xe máy", 100000.0 + random.nextInt(50000), "Xe/Tháng", ServiceType.FIXED));
                }
            } else {
                if (random.nextDouble() > 0.7) {
                    services.add(createService(p, "Phí quản lý", 500000.0, "Tháng", ServiceType.FIXED));
                }
            }
        }

        serviceItemRepository.saveAll(services);
        log.info("Initialized {} service items for {} properties.", services.size(), properties.size());
    }

    private ServiceItem createService(Property property, String name, Double price, String unit, ServiceType type) {
        ServiceItem s = new ServiceItem();
        s.setName(name);
        s.setPrice(price);
        s.setUnit(unit);
        s.setType(type);
        s.setProperty(property);
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }
}
