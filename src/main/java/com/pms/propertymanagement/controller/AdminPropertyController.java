package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Property;
import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.CategoryRepository;
import com.pms.propertymanagement.repository.PropertyRepository;
import com.pms.propertymanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/properties")
@RequiredArgsConstructor
@Slf4j
public class AdminPropertyController {

    private final PropertyRepository propertyRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("owners", userRepository.findByRoles_Name("OWNER"));
        model.addAttribute("activePage", "properties");
        model.addAttribute("pageTitle", "Thêm Cơ Sở Nhà Trọ Mới");
        model.addAttribute("content", "admin/properties/create");
        return "layout/admin-layout";
    }

    @PostMapping("/create")
    public String createProperty(
            @RequestParam("name") String name,
            @RequestParam("ownerId") Long ownerId,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("price") int price,
            @RequestParam("acreage") double acreage,
            @RequestParam("numberOfRooms") int numberOfRooms,
            @RequestParam(value = "originalAddress", required = false) String originalAddress,
            RedirectAttributes redirectAttributes) {

        Optional<User> ownerOpt = userRepository.findById(ownerOptId(ownerId));
        if (ownerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chủ trọ không tồn tại!");
            return "redirect:/admin/properties/create";
        }

        Property property = new Property();
        property.setName(name.trim());
        property.setOwner(ownerOpt.get());
        property.setPrice(price);
        property.setAcreage(acreage);
        property.setNumberOfRooms(numberOfRooms);
        property.setAddressNumber(originalAddress != null ? originalAddress.trim() : "Chưa cập nhật");
        property.setOriginalAddress(originalAddress != null ? originalAddress.trim() : "Chưa cập nhật");

        categoryRepository.findById(categoryId).ifPresent(property::setCategory);

        propertyRepository.save(property);
        redirectAttributes.addFlashAttribute("successMessage", "Đã tạo cơ sở nhà trọ mới thành công: " + property.getName());
        return "redirect:/admin/properties";
    }

    private Long ownerOptId(Long id) {
        return id;
    }

    @GetMapping
    public String listProperties(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model) {

        List<Property> properties = propertyRepository.findAll();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            properties = properties.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(kw))
                            || (p.getOriginalAddress() != null && p.getOriginalAddress().toLowerCase().contains(kw))
                            || (p.getNormalizedAddress() != null && p.getNormalizedAddress().toLowerCase().contains(kw))
                            || (p.getOwner() != null && p.getOwner().getFullName() != null && p.getOwner().getFullName().toLowerCase().contains(kw))
                            || (p.getOwner() != null && p.getOwner().getUsername() != null && p.getOwner().getUsername().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        if (categoryId != null) {
            properties = properties.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId))
                    .collect(Collectors.toList());
        }

        // Sort descending by ID
        properties.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        model.addAttribute("properties", properties);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("activePage", "properties");
        model.addAttribute("pageTitle", "Quản Lý Cơ Sở Nhà Trọ");
        model.addAttribute("content", "admin/properties/list");

        return "layout/admin-layout";
    }

    @GetMapping("/{id}")
    public String viewPropertyDetail(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Property> propertyOpt = propertyRepository.findById(id);
        if (propertyOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy cơ sở nhà trọ có ID: " + id);
            return "redirect:/admin/properties";
        }

        Property property = propertyOpt.get();
        model.addAttribute("property", property);
        model.addAttribute("activePage", "properties");
        model.addAttribute("pageTitle", "Chi Tiết Cơ Sở: " + property.getName());
        model.addAttribute("content", "admin/properties/detail");
        return "layout/admin-layout";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Property> propertyOpt = propertyRepository.findById(id);
        if (propertyOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy cơ sở nhà trọ có ID: " + id);
            return "redirect:/admin/properties";
        }

        Property property = propertyOpt.get();
        model.addAttribute("property", property);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("activePage", "properties");
        model.addAttribute("pageTitle", "Chỉnh Sửa Cơ Sở: " + property.getName());
        model.addAttribute("content", "admin/properties/edit");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/edit")
    public String updateProperty(
            @PathVariable("id") Long id,
            @RequestParam("name") String name,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("price") int price,
            @RequestParam("acreage") double acreage,
            @RequestParam("numberOfRooms") int numberOfRooms,
            @RequestParam(value = "originalAddress", required = false) String originalAddress,
            RedirectAttributes redirectAttributes) {

        Optional<Property> propertyOpt = propertyRepository.findById(id);
        if (propertyOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy cơ sở nhà trọ có ID: " + id);
            return "redirect:/admin/properties";
        }

        Property property = propertyOpt.get();
        property.setName(name.trim());
        property.setPrice(price);
        property.setAcreage(acreage);
        property.setNumberOfRooms(numberOfRooms);
        if (originalAddress != null && !originalAddress.trim().isEmpty()) {
            property.setOriginalAddress(originalAddress.trim());
        }

        categoryRepository.findById(categoryId).ifPresent(property::setCategory);

        propertyRepository.save(property);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin cơ sở '" + property.getName() + "' thành công!");
        return "redirect:/admin/properties";
    }

    @PostMapping("/{id}/delete")
    public String deleteProperty(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Property> propertyOpt = propertyRepository.findById(id);
        if (propertyOpt.isPresent()) {
            Property property = propertyOpt.get();
            try {
                propertyRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa cơ sở nhà trọ: " + property.getName());
            } catch (Exception ex) {
                log.error("Không thể xóa property ID: {}", id, ex);
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "Không thể xóa cơ sở '" + property.getName() + "' do đang có hợp đồng hoặc phòng trọ liên kết!");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Cơ sở nhà trọ không tồn tại!");
        }
        return "redirect:/admin/properties";
    }
}
