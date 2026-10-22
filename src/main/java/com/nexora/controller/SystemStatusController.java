package com.nexora.controller;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SystemStatusController {
    private final JdbcTemplate jdbc; private final Environment env;
    public SystemStatusController(JdbcTemplate jdbc, Environment env){this.jdbc=jdbc;this.env=env;}
    @GetMapping("/system/status")
    public String status(Authentication auth, Model model){
        model.addAttribute("student",auth.getName());
        model.addAttribute("students",count("students"));
        model.addAttribute("evidence",count("student_evidence"));
        model.addAttribute("criteria",count("career_criteria"));
        model.addAttribute("projects",count("student_projects"));
        model.addAttribute("google",env.getProperty("nexora.google.enabled",Boolean.class,false));
        model.addAttribute("github",env.getProperty("nexora.integrations.github.enabled",Boolean.class,false));
        model.addAttribute("linkedin",env.getProperty("nexora.integrations.linkedin.enabled",Boolean.class,false));
        return "system-status";
    }
    private long count(String table){try{Long n=jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);return n==null?0:n;}catch(Exception e){return -1;}}
}
