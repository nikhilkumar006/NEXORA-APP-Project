package com.nexora.career.market;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Search-first career catalogue. Only records backed by the cited Tevos H1 2026 report
 * carry role rank or salary fields. Curated catalogue entries intentionally expose no
 * salary, demand rank, or employer-specific hiring criteria without exact evidence.
 */
@Service
public class CareerDirectoryService {
    public static final String TEVOS_REPORT_URL = "https://gettevos.com/research-india-tech-hiring.php";
    public static final String TEVOS_REPORT_DATE = "28 Apr 2026";
    public static final String TEVOS_LIVE_URL = "https://gettevos.com/state-of-india.php";

    private final List<CareerRole> roles;
    private final List<CareerFamily> families;
    private final List<CareerCompany> companies;
    private final List<CareerLocation> locations;
    private final List<CareerSkill> skills;

    public CareerDirectoryService(MarketIntelligenceService market) {
        List<CareerRole> built = new ArrayList<>();
        for (MarketRole observed : market.roles()) {
            String title = canonicalTitle(observed);
            String family = familyFor(title);
            List<String> aliases = aliasesFor(title);
            built.add(new CareerRole(slug(title), title, family, familySlug(family), aliases,
                    observed.coreSkills(), observed.whatYouDo(), observed.entryPath(),
                    searchText(title, family, aliases, observed.coreSkills()), observed));
        }
        built.addAll(List.of(
            curated("full-stack-developer", "Full-Stack Developer", "Software Engineering",
                List.of("Full Stack Engineer", "Full Stack Developer", "Product Engineer"),
                List.of("JavaScript/TypeScript", "Frontend frameworks", "APIs", "SQL", "Testing", "Git"),
                "Build user-facing features and the server-side services that support them.",
                "Learn web fundamentals, build an end-to-end application, add tests, deploy it and document the design."),
            curated("backend-developer", "Backend Developer", "Software Engineering",
                List.of("Server-side Developer", "API Developer"),
                List.of("Java", "Python", "SQL", "REST APIs", "Testing", "Git"),
                "Build APIs, business logic, data persistence and server-side integrations.",
                "Choose one backend stack, learn SQL and HTTP, then build and deploy a tested API."),
            curated("api-engineer", "API Engineer", "Software Engineering",
                List.of("API Developer", "Integration Engineer"),
                List.of("HTTP", "REST", "OpenAPI", "Authentication", "Testing", "Observability"),
                "Design, implement and maintain reliable interfaces between software systems.",
                "Build versioned APIs, document contracts, test error cases and implement safe authentication."),
            curated("frontend-developer", "Frontend Developer", "Software Engineering",
                List.of("UI Engineer", "Web Interface Developer"),
                List.of("HTML", "CSS", "JavaScript", "TypeScript", "Accessibility", "Testing"),
                "Create accessible, responsive web interfaces that work across devices.",
                "Learn semantic HTML, CSS and JavaScript; build accessible projects and test them across viewports."),
            curated("web-developer", "Web Developer", "Software Engineering",
                List.of("Web Application Developer"),
                List.of("HTML", "CSS", "JavaScript", "HTTP", "APIs", "Git"),
                "Build and maintain websites and web applications.",
                "Master web fundamentals, build a responsive project, connect an API and deploy it."),
            curated("ai-engineer", "AI Engineer", "Data & AI",
                List.of("Generative AI Engineer", "Applied AI Engineer"),
                List.of("Python", "Model evaluation", "Data quality", "APIs", "RAG", "Security"),
                "Integrate AI capabilities into products and evaluate their reliability, safety and usefulness.",
                "Learn Python and data fundamentals, then build an evaluated AI feature with documented limitations."),
            curated("machine-learning-engineer", "Machine Learning Engineer", "Data & AI",
                List.of("ML Engineer"),
                List.of("Python", "Statistics", "Machine learning", "Model evaluation", "MLOps"),
                "Train, evaluate and deploy models as part of repeatable software systems.",
                "Build a strong statistics and ML foundation, validate data, compare baselines and deploy one reproducible project."),
            curated("data-scientist", "Data Scientist", "Data & AI",
                List.of("Applied Scientist"),
                List.of("Python", "Statistics", "SQL", "Experiment design", "Model evaluation"),
                "Use data analysis and statistical or machine-learning methods to answer defined questions.",
                "Learn statistics and SQL, practice experiment design, and publish reproducible analyses with clear assumptions."),
            curated("analytics-engineer", "Analytics Engineer", "Data & AI",
                List.of("BI Engineer", "Business Intelligence Engineer"),
                List.of("SQL", "Data modelling", "Python", "dbt", "Data quality"),
                "Transform source data into trusted, documented analytical datasets.",
                "Build SQL models with tests, lineage and definitions that analysts can review."),
            curated("mlops-engineer", "MLOps Engineer", "Data & AI",
                List.of("ML Platform Engineer"),
                List.of("Python", "CI/CD", "Containers", "Model monitoring", "Cloud"),
                "Build deployment, monitoring and governance workflows for ML systems.",
                "Learn software delivery and cloud foundations, then automate deployment and monitoring for a model."),
            curated("cloud-engineer", "Cloud Engineer", "Cloud & Infrastructure",
                List.of("Cloud Infrastructure Engineer"),
                List.of("Linux", "Networking", "Cloud IAM", "Infrastructure as code", "Monitoring"),
                "Provision and operate cloud infrastructure with attention to security, cost and reliability.",
                "Learn Linux and networks, use one cloud sandbox and document a secure deployment with least-privilege access."),
            curated("platform-engineer", "Platform Engineer", "Cloud & Infrastructure",
                List.of("Developer Platform Engineer"),
                List.of("Linux", "Containers", "Kubernetes", "CI/CD", "Observability"),
                "Build internal platforms that make software delivery safer and easier for engineering teams.",
                "Learn deployment automation and observability, then create a repeatable developer workflow."),
            curated("site-reliability-engineer", "Site Reliability Engineer", "Cloud & Infrastructure",
                List.of("SRE", "Reliability Engineer"),
                List.of("Linux", "Networking", "Monitoring", "Incident response", "Automation"),
                "Improve service reliability through engineering, measurement and operational automation.",
                "Learn systems fundamentals, measure service health and build an observable service with documented failure modes."),
            curated("database-administrator", "Database Administrator", "Data & AI",
                List.of("DBA", "Database Engineer"),
                List.of("SQL", "Indexing", "Backups", "Transactions", "Monitoring"),
                "Maintain data systems, reliability, access controls, performance and recovery.",
                "Learn relational database fundamentals, practise backup/restore and measure query performance."),
            curated("cybersecurity-analyst", "Cybersecurity Analyst", "Security",
                List.of("Security Analyst", "SOC Analyst"),
                List.of("Networking", "Linux", "Log analysis", "Incident response", "Security fundamentals"),
                "Investigate security alerts and help identify, contain and document threats.",
                "Build networking and operating-system fundamentals, use legal lab environments and document defensive investigations."),
            curated("application-security-engineer", "Application Security Engineer", "Security",
                List.of("Product Security Engineer", "AppSec Engineer"),
                List.of("Secure coding", "Threat modelling", "Web security", "SAST/DAST", "CI/CD"),
                "Find and reduce application security risks throughout the software lifecycle.",
                "Learn web security, practise in authorised labs and add security checks to a small application pipeline."),
            curated("qa-automation-engineer", "QA Automation Engineer", "Quality Engineering",
                List.of("Test Automation Engineer", "Automation Tester"),
                List.of("Test design", "Java/Python", "API testing", "Selenium/Playwright", "CI"),
                "Build reliable automated checks and quality feedback for software releases.",
                "Learn testing fundamentals, automate a realistic workflow and run the suite in CI."),
            curated("software-test-engineer", "Software Test Engineer", "Quality Engineering",
                List.of("QA Engineer", "Test Engineer"),
                List.of("Test planning", "Exploratory testing", "Defect reports", "API testing", "Automation basics"),
                "Evaluate software behavior, discover defects and help teams validate requirements.",
                "Practise test design, reproduce defects clearly and combine exploratory and automated testing."),
            curated("ios-developer", "iOS Developer", "Mobile & Embedded",
                List.of("iPhone App Developer"),
                List.of("Swift", "iOS SDK", "UI design patterns", "Networking", "Testing"),
                "Build and maintain applications for Apple platforms.",
                "Learn Swift and platform fundamentals, then ship a small accessible app with tests."),
            curated("mobile-app-developer", "Mobile App Developer", "Mobile & Embedded",
                List.of("Mobile Developer", "Cross-platform Developer"),
                List.of("Kotlin/Swift", "Mobile UI", "APIs", "Local storage", "Testing"),
                "Build applications for mobile devices and integrate device capabilities and backend services.",
                "Choose a mobile stack, build a complete small app and test on multiple screen sizes."),
            curated("embedded-software-engineer", "Embedded Software Engineer", "Mobile & Embedded",
                List.of("Embedded Engineer"),
                List.of("C/C++", "Microcontrollers", "RTOS", "Debugging", "Protocols"),
                "Write software that interacts with constrained hardware and real-world systems.",
                "Learn C, computer architecture and debugging; practise only on supported kits or simulators."),
            curated("firmware-engineer", "Firmware Engineer", "Mobile & Embedded",
                List.of("Firmware Developer"),
                List.of("C", "Microcontrollers", "Hardware interfaces", "Debugging", "Testing"),
                "Develop low-level software that controls or supports embedded devices.",
                "Study C and hardware interfaces, use documented development boards or simulators and test failure cases."),
            curated("systems-software-engineer", "Systems Software Engineer", "Systems & Emerging Tech",
                List.of("Systems Programmer"),
                List.of("C/C++", "Operating systems", "Concurrency", "Memory", "Debugging"),
                "Build software components close to the operating system, runtime or hardware boundary.",
                "Strengthen C/C++, OS and concurrency fundamentals, then implement and profile a systems project."),
            curated("game-developer", "Game Developer", "Systems & Emerging Tech",
                List.of("Gameplay Programmer"),
                List.of("C#", "C++", "Game engine", "Math", "Performance"),
                "Implement game behavior, gameplay systems and real-time interactive experiences.",
                "Choose an engine, make a small playable project, profile it and document the engineering decisions."),
            curated("blockchain-engineer", "Blockchain Engineer", "Systems & Emerging Tech",
                List.of("Smart Contract Developer"),
                List.of("Cryptography basics", "Distributed systems", "Smart contracts", "Testing", "Security"),
                "Build or integrate distributed-ledger software and carefully test security-critical components.",
                "Learn distributed systems and cryptographic basics first; use local test networks and security reviews."),
            curated("ux-engineer", "UX Engineer", "Product & Design",
                List.of("Design Engineer", "UI/UX Engineer"),
                List.of("HTML/CSS", "Accessibility", "Prototyping", "Design systems", "Usability testing"),
                "Bridge interaction design and front-end implementation to create usable, accessible experiences.",
                "Build a reusable interface, test it with users where possible and document accessibility decisions."),
            curated("accessibility-engineer", "Accessibility Engineer", "Product & Design",
                List.of("Web Accessibility Specialist"),
                List.of("WCAG", "Semantic HTML", "Screen readers", "Keyboard testing", "Automation"),
                "Evaluate and improve the accessibility of digital products and design systems.",
                "Study accessibility standards, test with keyboard and assistive technology, and document reproducible issues."),
            curated("data-engineer", "Data Engineer", "Data & AI",
                List.of("Big Data Engineer", "Data Platform Engineer"),
                List.of("SQL", "Python", "ETL/ELT", "Data modelling", "Cloud data services"),
                "Build reliable pipelines and platforms that make data available for analytics and machine learning.",
                "Build a tested data pipeline with validation, documentation and reproducible transformations."),
            curated("data-architect", "Data Architect", "Data & AI",
                List.of("Information Architect", "Data Platform Architect"),
                List.of("Data modelling", "SQL", "Governance", "Security", "Distributed systems"),
                "Design data structures, integration patterns and governance for an organisation's information systems.",
                "Practise conceptual and logical data modelling and document trade-offs for a small system."),
            curated("nlp-engineer", "Natural Language Processing Engineer", "Data & AI",
                List.of("NLP Engineer", "Language AI Engineer"),
                List.of("Python", "NLP", "Machine learning", "Evaluation", "Data preparation"),
                "Build systems that process, classify, search or generate human language.",
                "Compare a baseline and a model using a labelled dataset, appropriate metrics and error analysis."),
            curated("computer-vision-engineer", "Computer Vision Engineer", "Data & AI",
                List.of("Vision AI Engineer"),
                List.of("Python", "Image processing", "Deep learning", "Linear algebra", "Model evaluation"),
                "Develop software that extracts useful information from images or video.",
                "Build an image-classification or detection prototype and report its limitations and evaluation results."),
            curated("business-intelligence-developer", "Business Intelligence Developer", "Data & AI",
                List.of("BI Developer", "Reporting Developer"),
                List.of("SQL", "Data modelling", "Dashboards", "Data quality", "Communication"),
                "Create reliable reporting models and dashboards that help teams make evidence-based decisions.",
                "Build a dashboard from a documented dataset and validate totals against source queries."),
            curated("devops-engineer", "DevOps Engineer", "Cloud & Infrastructure",
                List.of("Build and Release Engineer"),
                List.of("Linux", "CI/CD", "Containers", "Cloud", "Infrastructure as code"),
                "Automate build, test, release and runtime workflows across development and operations.",
                "Create a CI pipeline for a small application and document rollback and secret-handling practices."),
            curated("solutions-architect", "Solutions Architect", "Cloud & Infrastructure",
                List.of("Cloud Solutions Architect", "Technical Architect"),
                List.of("System design", "Cloud architecture", "Networking", "Security", "Cost modelling"),
                "Design system-level solutions that balance requirements, reliability, security and cost.",
                "Write an architecture decision record and diagram for a small scalable application."),
            curated("network-engineer", "Network Engineer", "Cloud & Infrastructure",
                List.of("Network Administrator", "Network Operations Engineer"),
                List.of("TCP/IP", "Routing", "Switching", "DNS", "Network security"),
                "Design, configure and troubleshoot computer networks and their connectivity.",
                "Practise routing, subnetting and packet analysis in a legal network simulator or lab."),
            curated("database-developer", "Database Developer", "Data & AI",
                List.of("SQL Developer", "Database Programmer"),
                List.of("SQL", "Schema design", "Transactions", "Query tuning", "Data integrity"),
                "Design and implement database schemas, queries, procedures and data access patterns.",
                "Create a normalized schema, write parameterized queries and measure query plans."),
            curated("penetration-tester", "Penetration Tester", "Security",
                List.of("Ethical Hacker", "Security Testing Consultant"),
                List.of("Web security", "Networking", "Threat modelling", "Reporting", "Secure remediation"),
                "Assess systems for security weaknesses within an explicitly authorised scope.",
                "Use intentionally vulnerable training labs, document findings and provide reproducible remediation steps."),
            curated("incident-response-analyst", "Incident Response Analyst", "Security",
                List.of("DFIR Analyst", "Security Incident Analyst"),
                List.of("Log analysis", "Digital forensics", "Networking", "Incident handling", "Documentation"),
                "Investigate and coordinate response to security incidents using documented evidence.",
                "Practise with public sample logs and write a timeline, findings and containment recommendations."),
            curated("privacy-engineer", "Privacy Engineer", "Security",
                List.of("Data Privacy Engineer", "Privacy Technologist"),
                List.of("Data minimisation", "Threat modelling", "Access control", "Encryption concepts", "Compliance"),
                "Build technical controls that reduce privacy risk in products and data workflows.",
                "Map data flows for a small app and implement retention, access and deletion controls."),
            curated("robotics-software-engineer", "Robotics Software Engineer", "Systems & Emerging Tech",
                List.of("Robotics Developer", "Autonomy Software Engineer"),
                List.of("C++", "Python", "Control systems", "Sensors", "Simulation"),
                "Develop software for robot perception, planning, control or simulation.",
                "Start with a documented simulator, build one behaviour and test failure conditions."),
            curated("ar-vr-developer", "AR/VR Developer", "Systems & Emerging Tech",
                List.of("XR Developer", "Spatial Computing Developer"),
                List.of("3D maths", "Unity/Unreal", "Interaction design", "Performance", "Testing"),
                "Build interactive augmented, virtual or mixed-reality experiences.",
                "Prototype a small interaction in a supported engine and evaluate usability and performance."),
            curated("technical-writer", "Technical Writer", "Product & Design",
                List.of("Developer Documentation Engineer", "Documentation Specialist"),
                List.of("Technical communication", "API documentation", "Markdown", "Version control", "Information design"),
                "Explain software, APIs and technical workflows clearly for developers and users.",
                "Document an open-source API with a working quick-start, examples and troubleshooting guide."),
            curated("business-analyst", "IT Business Analyst", "Product & Design",
                List.of("Systems Analyst", "Technical Business Analyst"),
                List.of("Requirements analysis", "SQL basics", "Process modelling", "Communication", "Testing"),
                "Translate stakeholder needs into clear requirements, workflows and acceptance criteria.",
                "Create a process map, user stories and testable acceptance criteria for a real workflow."),
            curated("technical-product-manager", "Technical Product Manager", "Product & Design",
                List.of("Technical Product Owner", "Platform Product Manager"),
                List.of("Product discovery", "System concepts", "Metrics", "Prioritisation", "Communication"),
                "Coordinate product direction and delivery while balancing user, business and technical needs.",
                "Write a problem brief, prioritised roadmap and measurable success criteria for a student product."),
            curated("developer-advocate", "Developer Advocate", "Product & Design",
                List.of("Developer Relations Engineer", "DevRel"),
                List.of("Programming", "Public speaking", "Technical writing", "Demos", "Community feedback"),
                "Help developers succeed with a technology through examples, education and feedback loops.",
                "Publish a tested tutorial and demo project, then gather feedback from target users."),
            curated("erp-implementation-consultant", "ERP / Enterprise Systems Consultant", "Product & Design",
                List.of("Enterprise Application Consultant", "SAP Consultant"),
                List.of("Business processes", "SQL basics", "System configuration", "Integration", "Documentation"),
                "Configure and integrate enterprise software to support documented business processes.",
                "Learn a sandbox ERP workflow and document configuration, data flow and test cases."),
            curated("technical-support-engineer", "Technical Support Engineer", "Cloud & Infrastructure",
                List.of("Product Support Engineer", "Application Support Engineer"),
                List.of("Troubleshooting", "Networking", "Linux", "SQL basics", "Customer communication"),
                "Diagnose technical issues using logs, reproducible tests and clear customer communication.",
                "Build a troubleshooting playbook and practise diagnosing a sample service from logs."),
            curated("research-software-engineer", "Research Software Engineer", "Systems & Emerging Tech",
                List.of("Scientific Software Developer", "Research Programmer"),
                List.of("Python/C++", "Algorithms", "Reproducibility", "Testing", "Scientific computing"),
                "Build and maintain software used to conduct, reproduce or analyse research.",
                "Reproduce a published computational experiment with versioned code, tests and clear documentation."),
            curated("java-developer", "Java Developer", "Software Engineering",
                List.of("Java Application Developer", "JVM Developer"),
                List.of("Java", "OOP", "Collections", "Spring", "SQL", "Testing"),
                "Develop maintainable Java applications, services and integrations.",
                "Build a tested Java application, add persistence and document its API and error handling."),
            curated("python-developer", "Python Developer", "Software Engineering",
                List.of("Python Software Developer", "Python Backend Developer"),
                List.of("Python", "Data structures", "APIs", "SQL", "Testing", "Packaging"),
                "Build automation, APIs, data tools or application services using Python.",
                "Build a small packaged Python service with tests, structured logging and clear setup instructions."),
            curated("software-architect", "Software Architect", "Software Engineering",
                List.of("Application Architect", "Solution Software Architect"),
                List.of("Architecture patterns", "APIs", "Databases", "Security", "Scalability", "Trade-off analysis"),
                "Define system structure and technical trade-offs across components and teams.",
                "Document architecture decisions for a realistic system and explain reliability, security and cost trade-offs."),
            curated("distributed-systems-engineer", "Distributed Systems Engineer", "Systems & Emerging Tech",
                List.of("Distributed Platform Engineer"),
                List.of("Networking", "Concurrency", "Consistency", "Messaging", "Observability", "Fault tolerance"),
                "Design services that continue to behave predictably across machines and failures.",
                "Build a small multi-service demo and test retries, timeouts, idempotency and partial failure."),
            curated("compiler-engineer", "Compiler Engineer", "Systems & Emerging Tech",
                List.of("Language Tools Engineer", "Programming Language Engineer"),
                List.of("C/C++", "Parsing", "Compilers", "Algorithms", "Automata", "Debugging"),
                "Build or improve compilers, interpreters, language tooling and static-analysis components.",
                "Implement a small language parser or interpreter and add tests for syntax and runtime errors."),
            curated("embedded-software-engineer", "Embedded Software Engineer", "Mobile & Embedded",
                List.of("Embedded Systems Developer"),
                List.of("C/C++", "Memory constraints", "Concurrency", "Debugging", "Device interfaces", "Testing"),
                "Develop software that runs within devices with resource and timing constraints.",
                "Build a simulator or authorised development-board project and document timing and resource limits."),
            curated("firmware-engineer", "Firmware Engineer", "Mobile & Embedded",
                List.of("Low-level Software Engineer"),
                List.of("C", "C++", "Microcontrollers", "Protocols", "Debugging", "Real-time concepts"),
                "Implement low-level device software, boot behavior and hardware-facing protocols.",
                "Use a supported simulator or board to implement a small protocol and create reproducible tests."),
            curated("iot-software-engineer", "IoT Software Engineer", "Mobile & Embedded",
                List.of("Internet of Things Developer", "IoT Platform Developer"),
                List.of("Device messaging", "MQTT", "APIs", "Embedded basics", "Cloud", "Security"),
                "Connect devices to software services and manage device telemetry and commands.",
                "Build a simulated device-to-service pipeline with authenticated messages and observable failure handling."),
            curated("data-analyst", "Data Analyst", "Data & AI",
                List.of("Business Data Analyst", "Product Data Analyst"),
                List.of("SQL", "Python or R", "Statistics", "Visualisation", "Data quality"),
                "Clean and analyse datasets to answer business, product or operational questions.",
                "Publish a reproducible analysis with validated data, queries, clear charts and limitations."),
            curated("data-quality-engineer", "Data Quality Engineer", "Data & AI",
                List.of("Data Reliability Engineer"),
                List.of("SQL", "Data validation", "Python", "Testing", "Data pipelines", "Monitoring"),
                "Build checks that detect incomplete, inconsistent or unreliable data before it is consumed.",
                "Create a data pipeline with schema, freshness and quality tests and document alert handling."),
            curated("cloud-security-engineer", "Cloud Security Engineer", "Security",
                List.of("Cloud Security Specialist", "Cloud IAM Engineer"),
                List.of("Cloud IAM", "Threat modelling", "Networking", "Logging", "Infrastructure as code"),
                "Protect cloud identities, workloads, networks and configuration through preventive controls.",
                "Secure a sandbox deployment with least privilege, audit logs and documented threat scenarios."),
            curated("network-administrator", "Network Administrator", "Cloud & Infrastructure",
                List.of("Network Operations Administrator"),
                List.of("TCP/IP", "DNS", "Routing", "Switching", "Linux", "Monitoring"),
                "Maintain network availability, device configuration, access and troubleshooting procedures.",
                "Practise in a network simulator and document addressing, routing and failure diagnosis."),
            curated("systems-administrator", "IT Systems Administrator", "Cloud & Infrastructure",
                List.of("System Administrator", "Linux Administrator"),
                List.of("Linux", "Windows administration", "Networking", "Backups", "Identity", "Automation"),
                "Operate user, server and system environments with attention to access and recoverability.",
                "Automate a repeatable system task and test a backup-and-restore procedure in a lab."),
            curated("finops-engineer", "FinOps Engineer", "Cloud & Infrastructure",
                List.of("Cloud Cost Engineer"),
                List.of("Cloud billing", "SQL", "Automation", "Forecasting", "Tagging", "Cost optimisation"),
                "Help engineering teams understand and control cloud costs while preserving reliability.",
                "Analyse a sample cloud bill and build a transparent cost dashboard with optimisation hypotheses."),
            curated("threat-detection-engineer", "Threat Detection Engineer", "Security",
                List.of("Detection Engineer", "Security Analytics Engineer"),
                List.of("Log analysis", "Detection rules", "SIEM", "Python", "Incident response", "Threat modelling"),
                "Create and validate detections for suspicious activity using authorised security telemetry.",
                "Use a defensive lab to write a detection rule, test false positives and document response steps."),
            curated("performance-test-engineer", "Performance Test Engineer", "Quality Engineering",
                List.of("Load Test Engineer", "Performance QA Engineer"),
                List.of("Load testing", "Metrics", "Profiling", "HTTP", "Test design", "Observability"),
                "Measure latency, throughput and system behavior under representative load.",
                "Design a repeatable load test, record a baseline and explain bottlenecks without overclaiming capacity."),
            curated("test-infrastructure-engineer", "Test Infrastructure Engineer", "Quality Engineering",
                List.of("QA Platform Engineer"),
                List.of("CI/CD", "Containers", "Test frameworks", "Cloud runners", "Logging", "Reliability"),
                "Build reliable infrastructure and tooling for automated test execution at scale.",
                "Create a CI test pipeline with parallel execution, useful reports and failure triage instructions."),
            curated("salesforce-developer", "Salesforce Developer", "Product & Design",
                List.of("CRM Developer", "Salesforce Platform Developer"),
                List.of("Apex", "APIs", "Data modelling", "Security", "Testing", "Workflow automation"),
                "Customise and integrate CRM workflows and application logic on enterprise platforms.",
                "Use an authorised developer sandbox to build a workflow, permission model and automated tests."),
            curated("solutions-consultant", "Technical Solutions Consultant", "Product & Design",
                List.of("Solutions Engineer", "Implementation Consultant"),
                List.of("APIs", "Architecture", "Requirements", "Demonstrations", "Communication", "Troubleshooting"),
                "Translate customer needs into viable technical designs, demonstrations and implementation plans.",
                "Build a small proof of concept and explain constraints, integration points and deployment steps."),
            curated("technical-program-manager", "Technical Program Manager", "Product & Design",
                List.of("TPM", "Technical Delivery Manager"),
                List.of("Project planning", "Technical communication", "Risk management", "Dependencies", "Metrics"),
                "Coordinate cross-team technical delivery, dependencies, risk and measurable milestones.",
                "Plan a realistic software release with milestones, risk owners, dependency tracking and acceptance criteria.")
        ));
        // Keep one canonical entry for each slug. The sourced top-10 role takes precedence over a curated alias.
        Map<String, CareerRole> unique = new LinkedHashMap<>();
        for (CareerRole role : built) unique.putIfAbsent(role.slug(), role);
        this.roles = unique.values().stream()
                .sorted(Comparator.comparing(CareerRole::title, String.CASE_INSENSITIVE_ORDER)).toList();

        this.families = List.of(
            new CareerFamily("software-engineering","Software Engineering","Build application interfaces, APIs, services and full products."),
            new CareerFamily("data-ai","Data & AI","Work with data pipelines, analytical methods, machine learning and applied AI."),
            new CareerFamily("cloud-infrastructure","Cloud & Infrastructure","Automate delivery, cloud infrastructure, platform services and reliability."),
            new CareerFamily("security","Security","Protect applications, infrastructure and information through defensive engineering."),
            new CareerFamily("quality-engineering","Quality Engineering","Build testing strategies and automated quality checks."),
            new CareerFamily("mobile-embedded","Mobile & Embedded","Build mobile applications and software that interacts with devices."),
            new CareerFamily("systems-emerging-tech","Systems & Emerging Tech","Work close to operating systems, real-time systems and specialised software."),
            new CareerFamily("product-design","Product & Design","Connect technical implementation with product outcomes and usable experiences.")
        );
        this.companies = List.of(
            company("google","Google","https://www.google.com/","https://careers.google.com/",List.of("Google LLC","Alphabet")),
            company("microsoft","Microsoft","https://www.microsoft.com/","https://careers.microsoft.com/",List.of("Microsoft Corporation")),
            company("amazon","Amazon","https://www.amazon.com/","https://www.amazon.jobs/",List.of("Amazon Web Services","AWS")),
            company("tcs","Tata Consultancy Services","https://www.tcs.com/","https://www.tcs.com/careers",List.of("TCS")),
            company("infosys","Infosys","https://www.infosys.com/","https://www.infosys.com/careers/",List.of("Infosys Limited")),
            company("wipro","Wipro","https://www.wipro.com/","https://careers.wipro.com/",List.of("Wipro Limited")),
            company("zoho","Zoho","https://www.zoho.com/","https://www.zoho.com/careers.html",List.of("Zoho Corporation")),
            company("ibm","IBM","https://www.ibm.com/","https://www.ibm.com/careers",List.of("International Business Machines")),
            company("oracle","Oracle","https://www.oracle.com/","https://www.oracle.com/careers/",List.of("Oracle Corporation")),
            company("accenture","Accenture","https://www.accenture.com/","https://www.accenture.com/in-en/careers",List.of("Accenture India")),
            company("cognizant","Cognizant","https://www.cognizant.com/","https://careers.cognizant.com/global/en",List.of("Cognizant Technology Solutions")),
            company("nvidia","NVIDIA","https://www.nvidia.com/","https://www.nvidia.com/en-us/about-nvidia/careers/",List.of("Nvidia")),
            company("freshworks","Freshworks","https://www.freshworks.com/","https://www.freshworks.com/company/careers/",List.of("Freshworks Inc.")),
            company("flipkart","Flipkart","https://www.flipkart.com/","https://www.flipkartcareers.com/",List.of("Flipkart Internet"))
        );
        this.locations = List.of(
            new CareerLocation("bengaluru","Bengaluru","The linked Tevos H1 2026 role dataset reports p50 disclosed compensation for its top 10 roles in Bengaluru/Bangalore. Those values are medians, not averages or live vacancies.","Bengaluru Bangalore Banglore Karnataka"),
            new CareerLocation("hyderabad","Hyderabad","No role-specific salary snapshot for this city is bundled in NEXORA's current role dataset. National posting ranks are not city rankings.","Hyderabad Telangana"),
            new CareerLocation("chennai","Chennai","No role-specific salary snapshot for this city is bundled in NEXORA's current role dataset. National posting ranks are not city rankings.","Chennai Tamil Nadu"),
            new CareerLocation("pune","Pune","No role-specific salary snapshot for this city is bundled in NEXORA's current role dataset. National posting ranks are not city rankings.","Pune Maharashtra"),
            new CareerLocation("mumbai","Mumbai","No role-specific salary snapshot for this city is bundled in NEXORA's current role dataset. National posting ranks are not city rankings.","Mumbai Maharashtra"),
            new CareerLocation("delhi-ncr","Delhi NCR","No role-specific salary snapshot for this city is bundled in NEXORA's current role dataset. National posting ranks are not city rankings.","Delhi NCR New Delhi Gurugram Noida")
        );
        Map<String, CareerSkill> skillMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CareerRole role : roles) {
            for (String skill : role.skills()) {
                String cleaned = skill == null ? "" : skill.trim();
                if (cleaned.isEmpty()) continue;
                String key = normalize(cleaned);
                if (!skillMap.containsKey(key)) {
                    skillMap.put(key, new CareerSkill(slug(cleaned), cleaned,
                            roles.stream().filter(r -> r.skills().stream().anyMatch(s -> normalize(s).equals(key)))
                                    .map(CareerRole::slug).toList()));
                }
            }
        }
        this.skills = skillMap.values().stream().sorted(Comparator.comparing(CareerSkill::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static CareerRole curated(String slug, String title, String family, List<String> aliases,
                                      List<String> skills, String responsibilities, String entryPath) {
        return new CareerRole(slug, title, family, familySlug(family), aliases, skills, responsibilities, entryPath,
                searchText(title, family, aliases, skills), null);
    }

    private static CareerCompany company(String slug,String name,String homepage,String careers,List<String> aliases) {
        return new CareerCompany(slug,name,homepage,careers,aliases,searchText(name,"company",aliases,List.of()));
    }

    private static String familySlug(String familyName) {
        return canonicalFamilySlug(familyName);
    }

    private static String canonicalFamilySlug(String familyNameOrSlug) {
        String key = slug(familyNameOrSlug);
        return switch (key) {
            case "software-engineering" -> "software-engineering";
            case "data-ai", "data-and-ai" -> "data-ai";
            case "cloud-infrastructure", "cloud-and-infrastructure" -> "cloud-infrastructure";
            case "security" -> "security";
            case "quality-engineering" -> "quality-engineering";
            case "mobile-embedded", "mobile-and-embedded" -> "mobile-embedded";
            case "systems-emerging-tech", "systems-and-emerging-tech" -> "systems-emerging-tech";
            case "product-design", "product-and-design" -> "product-design";
            default -> key;
        };
    }

    private static String canonicalTitle(MarketRole r) {
        if (r.role().startsWith("ML Engineer")) return "ML Engineer";
        return r.role();
    }

    /** Assign sourced market roles to the same eight canonical families used by curated roles. */
    private static String familyFor(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);

        // Security must be checked before the generic word "analyst" so that, for example,
        // a Security Analyst never lands in Data & AI.
        if (containsAny(t, "security", "cybersecurity", "penetration", "appsec", "privacy",
                "threat detection", "incident response", "soc analyst")) {
            return "Security";
        }
        if (containsAny(t, "product manager", "product designer", "ux ", "ux engineer",
                "accessibility", "technical writer", "business analyst", "developer advocate",
                "enterprise systems consultant", "salesforce", "technical program manager",
                "technical solutions consultant")) {
            return "Product & Design";
        }
        if (containsAny(t, "qa", "sdet", "test", "quality engineer", "tester")) {
            return "Quality Engineering";
        }
        if (containsAny(t, "android", "mobile", "ios", "embedded", "firmware", "iot")) {
            return "Mobile & Embedded";
        }
        if (containsAny(t, "robotics", "game developer", "blockchain", "ar/vr", "compiler",
                "distributed systems", "systems software", "research software")) {
            return "Systems & Emerging Tech";
        }
        if (containsAny(t, "devops", "sre", "cloud", "platform engineer", "infrastructure",
                "network engineer", "network administrator", "systems administrator", "finops",
                "solutions architect", "technical support")) {
            return "Cloud & Infrastructure";
        }
        if (containsAny(t, "data", "analyst", "ml engineer", "machine learning", "ai engineer",
                "analytics", "database", "business intelligence", "natural language processing",
                "computer vision")) {
            return "Data & AI";
        }
        return "Software Engineering";
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    private static List<String> aliasesFor(String title) {
        return switch (title) {
            case "Software Engineer (Backend)" -> List.of("Backend Engineer", "Software Developer");
            case "Frontend Engineer" -> List.of("Frontend Developer", "Front-end Developer");
            case "ML Engineer" -> List.of("Machine Learning Engineer");
            case "DevOps / SRE" -> List.of("DevOps Engineer", "Site Reliability Engineer");
            case "QA / SDET" -> List.of("QA Engineer", "Software Development Engineer in Test");
            case "Android Engineer" -> List.of("Android Developer");
            default -> List.of();
        };
    }

    private static String searchText(String title,String family,List<String> aliases,List<String> skills) {
        return normalize(String.join(" ", title, family, String.join(" ",aliases),String.join(" ",skills)));
    }

    public static String slug(String value) {
        String ascii = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT).replace("&"," and ").replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");
    }

    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
    }

