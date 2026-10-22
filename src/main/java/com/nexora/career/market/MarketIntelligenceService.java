package com.nexora.career.market;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MarketIntelligenceService {
    private final List<MarketRole> roles = List.of(
        new MarketRole("Software Engineer (Backend)","Backend Engineer","#1", "₹24.5 LPA", "Bengaluru p50 advertised compensation", "+12% YoY", "Strong", "Python",
            List.of("Python","SQL","APIs","Cloud","System design","Git"),
            "Build APIs, services, data flows and the server-side systems behind products.",
            "DSA + one backend stack + SQL + REST + Git → projects → internship → interview readiness.",
            "Strong growth signal", "Technology roles remain among the fastest-growing categories; software/application developers are highlighted by WEF.",
            List.of("AI-assisted development","Cloud adoption","Digital access","Product engineering"),
            List.of("AI-assisted coding raises the bar for fundamentals","Hiring varies sharply by company and city"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("Product Manager","Product Manager","#2", "₹32.0 LPA", "Bengaluru p50 advertised compensation", "+31% YoY", "Very strong", "SQL",
            List.of("Product thinking","SQL","Analytics","User research","Communication","Experimentation"),
            "Define product problems, prioritize work, coordinate teams and measure product outcomes.",
            "Business/product fundamentals + analytics + product case studies + shipped projects.",
            "Positive growth signal", "Tevos reported the role as the #2 posting-volume role in its India H1 2026 corpus.",
            List.of("AI-enabled products","Digital adoption","Data-driven decision making"),
            List.of("Role definitions differ widely between companies","Strong communication and product evidence matter"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("Data Engineer","Data Engineer","#3", "₹22.0 LPA", "Bengaluru p50 advertised compensation", "+19% YoY", "Strong", "Spark",
            List.of("SQL","Python","Spark","ETL/ELT","Data modelling","Cloud"),
            "Build reliable pipelines and platforms that collect, transform and serve data for analytics and AI.",
            "SQL + Python + data modelling + one cloud/data platform + 2 pipeline projects.",
            "Strong growth signal", "Data-heavy and AI-enabled workflows are expected to keep increasing demand for data capabilities.",
            List.of("AI/data infrastructure","Analytics scale","Cloud platforms","Real-time data"),
            List.of("Tooling changes quickly","Production data quality matters as much as coding"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("Frontend Engineer","Frontend Engineer","#4", "₹20.5 LPA", "Bengaluru p50 advertised compensation", "+18% YoY", "Strong", "React",
            List.of("JavaScript/TypeScript","React","HTML/CSS","Accessibility","Testing","APIs"),
            "Build accessible, responsive product interfaces and connect them to application services.",
            "JavaScript/TypeScript + React + UI fundamentals + testing + 2 polished deployed projects.",
            "Positive growth signal", "Frontend engineering remains a high-volume technical role; React mentions rose in Tevos' 2026 skills snapshot.",
            List.of("Digital access","Product experience","TypeScript adoption","AI-assisted interfaces"),
            List.of("Basic UI-only portfolios are less differentiated","Performance and accessibility increasingly matter"),
            "Tevos State of Tech Hiring India H1 2026", "Apr 2026", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("ML Engineer / AI Engineer","ML / AI Engineer","#5", "₹28.0 LPA", "Bengaluru p50 advertised compensation for ML Engineer", "+26% YoY", "Very strong", "PyTorch",
            List.of("Python","PyTorch","ML fundamentals","Statistics","LLMs/RAG","MLOps"),
            "Develop, evaluate and deploy machine-learning systems and AI features in real products.",
            "Python + statistics + ML + deep learning + one deployed AI project + evaluation discipline.",
            "Very strong future signal", "WEF lists AI and machine-learning specialists among the fastest-growing roles; AI and big data are among the fastest-growing skills.",
            List.of("Generative AI","AI infrastructure","Automation","AI adoption across industries"),
            List.of("Tool knowledge alone is not enough","Model evaluation, data quality and systems skills are increasingly important"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("DevOps / SRE","DevOps / SRE","#6", "₹21.0 LPA", "Bengaluru p50 advertised compensation", "+9% YoY", "Strong", "Kubernetes",
            List.of("Linux","Cloud","Docker","Kubernetes","Terraform","CI/CD","Observability"),
            "Automate delivery and operate reliable, scalable software infrastructure.",
            "Linux + networking + cloud + Docker + Kubernetes + CI/CD + incident/project evidence.",
            "Positive growth signal", "Cloud/platform demand is supported by growing digital infrastructure and technology adoption.",
            List.of("Cloud adoption","Platform engineering","AI infrastructure","Reliability needs"),
            List.of("On-call expectations vary","Production troubleshooting requires hands-on practice"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("Data Analyst","Data Analyst","#7", "₹14.5 LPA", "Bengaluru p50 advertised compensation", "+11% YoY", "Growing", "SQL",
            List.of("SQL","Excel","Python","Statistics","Power BI/Tableau","Communication"),
            "Turn business data into analysis, dashboards and decisions.",
            "SQL + spreadsheet analytics + visualization + statistics + 2 business case projects.",
            "Growing with skill shift", "Analytical thinking remains important while AI increases the value of people who can frame and validate decisions.",
            List.of("Data-driven decisions","AI-assisted analytics","Digital transformation"),
            List.of("Routine reporting is increasingly automated","Business context differentiates candidates"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("Android Engineer","Android Engineer","#8", "₹18.0 LPA", "Bengaluru p50 advertised compensation", "−3% YoY", "Stable / mixed", "Kotlin",
            List.of("Kotlin","Android SDK","Jetpack","Coroutines","Testing","APIs"),
            "Build and maintain Android applications, from UI and device integration to production releases.",
            "Kotlin + Android fundamentals + Jetpack + API integration + 2 polished Android projects + testing.",
            "Mixed but evolving", "Tevos placed Android Engineer at #8 by India posting volume in its H1 2026 corpus; the global outlook is shaped more by digital access and software specialization than by one platform.",
            List.of("Mobile digital access","On-device AI","Modern Android tooling","Cross-platform competition"),
            List.of("Platform-specific demand can move with product strategy","Strong Android candidates need architecture and testing, not only UI skills"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("Security Engineer","Security Engineer","#9", "₹26.5 LPA", "Bengaluru p50 advertised compensation", "+17% YoY", "Strong", "SAST/DAST",
            List.of("Networking","Linux","Web security","SAST/DAST","Cloud security","Threat modelling"),
            "Find and reduce security weaknesses across applications, infrastructure and development pipelines.",
            "Networking + Linux + security fundamentals + labs + secure coding + practical security projects.",
            "Strong future signal", "WEF highlights networks and cybersecurity among the fastest-growing skill areas; BLS also projects strong information-security employment growth in the US.",
            List.of("Rising cyber risk","Cloud security","Secure software supply chains","AI-enabled attacks and defence"),
            List.of("Security requires continuous learning","Certifications help but hands-on labs matter"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025 + US BLS", "Apr 2026 / Jan 2025 / 2026", "https://gettevos.com/research-india-tech-hiring.php"),
        new MarketRole("QA / SDET","QA / SDET","#10", "₹13.0 LPA", "Bengaluru p50 advertised compensation", "−6% YoY", "Mixed", "Selenium",
            List.of("Testing","Selenium","API testing","Java/Python","CI/CD","Test design"),
            "Design automated and exploratory tests that protect software quality across releases.",
            "Testing fundamentals + automation framework + API testing + CI + one realistic automation project.",
            "Role is changing", "Automation remains important, but routine testing work is increasingly assisted by AI and modern developer tooling.",
            List.of("Automation","AI-assisted testing","Continuous delivery","Quality engineering"),
            List.of("Manual-only profiles face more automation pressure","Strong SDET profiles need programming and systems understanding"),
            "Tevos State of Tech Hiring India H1 2026 + WEF Future of Jobs 2025", "Apr 2026 / Jan 2025", "https://gettevos.com/research-india-tech-hiring.php")
    );

    private static final MarketPulse PULSE = new MarketPulse(
            "25 Sep 2026", "22,968", "857", "19,970", "2,729",
            List.of("Python 15,901", "SQL 13,983", "Java 11,765", "AWS 11,687", "Azure 11,021", "GenAI 8,005"),
            "Tevos — The State of India Tech Hiring", "https://gettevos.com/state-of-india.php");

    public List<MarketRole> roles(){ return roles; }
    public MarketPulse pulse(){ return PULSE; }
    public MarketRole find(String requested){
        if(requested==null || requested.isBlank()) return roles.get(0);
        String q=requested.trim().toLowerCase(Locale.ROOT);
        if(q.contains("backend") || q.equals("software developer") || q.equals("java developer") || q.equals("python developer")) return roles.get(0);
        if(q.contains("product")) return roles.get(1);
        if(q.contains("data engineer")) return roles.get(2);
        if(q.contains("frontend") || q.contains("full stack")) return roles.get(3);
        if(q.contains("ai") || q.contains("ml") || q.contains("machine")) return roles.get(4);
        if(q.contains("devops") || q.contains("cloud")) return roles.get(5);
        if(q.contains("data analyst")) return roles.get(6);
        if(q.contains("security") || q.contains("cyber")) return roles.get(7);
        if(q.contains("qa") || q.contains("automation")) return roles.get(8);
        return roles.stream().filter(r -> r.role().toLowerCase(Locale.ROOT).contains(q) || q.contains(r.shortName().toLowerCase(Locale.ROOT))).findFirst().orElse(roles.get(0));
    }
}
