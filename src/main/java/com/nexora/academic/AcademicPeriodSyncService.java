package com.nexora.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/** Keeps stored student year/semester aligned with NEXORA's Aug-Dec / Jan-May calendar. */
@Service
public class AcademicPeriodSyncService {
    private final JdbcTemplate jdbc;

    public AcademicPeriodSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs once each night in IST; dashboard/login also refreshes the individual student immediately. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    public void refreshAllStudents() {
        for (var row : jdbc.queryForList("SELECT registration_no,batch_start_year FROM students WHERE active=TRUE")) {
            String reg = String.valueOf(row.get("registration_no"));
            int batch = ((Number) row.get("batch_start_year")).intValue();
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            if (batch < 2000 || batch > today.getYear()) continue;
            AcademicPeriod.Current p = AcademicPeriod.calculate(batch, today);
            jdbc.update("UPDATE students SET current_year=?, current_semester=? WHERE registration_no=?",
                    p.yearOfStudy(), p.semester(), reg);
        }
    }
}
