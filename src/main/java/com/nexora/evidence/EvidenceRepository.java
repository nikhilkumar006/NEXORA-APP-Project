package com.nexora.evidence;
import java.util.List;
public interface EvidenceRepository { void save(EvidenceRecord e); List<EvidenceRecord> findByStudent(String reg); long countVerified(String reg,String category); }
