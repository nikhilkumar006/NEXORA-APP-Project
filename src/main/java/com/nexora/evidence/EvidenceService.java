package com.nexora.evidence;
import org.springframework.stereotype.Service; import java.time.LocalDateTime; import java.util.List;
@Service public class EvidenceService { private final EvidenceRepository repo; public EvidenceService(EvidenceRepository repo){this.repo=repo;} public void add(String reg,String cat,String metric,String value,String source,String status,String url,String file,String notes){repo.save(new EvidenceRecord(0,reg,cat,metric,value,source,status,url,file,LocalDateTime.now(),notes));} public List<EvidenceRecord> all(String reg){return repo.findByStudent(reg);} }
