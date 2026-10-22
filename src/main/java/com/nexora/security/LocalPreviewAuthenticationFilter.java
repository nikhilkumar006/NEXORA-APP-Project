package com.nexora.security;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Explicit local-development convenience only. When enabled, it lets the first
 * active local student preview the application without signing in. It never grants
 * admin or staff roles. The feature is disabled by default and must never be enabled
 * on a public deployment.
 */
public final class LocalPreviewAuthenticationFilter extends OncePerRequestFilter {
    public static final String PREVIEW_LOGGED_OUT = "NEXORA_PREVIEW_LOGGED_OUT";
    private static final List<SimpleGrantedAuthority> STUDENT_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));

    private final StudentRepository students;
    private final boolean enabled;

    public LocalPreviewAuthenticationFilter(StudentRepository students, boolean enabled) {
        this.students = students;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/login") || path.equals("/signup")
                || path.startsWith("/verify-login") || path.startsWith("/verify-email")
                || path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")
                || path.equals("/logout") || path.equals("/review-ended")
                || path.equals("/review/reenter") || path.startsWith("/css/")
                || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/webjars/") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        jakarta.servlet.http.HttpSession existingSession = request.getSession(false);
        if (existingSession != null && Boolean.TRUE.equals(existingSession.getAttribute(PREVIEW_LOGGED_OUT))) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken) {
            Student student = students.findFirstActive().orElse(null);
            if (student != null && student.getRegistrationNo() != null && !student.getRegistrationNo().isBlank()) {
                UsernamePasswordAuthenticationToken preview = new UsernamePasswordAuthenticationToken(
                        student.getRegistrationNo(), "LOCAL_PREVIEW", STUDENT_AUTHORITIES);
                preview.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(preview);
                SecurityContextHolder.setContext(context);
            }
        }
        filterChain.doFilter(request, response);
    }
}
