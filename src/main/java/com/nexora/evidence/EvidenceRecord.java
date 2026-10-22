package com.nexora.evidence;
import java.time.LocalDateTime;
public record EvidenceRecord(long id,String registrationNo,String category,String metric,String value,String source,String verificationStatus,String referenceUrl,String fileName,LocalDateTime capturedAt,String notes) {}
