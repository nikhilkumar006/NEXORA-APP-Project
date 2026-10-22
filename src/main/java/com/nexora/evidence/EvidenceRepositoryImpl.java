package com.nexora.evidence;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository; import java.util.List;
@Repository public class EvidenceRepositoryImpl implements EvidenceRepository {
 private final JdbcTemplate jdbc; public EvidenceRepositoryImpl(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void save(EvidenceRecord e){jdbc.update("INSERT INTO student_evidence(registration_no,category,metric,value_text,source,verification_status,reference_url,file_name,captured_at,notes) VALUES(?,?,?,?,?,?,?,?,?,?)",e.registrationNo(),e.category(),e.metric(),e.value(),e.source(),e.verificationStatus(),e.referenceUrl(),e.fileName(),e.capturedAt(),e.notes());}
 public List<EvidenceRecord> findByStudent(String reg){return jdbc.query("SELECT * FROM student_evidence WHERE registration_no=? ORDER BY captured_at DESC,id DESC",(r,n)->new EvidenceRecord(r.getLong("id"),r.getString("registration_no"),r.getString("category"),r.getString("metric"),r.getString("value_text"),r.getString("source"),r.getString("verification_status"),r.getString("reference_url"),r.getString("file_name"),r.getTimestamp("captured_at").toLocalDateTime(),r.getString("notes")),reg);}
 public long countVerified(String reg,String category){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM student_evidence WHERE registration_no=? AND category=? AND verification_status IN ('VERIFIED','API_VERIFIED','INSTITUTION_VERIFIED','OAUTH_VERIFIED')",Long.class,reg,category);return n==null?0:n;}
}
