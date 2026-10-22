package com.nexora.evidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
@Repository public class AchievementRepository {
 private final JdbcTemplate jdbc; public AchievementRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void save(String reg,String type,String title,String org,LocalDate date,String result,Long evidenceId){jdbc.update("INSERT INTO achievement_records(registration_no,achievement_type,title,organization,event_date,result_text,evidence_id) VALUES(?,?,?,?,?,?,?)",reg,type,title,org,date==null?null:Date.valueOf(date),result,evidenceId);}
 public List<Achievement> find(String reg){return jdbc.query("SELECT a.id,a.registration_no,a.achievement_type,a.title,a.organization,a.event_date,a.result_text,COALESCE(e.verification_status,'SELF_REPORTED') status FROM achievement_records a LEFT JOIN student_evidence e ON e.id=a.evidence_id WHERE a.registration_no=? ORDER BY a.event_date DESC,a.id DESC",(r,n)->new Achievement(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5)==null?"":r.getString(5),r.getDate(6)==null?null:r.getDate(6).toLocalDate(),r.getString(7),r.getString(8)),reg);}
}
