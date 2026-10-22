package com.nexora.security;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import java.util.ArrayList;
import java.util.List;

@Service
public class StudentDetailsService implements UserDetailsService {

    private final StudentRepository studentRepository;
    private final Environment env;

    public StudentDetailsService(StudentRepository studentRepository, Environment env) {
        this.studentRepository = studentRepository;
        this.env = env;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId)
            throws UsernameNotFoundException {

        String normalizedLoginId = loginId == null
                ? ""
                : loginId.trim();

        Student student = studentRepository
                .findByLoginId(normalizedLoginId)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        if (!student.isActive()) {
            throw new UsernameNotFoundException(
                    "Student account is inactive"
            );
        }

        if (!student.isAccountCreated()
                || student.getPasswordHash() == null) {

            throw new UsernameNotFoundException(
                    "NEXORA account has not been created"
            );
        }

        List<String> roles = new ArrayList<>();
        roles.add("STUDENT");
        String adminReg = env.getProperty("NEXORA_ADMIN_REGISTRATION", "").trim();
        String adminEmail = env.getProperty("NEXORA_ADMIN_EMAIL", "").trim().toLowerCase();
        if ((!adminReg.isBlank() && adminReg.equalsIgnoreCase(student.getRegistrationNo()))
                || (!adminEmail.isBlank() && adminEmail.equalsIgnoreCase(student.getCollegeEmail()))) {
            roles.add("ADMIN");
        }
        String staffReg = env.getProperty("NEXORA_STAFF_REGISTRATION", "").trim();
        String staffEmail = env.getProperty("NEXORA_STAFF_EMAIL", "").trim().toLowerCase();
        if ((!staffReg.isBlank() && staffReg.equalsIgnoreCase(student.getRegistrationNo()))
                || (!staffEmail.isBlank() && staffEmail.equalsIgnoreCase(student.getCollegeEmail()))) {
            roles.add("STAFF");
        }
        return User.withUsername(student.getRegistrationNo())
                .password(student.getPasswordHash())
                .roles(roles.toArray(String[]::new))
                .build();
    }
}