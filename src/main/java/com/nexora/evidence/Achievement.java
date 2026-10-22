package com.nexora.evidence;
import java.time.LocalDate;
public record Achievement(long id,String registrationNo,String type,String title,String organization,LocalDate eventDate,String resultText,String verificationStatus) {}
