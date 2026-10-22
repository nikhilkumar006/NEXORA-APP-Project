package com.nexora.service;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final StudentRepository studentRepository;

    public DashboardServiceImpl(
            StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public Student getStudentDashboardData(
            String registrationNo) {

        return studentRepository
                .findByLoginId(registrationNo)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Logged-in student not found"
                        )
                );
    }
}