    public List<CareerRole> roles() { return roles; }
    public List<CareerFamily> families() { return families; }
    public List<CareerCompany> companies() { return companies; }
    public List<CareerLocation> locations() { return locations; }
    public List<CareerSkill> skills() { return skills; }

    public CareerRole role(String slugOrName) {
        String key = normalize(slugOrName);
        return roles.stream().filter(r -> slug(r.slug()).equals(slug(slugOrName)) ||
                normalize(r.title()).equals(key) ||
                r.aliases().stream().anyMatch(a -> normalize(a).equals(key))).findFirst().orElse(null);
    }

    public CareerFamily family(String slugOrName) {
        String key = slug(slugOrName);
        return families.stream().filter(f -> f.slug().equals(key) || slug(f.name()).equals(key)).findFirst().orElse(null);
    }

    public CareerCompany company(String slugOrName) {
        String key = normalize(slugOrName);
        return companies.stream().filter(c -> c.slug().equals(slug(slugOrName)) || normalize(c.name()).equals(key) ||
                c.aliases().stream().anyMatch(a -> normalize(a).equals(key))).findFirst().orElse(null);
    }

    public CareerLocation location(String slugOrName) {
        String key = slug(slugOrName);
        return locations.stream().filter(l -> l.slug().equals(key) || slug(l.name()).equals(key)).findFirst().orElse(null);
    }

