package com.nexora.leaderboard;

import com.nexora.xp.XpService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/rewards")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRewardController {
    private final XpService xp;
    public AdminRewardController(XpService xp) { this.xp = xp; }

    @GetMapping
    public String review(Model model, @RequestParam(required = false) String result) {
        model.addAttribute("pendingRedemptions", xp.pendingRedemptions());
        model.addAttribute("result", result);
        return "admin-rewards";
    }

    @PostMapping("/{id}/paid")
    public String markPaid(@PathVariable long id, @RequestParam String rewardCode,
                           @RequestParam String paymentReference, Authentication authentication) {
        try {
            xp.markPaid(id, rewardCode, paymentReference, authentication.getName());
            return "redirect:/admin/rewards?result=paid";
        } catch (IllegalArgumentException ex) {
            return "redirect:/admin/rewards?result=invalid";
        }
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable long id, @RequestParam(required = false) String note, Authentication authentication) {
        try {
            xp.reject(id, note, authentication.getName());
            return "redirect:/admin/rewards?result=rejected";
        } catch (IllegalArgumentException ex) {
            return "redirect:/admin/rewards?result=invalid";
        }
    }
}
