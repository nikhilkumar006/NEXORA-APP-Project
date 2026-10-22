package com.nexora.evidence;
import org.springframework.stereotype.Service;
import java.util.Locale;
/** Deterministic checks only. No AI or fabricated authenticity claim. */
@Service public class CertificateVerificationService {
 public String assess(String issuer,String certificateNo,String verificationUrl,String extractedText){
  if(issuer==null||issuer.isBlank()) return "NEEDS_REVIEW";
  if(certificateNo==null||certificateNo.isBlank()) return "NEEDS_REVIEW";
  String text=(extractedText==null?"":extractedText).toLowerCase(Locale.ROOT);
  boolean numberPresent=text.contains(certificateNo.toLowerCase(Locale.ROOT));
  boolean issuerPresent=text.contains(issuer.toLowerCase(Locale.ROOT));
  if(verificationUrl!=null&&!verificationUrl.isBlank()&&numberPresent&&issuerPresent) return "READY_FOR_OFFICIAL_VERIFICATION";
  if(numberPresent&&issuerPresent) return "STRUCTURE_CHECKED";
  return "NEEDS_REVIEW";
 }
}
