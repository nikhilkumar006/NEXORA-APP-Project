package com.nexora.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Locale;
import java.util.UUID;

/**
 * Last-resort MVC error handling. The browser receives a safe message and a short
 * reference code; the full exception and request path are written to the application log.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NexoraException.class)
    public String handleNexora(NexoraException ex, Model model, HttpServletRequest request) {
        String reference = referenceCode();
        log.warn("NEXORA request failure reference={} method={} path={} message={}",
                reference, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("message", ex.getMessage() == null || ex.getMessage().isBlank()
                ? "NEXORA could not complete this request." : ex.getMessage());
        model.addAttribute("errorReference", reference);
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception ex, Model model, HttpServletRequest request) {
        String reference = referenceCode();
        log.error("Unhandled NEXORA request error reference={} method={} path={}",
                reference, request.getMethod(), request.getRequestURI(), ex);
        model.addAttribute("message", "NEXORA could not complete this request. Please retry. If the problem persists, share the reference code below with the developer.");
        model.addAttribute("errorReference", reference);
        return "error";
    }

    private String referenceCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
