package com.pms.propertymanagement.config.init;

import com.pms.propertymanagement.entity.*;
import com.pms.propertymanagement.enums.GeocodeStatus;
import com.pms.propertymanagement.repository.*;
import com.pms.propertymanagement.utils.SlugUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PropertyInitializer {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final WardRepository wardRepository;
    private final CategoryRepository categoryRepository;
    private final AmenityRepository amenityRepository;
    private final SurroundingRepository surroundingRepository;
    private final TargetTenantsRepository targetTenantsRepository;

    @Transactional
    public void init() {
        if (!propertyRepository.findAll().isEmpty()) return;

        User owner = userRepository.findByUsername("owner1").orElse(null);
        if (owner == null) return;

        // Load reference data
        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categories.put(c.getName(), c));
        
        List<Amenity> allAmenities = amenityRepository.findAll();
        List<Surrounding> allSurroundings = surroundingRepository.findAll();
        List<TargetTenant> allTargets = targetTenantsRepository.findAll();
        List<Ward> allWards = wardRepository.findAll();
        
        if (categories.isEmpty() || allWards.isEmpty()) return;

        Random random = new Random();
        List<Property> properties = new ArrayList<>();

        // 1. NHÀ TRỌ (5 Properties)
        Category catNhaTro = categories.get("Nhà trọ");
        if (catNhaTro != null) {
            properties.add(createProperty(owner, catNhaTro, allWards, allAmenities, allSurroundings, allTargets,
                "Nhà trọ 341", "Phòng trọ cao cấp ngay trung tâm Quận 8", "341 Bùi Minh Trực", 
                12.2, 2500000, 2, "Phòng mới xây có gác lửng, giờ giấc tự do, không chung chủ."));
            
            properties.add(createProperty(owner, catNhaTro, allWards, allAmenities, allSurroundings, allTargets,
                "Trọ Cô Ba", "Phòng trọ giá rẻ cho sinh viên gần ĐH Hutech", "152/12 Điện Biên Phủ", 
                15.0, 3000000, 5, "Cách đại học Hutech 500m, điện nước giá nhà nước, an ninh tốt."));
            
            properties.add(createProperty(owner, catNhaTro, allWards, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Xanh", "Phòng trọ khép kín đầy đủ tiện nghi Bình Thạnh", "25 Nguyễn Xí", 
                20.0, 4500000, 3, "Phòng full nội thất: Máy lạnh, tủ lạnh, giường nệm. Chỉ cần xách vali vào ở."));
            
            properties.add(createProperty(owner, catNhaTro, allWards, allAmenities, allSurroundings, allTargets,
                "Trọ An Nhiên", "Phòng trọ yên tĩnh thoáng mát Quận 7", "45 Lâm Văn Bền", 
                18.0, 3200000, 4, "Khu dân trí cao, yên tĩnh, thích hợp cho nhân viên văn phòng."));
                
            properties.add(createProperty(owner, catNhaTro, allWards, allAmenities, allSurroundings, allTargets,
                "Happy House", "Hệ thống phòng trọ tiện ích Thủ Đức", "102 Lê Văn Việt", 
                25.0, 4000000, 8, "Có thang máy, hầm để xe rộng rãi, bảo vệ 24/7."));
        }

        // 2. NHÀ NGUYÊN CĂN (5 Properties)
        Category catNguyenCan = categories.get("Nhà nguyên căn");
        if (catNguyenCan != null) {
            properties.add(createProperty(owner, catNguyenCan, allWards, allAmenities, allSurroundings, allTargets,
                "Nhà Mặt Tiền Lê Lợi", "Cho thuê nhà nguyên căn mặt tiền kinh doanh", "12 Lê Lợi", 
                80.0, 15000000, 1, "Nhà 1 trệt 2 lầu, mặt tiền đường lớn, thích hợp mở văn phòng hoặc shop."));
            
            properties.add(createProperty(owner, catNguyenCan, allWards, allAmenities, allSurroundings, allTargets,
                "Nhà Hẻm Xe Hơi Q3", "Nhà nguyên căn hẻm xe hơi yên tĩnh Quận 3", "20/5 Võ Văn Tần", 
                60.0, 12000000, 1, "Nhà mới sửa, sạch sẽ, khu vực an ninh, gần chợ và siêu thị."));
                
            properties.add(createProperty(owner, catNguyenCan, allWards, allAmenities, allSurroundings, allTargets,
                "Biệt thự mini Thảo Điền", "Cho thuê biệt thự mini sân vườn Thảo Điền", "15 Quốc Hương", 
                200.0, 35000000, 1, "Không gian xanh mát, hồ bơi riêng, nội thất cao cấp sang trọng."));
            
            properties.add(createProperty(owner, catNguyenCan, allWards, allAmenities, allSurroundings, allTargets,
                "Nhà phố KDC Him Lam", "Nhà phố nguyên căn KDC Him Lam Q7", "Đường số 5", 
                100.0, 18000000, 1, "Khu dân cư cao cấp, đường rộng 12m, gần Lotte Mart."));

            properties.add(createProperty(owner, catNguyenCan, allWards, allAmenities, allSurroundings, allTargets,
                "Nhà cấp 4 Gò Vấp", "Cho thuê nhà cấp 4 có gác lửng Gò Vấp", "120 Quang Trung", 
                40.0, 6000000, 1, "Nhà nhỏ xinh, phù hợp gia đình trẻ, điện nước chính chủ."));
        }

        // 3. CĂN HỘ (5 Properties)
        Category catCanHo = categories.get("Căn hộ");
        if (catCanHo != null) {
            properties.add(createProperty(owner, catCanHo, allWards, allAmenities, allSurroundings, allTargets,
                "Vinhomes Central Park", "Căn hộ 2PN View sông Landmark 81", "208 Nguyễn Hữu Cảnh", 
                75.0, 22000000, 1, "View trực diện sông Sài Gòn, full nội thất cao cấp, tiện ích 5 sao."));
            
            properties.add(createProperty(owner, catCanHo, allWards, allAmenities, allSurroundings, allTargets,
                "Masteri Thảo Điền", "Căn hộ 1PN Masteri Thảo Điền giá tốt", "159 Xa Lộ Hà Nội", 
                50.0, 14000000, 1, "Tầng trung, view thoáng, ngay ga Metro, có gym hồ bơi miễn phí."));
            
            properties.add(createProperty(owner, catCanHo, allWards, allAmenities, allSurroundings, allTargets,
                "Sunrise City", "Căn hộ Sunrise City Quận 7 - 3PN", "23 Nguyễn Hữu Thọ", 
                120.0, 28000000, 1, "Căn góc 3 view, nội thất gỗ tự nhiên, đối diện Lotte Mart."));
            
            properties.add(createProperty(owner, catCanHo, allWards, allAmenities, allSurroundings, allTargets,
                "The Gold View", "Cho thuê căn hộ Gold View Quận 4", "346 Bến Vân Đồn", 
                80.0, 17000000, 1, "Gần trung tâm Q1, view sông, đầy đủ tiện nghi, hồ bơi tràn bờ."));

            properties.add(createProperty(owner, catCanHo, allWards, allAmenities, allSurroundings, allTargets,
                "Ehome 3 Bình Tân", "Căn hộ giá rẻ Ehome 3 full nội thất", "102 Hồ Học Lãm", 
                60.0, 6500000, 1, "Khu chung cư yên tĩnh, nhiều cây xanh, phí quản lý thấp."));
        }

        // 4. KÝ TÚC XÁ (5 Properties)
        Category catKTX = categories.get("Ký túc xá");
        if (catKTX != null) {
            properties.add(createProperty(owner, catKTX, allWards, allAmenities, allSurroundings, allTargets,
                "KTX Cao Cấp Gò Vấp", "Giường tầng cao cấp máy lạnh 24/24 Gò Vấp", "50 Phạm Văn Đồng", 
                15.0, 1800000, 20, "Giường hộp riêng tư, bao điện nước, wifi tốc độ cao."));
            
            properties.add(createProperty(owner, catKTX, allWards, allAmenities, allSurroundings, allTargets,
                "Sleepbox Quận 10", "Sleepbox tiện nghi gần ĐH Bách Khoa", "268 Lý Thường Kiệt", 
                10.0, 2200000, 16, "Mô hình sleepbox hiện đại, khóa từ riêng, bếp chung sạch sẽ."));
            
            properties.add(createProperty(owner, catKTX, allWards, allAmenities, allSurroundings, allTargets,
                "KTX Sinh Viên Làng ĐH", "Ký túc xá giá rẻ khu Làng Đại Học", "Khu phố 6", 
                20.0, 800000, 50, "Môi trường học tập tốt, an ninh, gần trạm xe buýt, giá cực rẻ."));
            
            properties.add(createProperty(owner, catKTX, allWards, allAmenities, allSurroundings, allTargets,
                "Homestay 1990", "Homestay ký túc xá phong cách Vintage", "100 Trần Hưng Đạo", 
                18.0, 1900000, 12, "Không gian chill, ban công rộng, tổ chức tiệc BBQ hàng tuần."));

            properties.add(createProperty(owner, catKTX, allWards, allAmenities, allSurroundings, allTargets,
                "KTX Nữ Bình Thạnh", "Ký túc xá dành riêng cho nữ an ninh", "30 Đường D2", 
                25.0, 1600000, 10, "Chỉ nhận nữ, sạch sẽ, có camera giám sát, giờ giấc 11h đóng cửa."));
        }

        // =========================================================================
        // TEST DATA — Tăng Nhơn Phú & Phước Long (Thủ Đức) + Làng Đại Học (HCM + BD)
        // Phân bố budget để cover 3 case của REFINING:
        //   HCM "Dưới 2 triệu"  →  ~14 posts → REFINING_FEW  (6-20)
        //   HCM "2 – 4 triệu"   →  ~26 posts → REFINING_MANY (>20)
        //   HCM "7 – 15 triệu"  →   ~5 posts → REFINING_SKIP (≤5)
        // =========================================================================
        Ward wardTNP = findWardByName(allWards, "Tăng Nhơn Phú");
        Ward wardPL  = findWardByName(allWards, "Phường Phước Long");
        Ward wardLX  = findWardByName(allWards, "Phường Linh Xuân");
        Ward wardDH  = findWardByName(allWards, "Phường Đông Hòa");

        // ── 12 nhà trọ · Tăng Nhơn Phú (gần Khu CNC Thủ Đức) ──────────────────
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Anh Khôi",         "312/5 Lê Văn Việt",         18.0, 1_500_000, 4, 10.8448, 106.8035));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Thắng Lợi",        "45 Đường Tăng Nhơn Phú",    20.0, 1_800_000, 5, 10.8461, 106.8051));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Phòng trọ Minh Đức",       "28 Hẻm 62 Lê Văn Việt",     16.0, 2_000_000, 6, 10.8439, 106.8028));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Bà Mai",           "150/3 Đường D14",            22.0, 2_200_000, 4, 10.8472, 106.8063));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Phòng trọ Ánh Dương",      "67 Hẻm 24 Đường Số 8",      20.0, 2_500_000, 5, 10.8455, 106.8039));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Hương Giang",      "99 Đường D5",                24.0, 2_800_000, 4, 10.8467, 106.8055));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Phòng trọ Tiến Thành",     "33/1 Tăng Nhơn Phú A",      25.0, 3_000_000, 6, 10.8443, 106.8031));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Phúc Lợi",         "78 Đường Linh Đông",         28.0, 3_200_000, 5, 10.8479, 106.8067));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Văn Minh",         "210 Lê Văn Việt",            30.0, 4_000_000, 4, 10.8452, 106.8047));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Phòng trọ cao cấp TNP",    "55 Đường Số 3 Tăng Nhơn Phú",35.0, 5_000_000, 3, 10.8464, 106.8059));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Thanh Niên TNP",   "188 Đường Linh Trung",       32.0, 5_500_000, 3, 10.8435, 106.8025));
        properties.add(createPropertyAt(owner, catNhaTro, wardTNP, allAmenities, allSurroundings, allTargets,
                "Phòng mini Khu CNC",       "36 Đường Số 12",             40.0, 7_000_000, 2, 10.8476, 106.8071));

        // ── 8 nhà trọ · Phường Phước Long (Thủ Đức) ────────────────────────────
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Cô Hoa",           "102 Đường Số 6",             18.0, 1_500_000, 5, 10.8261, 106.7841));
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Phòng trọ Anh Hùng",       "234 Lê Văn Việt",            20.0, 2_000_000, 4, 10.8278, 106.7858));
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Bình Yên PL",      "56/4 Hẻm 30 Phước Long",    22.0, 2_500_000, 6, 10.8255, 106.7835));
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Nhà trọ An Khang PL",      "88 Đường Phước Long",        25.0, 3_000_000, 5, 10.8285, 106.7865));
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Phòng trọ Vạn Phát",       "111 Đường D7",               28.0, 3_500_000, 4, 10.8272, 106.7852));
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Đăng Khoa",        "40 Liên Phường",             30.0, 4_500_000, 3, 10.8264, 106.7845));
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Phan Linh",        "75 Đường Số 9 Phước Long",   35.0, 6_000_000, 2, 10.8281, 106.7861));
        properties.add(createPropertyAt(owner, catNhaTro, wardPL, allAmenities, allSurroundings, allTargets,
                "Phòng studio Phước Long",  "62 Liên Khu 1-2",            45.0, 8_000_000, 2, 10.8258, 106.7838));

        // ── 5 nhà trọ + KTX · Phường Linh Xuân (gần Làng ĐH ĐHQG, phía HCM) ──
        properties.add(createPropertyAt(owner, catKTX, wardLX, allAmenities, allSurroundings, allTargets,
                "KTX Làng ĐH Linh Xuân 1",  "Khu phố 1 Linh Xuân",       12.0,   800_000, 20, 10.8708, 106.7633));
        properties.add(createPropertyAt(owner, catKTX, wardLX, allAmenities, allSurroundings, allTargets,
                "KTX Làng ĐH Linh Xuân 2",  "Khu phố 2 Linh Xuân",       14.0, 1_000_000, 16, 10.8725, 106.7649));
        properties.add(createPropertyAt(owner, catNhaTro, wardLX, allAmenities, allSurroundings, allTargets,
                "Nhà trọ SV Linh Xuân",     "45 Đường Kha Vạn Cân",      18.0, 1_200_000,  8, 10.8701, 106.7627));
        properties.add(createPropertyAt(owner, catNhaTro, wardLX, allAmenities, allSurroundings, allTargets,
                "Phòng trọ cạnh Làng ĐH",   "120 Linh Xuân",              20.0, 1_500_000,  6, 10.8732, 106.7656));
        properties.add(createPropertyAt(owner, catNhaTro, wardLX, allAmenities, allSurroundings, allTargets,
                "Nhà trọ Gần ĐHQG HCM",    "88 Đường Số 8 Linh Xuân",   22.0, 2_000_000,  5, 10.8719, 106.7644));

        // ── 4 nhà trọ + KTX · Phường Đông Hòa, Bình Dương (gần Làng ĐH, phía BD) ──
        properties.add(createPropertyAt(owner, catKTX, wardDH, allAmenities, allSurroundings, allTargets,
                "KTX ĐHQG Bình Dương 1",    "Làng Đại Học Đông Hòa",     10.0,   700_000, 30, 10.9006, 106.7566));
        properties.add(createPropertyAt(owner, catKTX, wardDH, allAmenities, allSurroundings, allTargets,
                "KTX ĐHQG Bình Dương 2",    "Khu nhà ở SV ĐHQG BD",      12.0,   900_000, 20, 10.9022, 106.7582));
        properties.add(createPropertyAt(owner, catNhaTro, wardDH, allAmenities, allSurroundings, allTargets,
                "Nhà trọ SV Đông Hòa",      "55 Đường N8 Đông Hòa",      18.0, 1_300_000,  6, 10.8999, 106.7559));
        properties.add(createPropertyAt(owner, catNhaTro, wardDH, allAmenities, allSurroundings, allTargets,
                "Nhà trọ cạnh ĐHQG BD",     "88 Đường Số 4 Đông Hòa",    22.0, 1_800_000,  5, 10.9029, 106.7589));

        propertyRepository.saveAll(properties);
    }

    private Property createProperty(User owner, Category category, List<Ward> wards,
                                    List<Amenity> allAmenities, List<Surrounding> allSurroundings, 
                                    List<TargetTenant> allTargets,
                                    String name, String title, String address, double acreage, 
                                    int price, int rooms, String desc) {
        Random random = new Random();
        Property p = new Property();
        p.setName(name);
        p.setAddressNumber(address);
        p.setOwner(owner);
        p.setCategory(category);
        p.setAcreage(acreage);
        p.setPrice(price);
        p.setNumberOfRooms(rooms);
        p.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
        p.setUpdatedAt(LocalDateTime.now());
        
        // Random Ward from list
        p.setWard(wards.get(random.nextInt(wards.size())));
        
        // NEW ARCHITECTURE: Property only contains real estate fields
        // Marketing fields (title, slug, description) will be added by PostInitializer

        // Random Amenities (2-5 items)
        if (!allAmenities.isEmpty()) {
            Collections.shuffle(allAmenities);
            p.setAmenities(new HashSet<>(allAmenities.subList(0, Math.min(allAmenities.size(), random.nextInt(4) + 2))));
        }

        // Random Surroundings (2-4 items)
        if (!allSurroundings.isEmpty()) {
            Collections.shuffle(allSurroundings);
            p.setSurroundings(new HashSet<>(allSurroundings.subList(0, Math.min(allSurroundings.size(), random.nextInt(3) + 2))));
        }

        // Random Target Tenants (1-3 items)
        if (!allTargets.isEmpty()) {
            Collections.shuffle(allTargets);
            p.setTargetTenants(new HashSet<>(allTargets.subList(0, Math.min(allTargets.size(), random.nextInt(3) + 1))));
        }

        String[] sampleImages = {
            "/images/rooms/room-1.jpg", "/images/rooms/room-2.jpg", "/images/rooms/room-3.jpg", "/images/rooms/room-4.jpg",
            "/images/rooms/room-5.jpg", "/images/rooms/room-6.jpg", "/images/rooms/room-7.jpg", "/images/rooms/room-8.jpg",
            "/images/rooms/room-9.jpg", "/images/rooms/room-10.jpg", "/images/rooms/room-11.jpg", "/images/rooms/room-12.jpg"
        };
        List<PropertyImage> images = new ArrayList<>();
        int baseIdx = random.nextInt(sampleImages.length);
        for (int i = 0; i < 4; i++) {
            PropertyImage img = new PropertyImage();
            img.setImageUrl(sampleImages[(baseIdx + i) % sampleImages.length]);
            img.setIsPrimary(i == 0);
            img.setProperty(p);
            images.add(img);
        }
        p.setImages(images);

        return p;
    }

    /** Finds a ward by exact name; falls back to first ward if not found. */
    private Ward findWardByName(List<Ward> wards, String name) {
        return wards.stream()
                .filter(w -> w.getName().equals(name))
                .findFirst()
                .orElseGet(() -> wards.get(0));
    }

    /**
     * Creates a Property at a specific ward with hardcoded coordinates.
     * geocodeStatus is set to SUCCESS so bounding-box queries work immediately.
     */
    private Property createPropertyAt(User owner, Category category, Ward ward,
                                       List<Amenity> allAmenities, List<Surrounding> allSurroundings,
                                       List<TargetTenant> allTargets,
                                       String name, String address, double acreage,
                                       int price, int rooms, Double lat, Double lng) {
        Random random = new Random();
        Property p = new Property();
        p.setName(name);
        p.setAddressNumber(address);
        p.setOwner(owner);
        p.setCategory(category);
        p.setAcreage(acreage);
        p.setPrice(price);
        p.setNumberOfRooms(rooms);
        p.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
        p.setUpdatedAt(LocalDateTime.now());
        p.setWard(ward);

        if (lat != null && lng != null) {
            p.setLatitude(lat);
            p.setLongitude(lng);
            p.setGeocodeStatus(GeocodeStatus.SUCCESS);
        }

        // Random Amenities (3–6 items for richer recommendations)
        if (!allAmenities.isEmpty()) {
            List<Amenity> shuffled = new ArrayList<>(allAmenities);
            Collections.shuffle(shuffled);
            p.setAmenities(new HashSet<>(shuffled.subList(0, Math.min(shuffled.size(), random.nextInt(4) + 3))));
        }

        // Random Surroundings (2–4 items)
        if (!allSurroundings.isEmpty()) {
            List<Surrounding> shuffled = new ArrayList<>(allSurroundings);
            Collections.shuffle(shuffled);
            p.setSurroundings(new HashSet<>(shuffled.subList(0, Math.min(shuffled.size(), random.nextInt(3) + 2))));
        }

        // Random Target Tenants (1–2 items)
        if (!allTargets.isEmpty()) {
            List<TargetTenant> shuffled = new ArrayList<>(allTargets);
            Collections.shuffle(shuffled);
            p.setTargetTenants(new HashSet<>(shuffled.subList(0, Math.min(shuffled.size(), random.nextInt(2) + 1))));
        }

       String[] sampleImages = {
            "/images/rooms/room-1.jpg", "/images/rooms/room-2.jpg", "/images/rooms/room-3.jpg", "/images/rooms/room-4.jpg",
            "/images/rooms/room-5.jpg", "/images/rooms/room-6.jpg", "/images/rooms/room-7.jpg", "/images/rooms/room-8.jpg",
            "/images/rooms/room-9.jpg", "/images/rooms/room-10.jpg", "/images/rooms/room-11.jpg", "/images/rooms/room-12.jpg"
        };
        List<PropertyImage> images = new ArrayList<>();
        int baseIdx = random.nextInt(sampleImages.length);
        for (int i = 0; i < 4; i++) {
            PropertyImage img = new PropertyImage();
            img.setImageUrl(sampleImages[(baseIdx + i) % sampleImages.length]);
            img.setIsPrimary(i == 0);
            img.setProperty(p);
            images.add(img);
        }
        p.setImages(images);
        return p;
    }
}

