# NEXORA Career Guidance Repair R4

## What changed

- Career Guidance landing page now shows only the CSE career fields. It no longer puts O*NET, live-market cards, the all-roles catalogue, skill chips, employers, locations, or personalised recommendations on the landing page.
- Each career-field page now renders that field's actual role list, with a local role search. Selecting a role opens its role-detail page; the role page keeps its existing Career Selection link.
- The home route no longer performs Career Profile, Career GPS, or live-market reads simply to render the field directory.
- Role categorisation for sourced market roles checks security/test/mobile/systems/cloud/product groups before generic data/analyst matching so a security analyst cannot be mistakenly assigned to Data & AI.
- Fixed the compiler error in `AcademicPageReadCache`: the stale-entry cleanup lambda now captures a separate effectively-final timestamp instead of `now`, which is reassigned later in the method.
- Incremented the Career Guidance CSS/JavaScript cache key to `20261013-career-fields-r3`.

## Career fields and verified counts

### Software Engineering (10)
- API Engineer
- Backend Developer
- Frontend Developer
- Frontend Engineer
- Full-Stack Developer
- Java Developer
- Python Developer
- Software Architect
- Software Engineer (Backend)
- Web Developer

### Data & AI (15)
- AI Engineer
- Analytics Engineer
- Business Intelligence Developer
- Computer Vision Engineer
- Data Analyst
- Data Architect
- Data Engineer
- Data Quality Engineer
- Data Scientist
- Database Administrator
- Database Developer
- Machine Learning Engineer
- ML Engineer
- MLOps Engineer
- Natural Language Processing Engineer

### Cloud & Infrastructure (11)
- Cloud Engineer
- DevOps / SRE
- DevOps Engineer
- FinOps Engineer
- IT Systems Administrator
- Network Administrator
- Network Engineer
- Platform Engineer
- Site Reliability Engineer
- Solutions Architect
- Technical Support Engineer

### Security (8)
- Application Security Engineer
- Cloud Security Engineer
- Cybersecurity Analyst
- Incident Response Analyst
- Penetration Tester
- Privacy Engineer
- Security Engineer
- Threat Detection Engineer

### Quality Engineering (5)
- Performance Test Engineer
- QA / SDET
- QA Automation Engineer
- Software Test Engineer
- Test Infrastructure Engineer

### Mobile & Embedded (6)
- Android Engineer
- Embedded Software Engineer
- Firmware Engineer
- iOS Developer
- IoT Software Engineer
- Mobile App Developer

### Systems & Emerging Tech (8)
- AR/VR Developer
- Blockchain Engineer
- Compiler Engineer
- Distributed Systems Engineer
- Game Developer
- Research Software Engineer
- Robotics Software Engineer
- Systems Software Engineer

### Product & Design (11)
- Accessibility Engineer
- Developer Advocate
- ERP / Enterprise Systems Consultant
- IT Business Analyst
- Product Manager
- Salesforce Developer
- Technical Product Manager
- Technical Program Manager
- Technical Solutions Consultant
- Technical Writer
- UX Engineer

**Directory validation: PASS** — 74 of 74 roles mapped; every field is non-empty; all role slugs resolve.

## Checks performed

- `AcademicPageReadCache.java` and the career-directory service plus its data dependencies compiled with `javac` using minimal Spring annotation stubs. This specifically checks the fixed lambda and the actual directory implementation, but is not a full application build.
- Directory harness verified all eight fields have at least one role, every role's family slug matches its containing field, all 74 role slugs resolve through `CareerDirectoryService.role(...)`, and field totals equal the unique directory role count.
- `node --check src/main/resources/static/js/career-explorer.js` passed.
- Career template structure and cache-busted JavaScript references were checked.
- Full Maven compile could not be run in this environment: `./mvnw -B -DskipTests compile` failed before compilation because Maven 3.9.16 could not be downloaded from Maven Central. Please run the full build in IntelliJ/networked environment and test the routes against the configured database before relying on the complete website runtime.
