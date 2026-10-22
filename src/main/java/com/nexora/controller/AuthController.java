package com.nexora.controller;

import com.nexora.security.StudentDetailsService;
import com.nexora.service.LoginOtpService;
import com.nexora.service.RegistrationService;
import com.nexora.service.StudentLoginId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class AuthController {
    private static final String LOGIN_REG = "NEXORA_LOGIN_REG";
    private static final String LOGIN_EMAIL = "NEXORA_LOGIN_EMAIL";
    private static final String REG_EMAIL = "NEXORA_REG_EMAIL";

    private final RegistrationService registrationService;
    private final LoginOtpService loginOtpService;
    private final StudentDetailsService studentDetailsService;
    private final Environment environment;

    public AuthController(RegistrationService registrationService, LoginOtpService loginOtpService,
                          StudentDetailsService studentDetailsService, Environment environment) {
        this.registrationService = registrationService;
        this.loginOtpService = loginOtpService;
        this.studentDetailsService = studentDetailsService;
        this.environment = environment;
    }

    private boolean googleEnabled() {
        return environment.getProperty("nexora.google.enabled", Boolean.class, false);
    }

    private boolean localModuleReview() {
        return environment.getProperty("nexora.dev-access.enabled", Boolean.class, false);
    }

    @GetMapping("/")
    public String home() { return "redirect:/dashboard"; }

    @GetMapping("/login")
    public String login(Model model, Authentication authentication) {
        if (localModuleReview()) return "redirect:/dashboard";
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
            return "redirect:/dashboard";
        }
        model.addAttribute("googleEnabled", googleEnabled());
        return "login";
    }

    @PostMapping("/login")
    public String startLogin(@RequestParam(defaultValue = "") String username,
                             @RequestParam(defaultValue = "") String password,
                             HttpServletRequest request, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        LoginOtpService.LoginStartResult result = loginOtpService.start(username, password);
        if (!result.success()) {
            model.addAttribute("error", result.error());
            model.addAttribute("googleEnabled", googleEnabled());
            return "login";
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(LOGIN_REG, result.registrationNo());
        session.setAttribute(LOGIN_EMAIL, result.email());
        return "redirect:/verify-login";
    }

    @GetMapping("/verify-login")
    public String verifyLoginPage(HttpServletRequest request, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        HttpSession session = request.getSession(false);
        String email = session == null ? null : (String) session.getAttribute(LOGIN_EMAIL);
        String reg = session == null ? null : (String) session.getAttribute(LOGIN_REG);
        if (email == null || reg == null) return "redirect:/login";
        model.addAttribute("maskedEmail", maskEmailForDisplay(email));
        return "verify-login";
    }

    @PostMapping("/verify-login")
    public String verifyLogin(@RequestParam(defaultValue = "") String otp,
                              HttpServletRequest request, HttpServletResponse response, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        HttpSession session = request.getSession(false);
        String reg = session == null ? null : (String) session.getAttribute(LOGIN_REG);
        String email = session == null ? null : (String) session.getAttribute(LOGIN_EMAIL);
        if (reg == null || email == null) return "redirect:/login";

        LoginOtpService.VerifyResult result = loginOtpService.verify(reg, otp);
        if (!result.success()) {
            model.addAttribute("maskedEmail", maskEmailForDisplay(email));
            model.addAttribute("error", result.error());
            return "verify-login";
        }

        try {
            UserDetails user = studentDetailsService.loadUserByUsername(result.registrationNo());
            request.changeSessionId();
            UsernamePasswordAuthenticationToken authenticated = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            new HttpSessionSecurityContextRepository().saveContext(context, request, response);
            session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(LOGIN_REG);
                session.removeAttribute(LOGIN_EMAIL);
                session.removeAttribute(REG_EMAIL);
            }
            return "redirect:/dashboard";
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            model.addAttribute("googleEnabled", googleEnabled());
            model.addAttribute("error", "Your account could not be authenticated. Please start login again.");
            return "login";
        }
    }

    @PostMapping("/verify-login/resend")
    public String resendLogin(HttpServletRequest request, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        HttpSession session = request.getSession(false);
        String reg = session == null ? null : (String) session.getAttribute(LOGIN_REG);
        String email = session == null ? null : (String) session.getAttribute(LOGIN_EMAIL);
        if (reg == null || email == null) return "redirect:/login";
        String error = loginOtpService.resend(reg);
        model.addAttribute("maskedEmail", maskEmailForDisplay(email));
        if (error == null) model.addAttribute("message", "A new verification code has been sent.");
        else model.addAttribute("error", error);
        return "verify-login";
    }

    @GetMapping("/signup")
    public String signup(Model model, Authentication authentication) {
        if (localModuleReview()) return "redirect:/dashboard";
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
            return "redirect:/dashboard";
        }
        return "signup";
    }

    @PostMapping("/signup")
    public String create(@RequestParam(defaultValue = "") String registrationNo,
                         @RequestParam(defaultValue = "") String email,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(defaultValue = "") String confirmPassword,
                         @RequestParam(defaultValue = "") String fullName,
                         @RequestParam(defaultValue = "") String department,
                         @RequestParam(defaultValue = "") String program,
                         @RequestParam(defaultValue = "") String section,
                         @RequestParam(defaultValue = "2025") int batchStart,
                         @RequestParam(defaultValue = "2029") int batchEnd,
                         @RequestParam(defaultValue = "2") int currentYear,
                         @RequestParam(defaultValue = "3") int currentSemester,
                         HttpServletRequest request, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        RegistrationService.RegistrationData data = new RegistrationService.RegistrationData(
                registrationNo, email, password, confirmPassword, fullName, department, program, section,
                batchStart, batchEnd, currentYear, currentSemester, "");
        String error = registrationService.start(data);
        if (error != null) {
            model.addAttribute("error", error);
            return "signup";
        }
        String normalizedEmail = StudentLoginId.normalizeEmail(email);
        request.getSession(true).setAttribute(REG_EMAIL, normalizedEmail);
        return "redirect:/verify-email?email=" + URLEncoder.encode(normalizedEmail, StandardCharsets.UTF_8);
    }

    @GetMapping("/verify-email")
    public String verifyEmailPage(@RequestParam(required = false) String email,
                                  HttpServletRequest request, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        HttpSession session = request.getSession(false);
        String sessionEmail = session == null ? null : (String) session.getAttribute(REG_EMAIL);
        String resolved = sessionEmail != null ? sessionEmail : (email == null || email.isBlank() ? "" : StudentLoginId.normalizeEmail(email));
        if (resolved.isBlank() || resolved.startsWith("@") || !resolved.endsWith("@srmist.edu.in")) return "redirect:/signup";
        model.addAttribute("email", resolved);
        return "verify-email";
    }

    @PostMapping("/verify-email")
    public String verifyEmail(@RequestParam(defaultValue = "") String email,
                              @RequestParam(defaultValue = "") String otp,
                              HttpServletRequest request, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        String normalizedEmail = StudentLoginId.normalizeEmail(email);
        HttpSession session = request.getSession(false);
        String sessionEmail = session == null ? null : (String) session.getAttribute(REG_EMAIL);
        if (sessionEmail == null || !sessionEmail.equalsIgnoreCase(normalizedEmail)) return "redirect:/signup";
        String error = registrationService.verify(normalizedEmail, otp);
        if (error != null) {
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("error", error);
            return "verify-email";
        }
        session.removeAttribute(REG_EMAIL);
        return "redirect:/login?registered";
    }

    @PostMapping("/verify-email/resend")
    public String resendEmail(@RequestParam(defaultValue = "") String email,
                              HttpServletRequest request, Model model) {
        if (localModuleReview()) return "redirect:/dashboard";
        String normalizedEmail = StudentLoginId.normalizeEmail(email);
        HttpSession session = request.getSession(false);
        String sessionEmail = session == null ? null : (String) session.getAttribute(REG_EMAIL);
        if (sessionEmail == null || !sessionEmail.equalsIgnoreCase(normalizedEmail)) return "redirect:/signup";
        String error = registrationService.resend(normalizedEmail);
        model.addAttribute("email", normalizedEmail);
        if (error == null) model.addAttribute("message", "A new verification code has been sent.");
        else model.addAttribute("error", error);
        return "verify-email";
    }

    @GetMapping("/logout/confirm")
    public String logoutConfirmation() { return "logout-confirm"; }

    @GetMapping("/review-ended")
    public String reviewEnded() { return "review-ended"; }

    @PostMapping("/review/reenter")
    public String reenterLocalReview(HttpServletRequest request) {
        if (!localModuleReview()) return "redirect:/login";
        HttpSession session = request.getSession(true);
        session.removeAttribute(com.nexora.security.LocalPreviewAuthenticationFilter.PREVIEW_LOGGED_OUT);
        return "redirect:/dashboard";
    }

    private String maskEmailForDisplay(String email) {
        if (email == null || email.isBlank()) return "your registered SRMIST email";
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) return "your registered SRMIST email";
        String local = value.substring(0, at);
        String domain = value.substring(at);
        String masked = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, Math.min(2, local.length())) + "****";
        return masked + domain;
    }
}
