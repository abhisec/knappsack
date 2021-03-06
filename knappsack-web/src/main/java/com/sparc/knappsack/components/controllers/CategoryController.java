package com.sparc.knappsack.components.controllers;

import com.sparc.knappsack.components.entities.*;
import com.sparc.knappsack.components.services.ApplicationService;
import com.sparc.knappsack.components.services.CategoryService;
import com.sparc.knappsack.components.services.OrganizationService;
import com.sparc.knappsack.components.services.UserService;
import com.sparc.knappsack.components.validators.CategoryValidator;
import com.sparc.knappsack.enums.AppState;
import com.sparc.knappsack.enums.StorageType;
import com.sparc.knappsack.forms.CategoryForm;
import com.sparc.knappsack.forms.EnumEditor;
import com.sparc.knappsack.forms.Result;
import com.sparc.knappsack.models.ApplicationModel;
import com.sparc.knappsack.models.CategoryModel;
import com.sparc.knappsack.util.UserAgentInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class CategoryController extends AbstractController {
    @Qualifier("categoryService")
    @Autowired
    private CategoryService categoryService;

    @Qualifier("applicationService")
    @Autowired
    private ApplicationService applicationService;

    @Qualifier("userService")
    @Autowired
    private UserService userService;

    @Qualifier("organizationService")
    @Autowired
    private OrganizationService organizationService;

    @Qualifier("categoryValidator")
    @Autowired
    private CategoryValidator categoryValidator;

    @InitBinder("category")
    protected void initBinder(WebDataBinder binder) {
        binder.setValidator(categoryValidator);
        binder.setBindEmptyMultipartFiles(false);
        binder.registerCustomEditor(StorageType.class, new EnumEditor(StorageType.class));
    }

    @PreAuthorize("hasAccessToCategory(#id, #userAgentInfo.applicationType) or hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/categories/{id}", method = RequestMethod.GET)
    public String displayApplicationsForCategory(@PathVariable Long id, Model model, UserAgentInfo userAgentInfo) {
        checkRequiredEntity(categoryService, id);

        Category category = categoryService.get(id);

        model.addAttribute("selectedCategory", categoryService.createCategoryModel(category));

        User user = userService.getUserFromSecurityContext();
        List<Application> applications = userService.getApplicationsForUser(user, userAgentInfo.getApplicationType(), category.getId(), AppState.GROUP_PUBLISH, AppState.ORGANIZATION_PUBLISH, AppState.ORG_PUBLISH_REQUEST);
        List<ApplicationModel> applicationModels = applicationService.createApplicationModels(applications);

        model.addAttribute("applications", applicationModels);

        return "category_resultsTH";
    }

    @RequestMapping(value = "/categories", method = RequestMethod.GET)
    public String categories(Model model, UserAgentInfo userAgentInfo) {
        User user = userService.getUserFromSecurityContext();

        List<Category> categoryList = userService.getCategoriesForUser(user, userAgentInfo.getApplicationType());
        List<CategoryModel> categoryModelList = new ArrayList<CategoryModel>();

        for (Category category : categoryList) {
            CategoryModel categoryModel = categoryService.createCategoryModel(category);

            if (categoryModel != null) {
                categoryModelList.add(categoryModel);
            }
        }

        model.addAttribute("categories", categoryModelList);

        return "categoriesTH";
    }

    @PreAuthorize("isOrganizationAdmin(#orgId) or hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/manager/addCategory/{orgId}", method = RequestMethod.GET)
    public String addCategory(Model model, @PathVariable Long orgId) {
        checkRequiredEntity(organizationService, orgId);

        Organization organization = organizationService.get(orgId);

        if (organization != null) {
            if (!model.containsAttribute("category")) {
                CategoryForm categoryForm = new CategoryForm();
                categoryForm.setOrganizationId(organization.getId());
                categoryForm.setOrgStorageConfigId(organization.getOrgStorageConfig().getId());

                model.addAttribute("category", categoryForm);
            }

            model.addAttribute("parentOrgId", organization.getId());
            model.addAttribute("parentOrgName", organization.getName());
            model.addAttribute("orgStorageConfigId", organization.getOrgStorageConfig().getId());
            model.addAttribute("storageConfigurations", organization.getOrgStorageConfig().getStorageConfigurations());
        }

        model.addAttribute("isEdit", false);

        return "manager/manageCategoryTH";
    }

    @RequestMapping(value = "/manager/categoryUploadProgress", method = RequestMethod.POST)
    public
    @ResponseBody
    Result categoryUploadProgress(HttpServletResponse response, @ModelAttribute("category") CategoryForm category) {
        MultipartFile file = category.getIcon();
        Result result = new Result();

        try {
            if (file != null && !file.isEmpty()) {
                file.getBytes();
                result.setResult(true);
            } else {
                result.setResult(false);
            }
        } catch (IOException e) {
            e.printStackTrace();
            result.setResult(false);
        }

        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        return result;
    }

    @PreAuthorize("isOrganizationAdmin(#category.organizationId) or hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/manager/saveCategory", method = RequestMethod.POST)
    public String saveCategory(Model model, @ModelAttribute("category") @Valid CategoryForm category, BindingResult bindingResult, @RequestParam boolean editing) {
        if(bindingResult.hasErrors()) {
            model.addAttribute("category", category);
            if (editing) {
                return editCategory(model, category.getId(), category.getOrganizationId());
            } else {
                return addCategory(model, category.getOrganizationId());
            }
        }

        if(category.getId() != null && category.getId() > 0) {
            categoryService.updateCategory(category);
        } else {
            categoryService.saveCategory(category);
        }

        return "redirect:/manager/editOrg/" + category.getOrganizationId();
    }

    @PreAuthorize("isOrganizationAdmin(#orgId) or hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/manager/deleteCategory/{id}/{orgId}", method = RequestMethod.GET)
    public String deleteCategory(@PathVariable Long id, @PathVariable Long orgId) {
        checkRequiredEntity(categoryService, id);

        Category category = categoryService.get(id);
        Long organizationId = category.getOrganization().getId();
        if (!orgId.equals(organizationId)) {
            throw new RuntimeException("Attempted to delete category for organization with different Id");
        }

        categoryService.delete(id);

        return "redirect:/manager/editOrg/" + organizationId;
    }

    @PreAuthorize("isOrganizationAdmin(#orgId) or hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/manager/deleteCategoryIcon/{id}/{orgId}", method = RequestMethod.POST)
    public String deleteCategoryIcon(Model model, @PathVariable Long id, @PathVariable Long orgId) {
        checkRequiredEntity(categoryService, id);

        Category category = categoryService.get(id);
        Long organizationId = category.getOrganization().getId();
        if (!orgId.equals(organizationId)) {
            throw new RuntimeException("Attempted to delete category icon for organization with different Id");
        }
        categoryService.deleteIcon(category);

        return editCategory(model, id, orgId);
    }

    @PreAuthorize("isOrganizationAdmin(#orgId) or hasRole('ROLE_ADMIN')")
    @RequestMapping(value = "/manager/editCategory/{id}/{orgId}", method = RequestMethod.GET)
    public String editCategory(Model model, @PathVariable Long id, @PathVariable Long orgId) {
        checkRequiredEntity(categoryService, id);

        Category category = categoryService.get(id);
        Long organizationId = category.getOrganization().getId();
        if (!orgId.equals(organizationId)) {
            throw new RuntimeException("Attempted to edit category for organization with different Id");
        }

        if (!model.containsAttribute("category")) {

            if (category != null) {
                CategoryForm categoryForm = new CategoryForm();
                categoryForm.setDescription(category.getDescription());
                categoryForm.setId(category.getId());
                categoryForm.setName(category.getName());
                categoryForm.setStorageConfigurationId(category.getStorageConfiguration().getId());

                AppFile icon = category.getIcon();
                if (icon != null) {
                    MockMultipartFile iconMultipartFile = new MockMultipartFile(category.getIcon().getName(), category.getIcon().getName(), category.getIcon().getType(), new byte[0]);
                    categoryForm.setIcon(iconMultipartFile);
                }

                model.addAttribute("category", categoryForm);
            }
        }

        model.addAttribute("isEdit", true);

        if(category != null) {
            model.addAttribute("parentOrgId", category.getOrganization().getId());
            model.addAttribute("parentOrgName", category.getOrganization().getName());
            model.addAttribute("orgStorageConfigId", category.getOrganization().getOrgStorageConfig().getId());
            model.addAttribute("storageConfigurations", category.getOrganization().getOrgStorageConfig().getStorageConfigurations());
        }

        return "manager/manageCategoryTH";
    }

}