    public CareerSkill skill(String slugOrName) {
        String key = normalize(slugOrName);
        return skills.stream().filter(s -> s.slug().equals(slug(slugOrName)) || normalize(s.name()).equals(key)).findFirst().orElse(null);
    }

    public List<CareerRole> rolesByFamily(String familySlug, String sortMode) {
        List<CareerRole> result = roles.stream().filter(r -> r.familySlug().equals(canonicalFamilySlug(familySlug))).collect(Collectors.toCollection(ArrayList::new));
        sort(result, sortMode);
        return result;
    }

    public List<CareerRole> rolesForSkill(String skillSlug) {
        CareerSkill found = skill(skillSlug);
        if (found == null) return List.of();
        return found.roleSlugs().stream().map(this::role).filter(Objects::nonNull)
                .sorted(Comparator.comparing(CareerRole::title,String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public List<CareerRole> related(CareerRole selected, int limit) {
        return roles.stream().filter(r -> !r.slug().equals(selected.slug()) && r.familySlug().equals(selected.familySlug()))
                .limit(Math.max(0,limit)).toList();
    }

    public List<CareerRole> rolesWithMarketEvidence() {
        return roles.stream().filter(r -> r.marketEvidence() != null)
                .sorted(Comparator.comparingInt(r -> demandRank(r.marketEvidence())))
                .toList();
    }

    public List<CareerRole> rolesForLocation(CareerLocation location) {
        if (location == null || !location.slug().equals("bengaluru")) return List.of();
        return rolesWithMarketEvidence();
    }

    public Optional<SearchTarget> bestSearchTarget(String query) {
        String q = normalize(query);
        if (q.isBlank()) return Optional.empty();
        List<SearchTarget> candidates = new ArrayList<>();
        for (CareerRole r : roles) {
            int score = matchScore(q, r.title(), r.aliases(), r.family(), r.skills());
            if (score < 99) candidates.add(new SearchTarget(r.title(),r.family(),"Role","/career/role/"+r.slug(),searchText(r.title(),r.family(),r.aliases(),r.skills()),score));
        }
        for (CareerFamily f : families) {
            int s=matchScore(q,f.name(),List.of(),f.name(),List.of());
            if(s<99)candidates.add(new SearchTarget(f.name(),"Career family","Family","/career/family/"+f.slug(),normalize(f.name()),s));
        }
        for (CareerCompany c : companies) {
            int s=matchScore(q,c.name(),c.aliases(),"Company",List.of());
            if(s<99)candidates.add(new SearchTarget(c.name(),"Company identity only","Company","/career/company/"+c.slug(),c.searchText(),s));
        }
        for (CareerLocation l : locations) {
            int s=matchScore(q,l.name(),List.of(), "Technology location",List.of());
            if(s<99)candidates.add(new SearchTarget(l.name(),"Location explorer","Location","/career/location/"+l.slug(),normalize(l.name()+" "+l.searchText()),s));
        }
        for (CareerSkill s : skills) {
            int score=matchScore(q,s.name(),List.of(),"Skill",List.of());
            if(score<99)candidates.add(new SearchTarget(s.name(),"Explore roles using this skill","Skill","/career/skill/"+s.slug(),normalize(s.name()),score));
        }
        return candidates.stream().min(Comparator.comparingInt(SearchTarget::score).thenComparing(SearchTarget::label,String.CASE_INSENSITIVE_ORDER));
    }

    private static int matchScore(String query,String title,List<String> aliases,String family,List<String> skills) {
        String t=normalize(title);
        if(t.equals(query))return 0;
        if(aliases.stream().anyMatch(a->normalize(a).equals(query)))return 1;
        if(t.startsWith(query))return 2;
        if(aliases.stream().anyMatch(a->normalize(a).startsWith(query)))return 3;
        if(normalize(family).equals(query))return 4;
        if(t.contains(query))return 5;
        if(aliases.stream().anyMatch(a->normalize(a).contains(query)))return 6;
        if(skills.stream().anyMatch(s->normalize(s).equals(query)))return 7;
        if(skills.stream().anyMatch(s->normalize(s).contains(query)))return 8;
        if(normalize(family).contains(query))return 9;
        return 99;
    }

    private static void sort(List<CareerRole> roles, String sortMode) {
        if ("demand".equalsIgnoreCase(sortMode)) {
            roles.sort(Comparator.comparingInt((CareerRole r) -> r.marketEvidence() == null ? Integer.MAX_VALUE : demandRank(r.marketEvidence()))
                    .thenComparing(CareerRole::title,String.CASE_INSENSITIVE_ORDER));
        } else roles.sort(Comparator.comparing(CareerRole::title,String.CASE_INSENSITIVE_ORDER));
    }

    private static int demandRank(MarketRole market) {
        if (market == null || market.demandRank() == null) return Integer.MAX_VALUE;
        try { return Integer.parseInt(market.demandRank().replaceAll("[^0-9]","")); }
        catch (Exception ignored) { return Integer.MAX_VALUE; }
    }

    public record CareerRole(String slug,String title,String family,String familySlug,List<String> aliases,
                             List<String> skills,String responsibilities,String entryPath,String searchText,
                             MarketRole marketEvidence) {}
    public record CareerFamily(String slug,String name,String description) {}
    public record CareerCompany(String slug,String name,String homepage,String careersUrl,List<String> aliases,String searchText) {}
    public record CareerLocation(String slug,String name,String evidenceNote,String searchText) {}
    public record CareerSkill(String slug,String name,List<String> roleSlugs) {}
    public record SearchTarget(String label,String subtitle,String kind,String href,String searchText,int score) {}
}
