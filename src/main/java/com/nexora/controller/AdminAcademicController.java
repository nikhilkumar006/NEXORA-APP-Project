package com.nexora.controller;

import com.nexora.academic.AcademicResource;
import com.nexora.academic.AcademicResourceService;
import com.nexora.subscription.SubscriptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import java.nio.file.Files;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAcademicController {
    private final AcademicResourceService resources;
    private final SubscriptionService subscriptions;

    public AdminAcademicController(AcademicResourceService resources, SubscriptionService subscriptions) {
        this.resources = resources;
        this.subscriptions = subscriptions;
    }

    @GetMapping({"", "/review"})
    public String review(Model model) {
        model.addAttribute("academicUploads", resources.findPendingApprovals());
        model.addAttribute("subscriptionRequests", subscriptions.pendingRequests());
        return "admin-review";
    }

    @PostMapping("/academic/{id}/approve")
    public String approveAcademic(@PathVariable long id, Authentication authentication) {
        resources.approve(id, authentication.getName());
        return "redirect:/admin/review?academicApproved=1";
    }

    @PostMapping("/academic/{id}/reject")
    public String rejectAcademic(@PathVariable long id, Authentication authentication) {
        resources.reject(id, authentication.getName());
        return "redirect:/admin/review?academicRejected=1";
    }


    @GetMapping("/subscription/{id}/screenshot")
    public ResponseEntity<?> screenshot(@PathVariable long id) {
        try {
            var path = subscriptions.paymentScreenshot(id);
            if (path == null) return ResponseEntity.notFound().build();
            String mime = Files.probeContentType(path);
            return ResponseEntity.ok().contentType(mime == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mime))
                    .body(new InputStreamResource(Files.newInputStream(path)));
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @PostMapping("/subscription/{id}/approve")
    public String approveSubscription(@PathVariable long id, Authentication authentication) {
        subscriptions.approveRequest(id, authentication.getName());
        return "redirect:/admin/review?subscriptionApproved=1";
    }

    @PostMapping("/subscription/{id}/reject")
    public String rejectSubscription(@PathVariable long id, Authentication authentication) {
        subscriptions.rejectRequest(id, authentication.getName());
        return "redirect:/admin/review?subscriptionRejected=1";
    }
}
