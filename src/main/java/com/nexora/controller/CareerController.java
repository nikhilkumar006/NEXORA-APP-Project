package com.nexora.controller;

import com.nexora.career.*;
import com.nexora.career.market.LiveCareerMarketService;
import com.nexora.career.market.CareerDirectoryService;
import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Controller
public class CareerController {
    private final CareerService service;
    private final LiveCareerMarketService liveMarket;
    private final StudentRepository students;
    private final CareerDirectoryService directory;
    private final CareerGpsService gps;
    private final DevelopmentGpsPageCache developmentPageCache;

    public CareerController(CareerService service, LiveCareerMarketService liveMarket, StudentRepository students,
                           CareerDirectoryService directory, CareerGpsService gps, DevelopmentGpsPageCache developmentPageCache) {
        this.service = service;
        this.liveMarket = liveMarket;
        this.students = students;
        this.directory = directory;
        this.gps = gps;
        this.developmentPageCache = developmentPageCache;
    }

    @GetMapping("/career")
    public String page(Authentication a, Model m){
        if (a == null || !a.isAuthenticated() || a.getName() == null || "anonymousUser".equals(a.getName())) {
            return "redirect:/login";
        }
        Student student = students.findByLoginId(a.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        m.addAttribute("studentInitial", student.getFullName() != null && !student.getFullName().isBlank()
                ? student.getFullName().substring(0, 1).toUpperCase(java.util.Locale.ROOT) : "N");
        // The landing page is a static career-field index. Avoid unnecessary profile,
        // Career GPS, and live-market reads before displaying this page.
        addDirectoryIndex(m);
        m.addAttribute("pageType", "home");
        m.addAttribute("pageTitle", "Career Guidance");
        m.addAttribute("pageSubtitle", "Explore CSE career fields and the roles within each field.");
        return "career";
    }

    private void addDirectoryIndex(Model model) {
        // The Career Guidance landing page is deliberately limited to career fields.
        // Do not query live market providers or populate company/location/skill indexes
        // just to render this static directory page.
        java.util.List<com.nexora.career.market.CareerDirectoryService.CareerFamily> families =
                directory.families().stream()
                        .sorted(java.util.Comparator.comparing(
                                com.nexora.career.market.CareerDirectoryService.CareerFamily::name,
                                String.CASE_INSENSITIVE_ORDER))
                        .toList();
        java.util.Map<String, Integer> familyRoleCounts = new java.util.LinkedHashMap<>();
        for (com.nexora.career.market.CareerDirectoryService.CareerFamily family : families) {
            familyRoleCounts.put(family.slug(), directory.rolesByFamily(family.slug(), "az").size());
        }
        model.addAttribute("directoryFamilies", families);
        model.addAttribute("familyRoleCounts", familyRoleCounts);
    }

    @GetMapping("/career/market-data")
    @ResponseBody
    public LiveCareerMarketService.Snapshot marketData(){ return liveMarket.get(); }

    @PostMapping("/career")
    @Transactional
    public String save(Authentication a,@RequestParam String primaryPath,@RequestParam String secondaryPath,@RequestParam String higherStudyPreference,
                       @RequestParam String targetRole,@RequestParam String targetBand,@RequestParam(defaultValue="SRM-PLACEMENT-2026") String sourceVersion){
        Student student = authenticatedStudent(a);
        if (student == null) return "redirect:/login?error=account";
        if (targetRole == null || targetRole.isBlank() || directory.role(targetRole) == null)
            return "redirect:/career?error=role";
        if (!java.util.Set.of("Standard", "Dream", "Super Dream", "Marquee").contains(targetBand))
            return "redirect:/career?error=band";
        String source = sourceVersion != null && sourceVersion.matches("SRM-PLACEMENT-[0-9]{4}")
                && service.availableSourceVersions().contains(sourceVersion) ? sourceVersion : "SRM-PLACEMENT-2026";
        String path = primaryPath != null && primaryPath.toLowerCase(java.util.Locale.ROOT).contains("off-campus")
                ? "Off-campus placement" : "On-campus placement";
        String secondary = secondaryPath != null && secondaryPath.toLowerCase(java.util.Locale.ROOT).contains("on-campus")
                ? "On-campus placement" : "Off-campus placement";
        if (path.equals(secondary)) secondary = path.equals("On-campus placement") ? "Off-campus placement" : "On-campus placement";
        String canonicalRole = directory.role(targetRole).title();
        service.saveProfile(new CareerProfile(student.getRegistrationNo(),path,secondary,
                higherStudyPreference == null || higherStudyPreference.isBlank() ? "Keep options open" : higherStudyPreference.trim(),
                canonicalRole,targetBand,source));
        // Keep the shared role mapping in sync: Career Selection, Development GPS and
        // Recommended Courses all consume CareerGpsSelection as the canonical target.
        // Preserve company/programme only when the previous off-campus choice still belongs
        // to this same role; never carry a stale employer programme into a different role.
        CareerGpsSelection oldSelection = gps.find(student.getRegistrationNo()).orElse(null);
        boolean sameRole = oldSelection != null && oldSelection.targetRole() != null
                && directory.role(oldSelection.targetRole()) != null
                && directory.role(oldSelection.targetRole()).title().equalsIgnoreCase(canonicalRole);
        String company = "Off-campus placement".equals(path) && sameRole
                && "Off-campus placement".equalsIgnoreCase(oldSelection.primaryPath())
                ? java.util.Objects.toString(oldSelection.targetCompany(), "") : "";
        String program = company.isBlank() ? "Not specified" : java.util.Objects.toString(oldSelection.targetProgram(), "Not specified");
        gps.save(new CareerGpsSelection(student.getRegistrationNo(), path, company, program, canonicalRole, targetBand, source));
        developmentPageCache.evict(student.getRegistrationNo());
        return "redirect:/career?saved";
    }

    @PostMapping("/career/placement-profile")
    public String savePlacement(Authentication a,
        @RequestParam(required=false) Double cgpa,@RequestParam(defaultValue="0") Integer activeArrears,
        @RequestParam(required=false) Double aptitude,@RequestParam(required=false) Double technicalSkills,
        @RequestParam(defaultValue="0") Integer codingProblems,@RequestParam(defaultValue="0") Integer leetcode,
        @RequestParam(required=false) Double hackerRank,@RequestParam(required=false) Double codingAssessment,
        @RequestParam(defaultValue="0") Integer internships,@RequestParam(defaultValue="0") Integer projects,
        @RequestParam(defaultValue="0") Integer certifications,@RequestParam(defaultValue="0") Integer hackathons,
        @RequestParam(defaultValue="0") Integer competitiveProgramming,@RequestParam(defaultValue="0") Integer githubRepositories,
        @RequestParam(defaultValue="0") Integer implementedProjects,@RequestParam(defaultValue="false") Boolean linkedInUpdated,
        @RequestParam(defaultValue="false") Boolean portfolio,@RequestParam(defaultValue="false") Boolean ePortfolio,
        @RequestParam(required=false) Double communication,@RequestParam(required=false) Double groupDiscussion,@RequestParam(required=false) Double presentation,
        @RequestParam(required=false) Double technicalMock,@RequestParam(required=false) Double hrMock,
        @RequestParam(defaultValue="Basic Readiness") String resumeLevel,@RequestParam(defaultValue="Satisfactory") String professionalBehaviour,
        @RequestParam(required=false) Double placementTrainingAttendance,@RequestParam(required=false) Double placementAssessmentAttendance,
        @RequestParam(defaultValue="false") Boolean resumeVerified,@RequestParam(defaultValue="false") Boolean profileVerified,
        @RequestParam(defaultValue="0") Integer industryExposure,@RequestParam(required=false) Double tenthPercentile,@RequestParam(required=false) Double twelfthPercentile){
        Student student = authenticatedStudent(a);
        if (student == null) return "redirect:/login?error=account";
        service.savePlacementProfile(new PlacementProfile(student.getRegistrationNo(),cgpa,activeArrears,aptitude,technicalSkills,codingProblems,leetcode,hackerRank,codingAssessment,internships,projects,certifications,hackathons,competitiveProgramming,githubRepositories,implementedProjects,linkedInUpdated,portfolio,ePortfolio,communication,groupDiscussion,presentation,technicalMock,hrMock,resumeLevel,professionalBehaviour,placementTrainingAttendance,placementAssessmentAttendance,resumeVerified,profileVerified,industryExposure,tenthPercentile,twelfthPercentile));
        return "redirect:/career?savedProfile";
    }

    private Student authenticatedStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || authentication.getName().isBlank() || "anonymousUser".equals(authentication.getName())) return null;
        return students.findByLoginId(authentication.getName()).orElse(null);
    }
}
