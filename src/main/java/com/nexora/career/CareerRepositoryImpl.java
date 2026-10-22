package com.nexora.career;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository; import java.util.Optional;
@Repository public class CareerRepositoryImpl implements CareerRepository {
 private final JdbcTemplate jdbc; public CareerRepositoryImpl(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void save(CareerProfile p){jdbc.update("INSERT INTO career_profiles(registration_no,primary_path,secondary_path,higher_study_preference,target_role,target_band,source_version) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE primary_path=VALUES(primary_path),secondary_path=VALUES(secondary_path),higher_study_preference=VALUES(higher_study_preference),target_role=VALUES(target_role),target_band=VALUES(target_band),source_version=VALUES(source_version)",p.registrationNo(),p.primaryPath(),p.secondaryPath(),p.higherStudyPreference(),p.targetRole(),p.targetBand(),p.sourceVersion());}
 public Optional<CareerProfile> find(String reg){return jdbc.query("SELECT * FROM career_profiles WHERE registration_no=?",(r,n)->new CareerProfile(r.getString("registration_no"),r.getString("primary_path"),r.getString("secondary_path"),r.getString("higher_study_preference"),r.getString("target_role"),r.getString("target_band"),r.getString("source_version")),reg).stream().findFirst();}
}
