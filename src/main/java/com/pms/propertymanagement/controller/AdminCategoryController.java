package com.pms.propertymanagement.controller;

import com.pms.propertymanagement.entity.Category;
import com.pms.propertymanagement.repository.CategoryRepository;
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
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@Slf4j
public class AdminCategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public String listCategories(
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        List<Category> categories = categoryRepository.findAll();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            categories = categories.stream()
                    .filter(c -> c.getName() != null && c.getName().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }

        // Sort by ID
        categories.sort((a, b) -> Long.compare(a.getId(), b.getId()));

        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword);
        model.addAttribute("activePage", "categories");
        model.addAttribute("pageTitle", "Quản Lý Danh Mục Nhà Trọ");
        model.addAttribute("content", "admin/categories/list");

        return "layout/admin-layout";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("activePage", "categories");
        model.addAttribute("pageTitle", "Thêm Danh Mục Mới");
        model.addAttribute("content", "admin/categories/create");
        return "layout/admin-layout";
    }

    @PostMapping("/create")
    public String createCategory(
            @RequestParam("name") String name,
            RedirectAttributes redirectAttributes) {

        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên danh mục không được để trống!");
            return "redirect:/admin/categories/create";
        }

        if (categoryRepository.findByName(trimmedName) != null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Danh mục '" + trimmedName + "' đã tồn tại!");
            return "redirect:/admin/categories/create";
        }

        Category category = new Category();
        category.setName(trimmedName);
        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm danh mục mới thành công: " + trimmedName);
        return "redirect:/admin/categories";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Category> categoryOpt = categoryRepository.findById(id);
        if (categoryOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy danh mục mã ID: " + id);
            return "redirect:/admin/categories";
        }

        Category category = categoryOpt.get();
        model.addAttribute("category", category);
        model.addAttribute("activePage", "categories");
        model.addAttribute("pageTitle", "Chỉnh Sửa Danh Mục - " + category.getName());
        model.addAttribute("content", "admin/categories/edit");
        return "layout/admin-layout";
    }

    @PostMapping("/{id}/edit")
    public String updateCategory(
            @PathVariable("id") Long id,
            @RequestParam("name") String name,
            RedirectAttributes redirectAttributes) {

        Optional<Category> categoryOpt = categoryRepository.findById(id);
        if (categoryOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy danh mục mã ID: " + id);
            return "redirect:/admin/categories";
        }

        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên danh mục không được để trống!");
            return "redirect:/admin/categories/" + id + "/edit";
        }

        Category existing = categoryRepository.findByName(trimmedName);
        if (existing != null && !existing.getId().equals(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tên danh mục '" + trimmedName + "' đã tồn tại!");
            return "redirect:/admin/categories/" + id + "/edit";
        }

        Category category = categoryOpt.get();
        category.setName(trimmedName);
        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật danh mục thành công: " + trimmedName);
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Category> categoryOpt = categoryRepository.findById(id);
        if (categoryOpt.isPresent()) {
            Category category = categoryOpt.get();
            try {
                if (category.getProperties() != null && !category.getProperties().isEmpty()) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                            "Không thể xóa danh mục '" + category.getName() + "' vì đang có " 
                            + category.getProperties().size() + " cơ sở nhà trọ liên kết!");
                    return "redirect:/admin/categories";
                }
                categoryRepository.deleteById(id);
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa danh mục: " + category.getName());
            } catch (Exception ex) {
                log.error("Lỗi khi xóa category ID: {}", id, ex);
                redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa danh mục do có dữ liệu liên kết!");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Danh mục không tồn tại!");
        }
        return "redirect:/admin/categories";
    }
}
