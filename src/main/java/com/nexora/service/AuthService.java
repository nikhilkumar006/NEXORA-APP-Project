package com.nexora.service;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    public AuthService(StudentRepository studentRepository, PasswordEncoder passwordEncoder) { this.studentRepository=studentRepository; this.passwordEncoder=passwordEncoder; }
    public String registerStudent(String registrationNo,String email,String password,String confirmPassword,String fullName,String department,String program,String section,int batchStart,int batchEnd,int year,int semester) {
        registrationNo=registrationNo==null?"":registrationNo.trim().toUpperCase(); email=email==null?"":email.trim().toLowerCase();
        if(registrationNo.isBlank()||email.isBlank()||password==null||confirmPassword==null||password.isBlank()||confirmPassword.isBlank()||fullName==null||fullName.isBlank()) return "All required fields must be filled.";
        if(!email.endsWith("@srmist.edu.in")) return "Use your official @srmist.edu.in email address.";
        if(!password.equals(confirmPassword)) return "Passwords do not match.";
        if(password.length()<8) return "Password must contain at least 8 characters.";
        if(batchStart<2020||batchEnd<batchStart||year<1||year>4||semester<1||semester>8) return "Enter valid academic details.";
        if(studentRepository.findByLoginId(registrationNo).isPresent() || studentRepository.findByLoginId(email).isPresent()) return "An account already exists for this registration number or email.";
        Student s=new Student(); s.setRegistrationNo(registrationNo); s.setCollegeEmail(email); s.setFullName(fullName.trim()); s.setDepartment(department); s.setProgram(program); s.setSection(section); s.setBatchStartYear(batchStart); s.setBatchEndYear(batchEnd); s.setCurrentYear(year); s.setCurrentSemester(semester); s.setActive(true); s.setAccountCreated(true); s.setPasswordHash(passwordEncoder.encode(password));
        studentRepository.createStudent(s); return null;
    }
}
