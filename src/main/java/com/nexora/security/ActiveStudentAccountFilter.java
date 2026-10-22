package com.nexora.security;

import com.nexora.repository.StudentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Immediately blocks an already-authenticated student session after permanent account deactivation. */
public final class ActiveStudentAccountFilter extends OncePerRequestFilter {
    private final StudentRepository students;
    public ActiveStudentAccountFilter(StudentRepository students) { this.students = students; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean studentRole = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()));
        if (studentRole && auth.getName() != null) {
            var student = students.findByLoginId(auth.getName()).orElse(null);
            if (student != null && !student.isActive()) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("This student account has been permanently disabled. Contact the institution for an appeal.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
