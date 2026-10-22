package com.nexora.subscription;

import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
public class SubscriptionController {
    private final SubscriptionService subscriptions;
    private final StudentRepository students;
    public SubscriptionController(SubscriptionService subscriptions, StudentRepository students){this.subscriptions=subscriptions;this.students=students;}

    /** Subscription options now live inside Profile; there is no standalone Premium module. */
    @GetMapping({"/pricing", "/subscription"})
    public String page(@RequestParam(required = false) String paymentSubmitted) {
        return paymentSubmitted == null
                ? "redirect:/profile#subscription"
                : "redirect:/profile?paymentSubmitted=1#subscription";
    }

    @GetMapping("/subscription/pay")
    public String payPage(Authentication a,@RequestParam String plan,@RequestParam(defaultValue="ANNUAL") String cycle,
                          @RequestParam(required=false) String error,Model m){
        var student=students.findByLoginId(a.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        SubscriptionPlan selected=subscriptions.plans().stream().filter(p->p.code().equalsIgnoreCase(plan)).findFirst().orElse(null);
        if (selected == null || "FREE".equalsIgnoreCase(selected.code())) return "redirect:/profile#subscription";
        String normalizedCycle = cycle == null ? "ANNUAL" : cycle.toUpperCase();
        if (!"SEMESTER".equals(normalizedCycle) && !"ANNUAL".equals(normalizedCycle)) normalizedCycle = "ANNUAL";
        m.addAttribute("student",student); m.addAttribute("selected",selected); m.addAttribute("cycle",normalizedCycle);
        m.addAttribute("amount",selected.price(normalizedCycle));
        m.addAttribute("error", error);
        return "subscription-payment";
    }

    @PostMapping("/subscription/payment-request")
    public String paymentRequest(Authentication a,
                                 @RequestParam String plan,@RequestParam String cycle,
                                 @RequestParam String transactionId,@RequestParam(required=false) String payerUpiId,
                                 @RequestParam String paymentDate,@RequestParam String paymentTime,
                                 @RequestParam(required=false) MultipartFile paymentScreenshot,
                                 @RequestParam(required=false) String note) {
        try {
            var student=students.findByLoginId(a.getName()).orElseThrow();
            subscriptions.createPaymentRequest(student.getRegistrationNo(),student.getFullName(),student.getCollegeEmail(),plan,cycle,transactionId,payerUpiId,
                    LocalDate.parse(paymentDate),LocalTime.parse(paymentTime),paymentScreenshot,note);
            return "redirect:/profile?paymentSubmitted=1#subscription";
        } catch (Exception e) {
            return "redirect:/subscription/pay?plan="+plan+"&cycle="+cycle+"&error="+java.net.URLEncoder.encode(e.getMessage()==null?"Payment request failed":e.getMessage(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
