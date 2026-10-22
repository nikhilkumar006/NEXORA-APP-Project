package com.nexora.security;

import com.nexora.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private final GoogleAuthenticationSuccessHandler googleHandler;
    private final IntegrationOAuthSuccessHandler integrationHandler;
    private final Environment env;
    private final boolean devAccessEnabled;

    public SecurityConfig(GoogleAuthenticationSuccessHandler googleHandler,
                          IntegrationOAuthSuccessHandler integrationHandler,
                          Environment env,
                          @Value("${nexora.dev-access.enabled:false}") boolean devAccessEnabled) {
        this.googleHandler = googleHandler;
        this.integrationHandler = integrationHandler;
        this.env = env;
        this.devAccessEnabled = devAccessEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, StudentRepository students) throws Exception {
        http.authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/", "/login", "/signup", "/verify-email",
                            "/verify-email/resend", "/verify-login", "/verify-login/resend", "/logout", "/logout/confirm",
                            "/review-ended", "/review/reenter",
                            "/css/**", "/js/**", "/images/**", "/favicon.ico", "/oauth2/**",
                            "/login/oauth2/**").permitAll();
                    // Local module-review profile only: let the owner populate actual chapter questions without enabling login screens.
                    if (env.acceptsProfiles(org.springframework.core.env.Profiles.of("review"))) {
                        auth.requestMatchers("/staff/chapter-tests").permitAll();
                    }
                    auth.requestMatchers("/admin/**").hasRole("ADMIN")
                            .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                            .anyRequest().authenticated();
                })
                .formLogin(form -> form.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, authException) -> {
                    boolean previewEnded = false;
                    jakarta.servlet.http.HttpSession session = request.getSession(false);
                    if (session != null) previewEnded = Boolean.TRUE.equals(
                            session.getAttribute(LocalPreviewAuthenticationFilter.PREVIEW_LOGGED_OUT));
                    String destination = previewEnded ? "/review-ended" : (devAccessEnabled ? "/dashboard" : "/login");
                    response.sendRedirect(request.getContextPath() + destination);
                }))
                .logout(logout -> logout.logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String contextPath = request.getContextPath();
                            if (devAccessEnabled) {
                                // Session invalidation happens before this handler; create a fresh, explicitly signed-out review session.
                                jakarta.servlet.http.HttpSession session = request.getSession(true);
                                session.setAttribute(LocalPreviewAuthenticationFilter.PREVIEW_LOGGED_OUT, Boolean.TRUE);
                                response.sendRedirect(contextPath + "/review-ended");
                            } else {
                                response.sendRedirect(contextPath + "/login?logout");
                            }
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());

        if (devAccessEnabled) {
            // Explicit local preview mode. Administrative paths remain protected apart from the local-only chapter-test authoring route.
            http.addFilterBefore(new LocalPreviewAuthenticationFilter(students, true),
                    AnonymousAuthenticationFilter.class);
        }
        // Deactivated/banned student accounts lose even an already-open session immediately.
        http.addFilterAfter(new ActiveStudentAccountFilter(students), AnonymousAuthenticationFilter.class);

        if (env.getProperty("nexora.google.enabled", Boolean.class, false)) {
            http.oauth2Login(o -> o.loginPage("/login").successHandler(googleHandler)
                    .failureUrl("/login?googleError"));
        } else if (env.getProperty("nexora.integrations.github.enabled", Boolean.class, false)
                || env.getProperty("nexora.integrations.linkedin.enabled", Boolean.class, false)) {
            http.oauth2Login(o -> o.loginPage("/login").successHandler(integrationHandler)
                    .failureUrl("/integrations?error"));
        }

        http.headers(headers -> headers.cacheControl(cache -> { }));
        return http.build();
    }
}
