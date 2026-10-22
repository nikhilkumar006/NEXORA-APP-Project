package com.nexora.leaderboard;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import com.nexora.xp.XpService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LeaderboardController {
    private final LeaderboardService leaderboard;
    private final XpService xp;
    private final StudentRepository students;

    public LeaderboardController(LeaderboardService leaderboard, XpService xp, StudentRepository students) {
        this.leaderboard = leaderboard;
        this.xp = xp;
        this.students = students;
    }

    /** XP and redemption are displayed on the Dashboard only. */
    @GetMapping({"/leaderboard", "/xp"})
    public String page() {
        return "redirect:/dashboard";
    }

    @PostMapping("/xp/redeem")
    public String redeem(Authentication authentication, @RequestParam String upiId) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/login";
        try {
            xp.createRedemption(student.getRegistrationNo(), upiId);
            return "redirect:/dashboard?rewardRequested=1";
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(java.util.Locale.ROOT);
            String code = message.contains("upi") ? "upi"
                    : message.contains("available contribution xp") || message.contains("at least 50") ? "balance" : "invalid";
            return "redirect:/dashboard?rewardError=" + code;
        }
    }

    private Student resolveStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) return null;
        return students.findByLoginId(authentication.getName()).orElse(null);
    }
}
