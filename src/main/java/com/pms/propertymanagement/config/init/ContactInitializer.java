package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.Contact;
import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.ContactRepository;
import com.pms.propertymanagement.repository.PropertyRepository;
import com.pms.propertymanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContactInitializer {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    @Transactional
    public void init() {
        User owner = userRepository.findByUsername("owner1").orElse(null);
        if (owner == null) return;

        // If contacts already exist for this owner, skip
        if (!contactRepository.findByOwnerId(owner.getId()).isEmpty()) return;

        List<Property> properties = propertyRepository.findByOwnerId(owner.getId());
        if (properties.isEmpty()) return;

        Random random = new Random();
        List<Contact> contacts = new ArrayList<>();

        String[] firstNames = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng"};
        String[] middleNames = {"Văn", "Thị", "Hữu", "Minh", "Quốc", "Gia", "Bảo", "Ngọc", "Thanh", "Đức"};
        String[] lastNames = {"An", "Bình", "Cường", "Dũng", "Em", "Giang", "Hùng", "Hải", "Khánh", "Linh", "Minh", "Nam", "Oanh", "Phú", "Quân"};

        String[] notes = {
            "Cần thuê gấp trong tuần này",
            "Hỏi xem có chỗ để xe hơi không",
            "Muốn hẹn xem phòng vào thứ 7",
            "Sinh viên năm nhất muốn tìm phòng trọ",
            "Hỏi giá điện nước thế nào",
            "Gia đình 3 người muốn thuê lâu dài",
            "Có cho nuôi thú cưng không?",
            "Phòng này còn trống không?",
            "Muốn tìm phòng có ban công",
            "Đã gọi nhưng không bắt máy"
        };

        // Generate 15-20 contacts distributed among properties
        int totalContacts = 15 + random.nextInt(6);

        for (int i = 0; i < totalContacts; i++) {
            Property randomProp = properties.get(random.nextInt(properties.size()));
            String fullName = firstNames[random.nextInt(firstNames.length)] + " " +
                              middleNames[random.nextInt(middleNames.length)] + " " +
                              lastNames[random.nextInt(lastNames.length)];

            String phone = "09" + (random.nextInt(90000000) + 10000000);

            Contact c = new Contact();
            c.setName(fullName);
            c.setPhone(phone);
            c.setNote(notes[random.nextInt(notes.length)]);
            c.setOwner(owner);
            c.setProperty(randomProp);
            c.setIsChecked(random.nextBoolean());
            contacts.add(c);
        }

        contactRepository.saveAll(contacts);
        log.info("Generated {} sample contacts for owner1", contacts.size());
    }
}
