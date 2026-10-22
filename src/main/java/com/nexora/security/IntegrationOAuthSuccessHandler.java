package com.nexora.security;

import com.nexora.evidence.EvidenceRepository;
import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nexora.controller.GitHubIntegrationService;
import java.io.IOException;
import java.util.Optional;

@Component
public class IntegrationOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final StudentRepository students; private final EvidenceRepository evidence; private final ObjectProvider<OAuth2AuthorizedClientService> clients; private final JdbcTemplate jdbc; private final GitHubIntegrationService githubSync;
    public IntegrationOAuthSuccessHandler(StudentRepository s,EvidenceRepository e,ObjectProvider<OAuth2AuthorizedClientService> c, JdbcTemplate jdbc, GitHubIntegrationService githubSync){students=s;evidence=e;clients=c;this.jdbc=jdbc;this.githubSync=githubSync;}
    @Override public void onAuthenticationSuccess(HttpServletRequest req,HttpServletResponse res,Authentication auth)throws IOException,ServletException{
        if(!(auth instanceof OAuth2AuthenticationToken token)){res.sendRedirect("/integrations?error");return;}
        String provider=token.getAuthorizedClientRegistrationId(); String email=token.getPrincipal().getAttribute("email"); String savedReg=(String)req.getSession().getAttribute("integrationStudent");
        Optional<Student> student=savedReg==null?(email==null?Optional.empty():students.findByLoginId(email)):students.findByLoginId(savedReg);
        if(student.isEmpty()){res.sendRedirect("/integrations?error=account");return;}
        String reg=student.get().getRegistrationNo();
        OAuth2AuthorizedClientService clientService=clients.getIfAvailable();
        OAuth2AuthorizedClient client=clientService==null?null:clientService.loadAuthorizedClient(provider,token.getName());
        String access=client==null||client.getAccessToken()==null?null:client.getAccessToken().getTokenValue();
        if("github".equals(provider)&&access!=null){
            String githubLogin = token.getPrincipal().getAttribute("login");
            try { githubSync.connectAndSync(reg, token.getName(), githubLogin, access); } catch (Exception ignored) { }
        } else if("linkedin".equals(provider)){
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            jdbc.update("DELETE FROM student_evidence WHERE registration_no=? AND category='PROFESSIONAL' AND metric IN ('linkedin_profile','linkedin_headline')", reg);
            String name = token.getPrincipal().getAttribute("name");
            String headline = token.getPrincipal().getAttribute("headline");
            evidence.save(new com.nexora.evidence.EvidenceRecord(0,reg,"PROFESSIONAL","linkedin_profile",
                    name == null || name.isBlank() ? "connected" : name,"LinkedIn OIDC","OAUTH_VERIFIED",
                    null,null,now,"Authorized LinkedIn identity/profile connection. OIDC permissions do not imply certification or internship-history access."));
            if (headline != null && !headline.isBlank()) {
                evidence.save(new com.nexora.evidence.EvidenceRecord(0,reg,"PROFESSIONAL","linkedin_headline",
                        headline,"LinkedIn OIDC","OAUTH_VERIFIED",null,null,now,
                        "Headline returned by the authorized LinkedIn profile endpoint."));
            }
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(reg, "integration-session", java.util.List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        res.sendRedirect("/integrations?connected="+provider);
    }
}
