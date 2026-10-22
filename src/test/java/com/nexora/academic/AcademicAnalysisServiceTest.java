package com.nexora.academic;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AcademicAnalysisServiceTest {
    private final AcademicAnalysisService service = new AcademicAnalysisService();
    private static final List<String> CURRENT_SEMESTER_SUBJECTS = List.of(
            "21MAB201T", "21DCS201P", "21CSS201T", "21CSC201J", "21CSC202J", "21CSC203P");

    @Test
    void everyConfiguredCurrentSemesterSubjectHasFiveOrderedUnits() {
        for (String subjectCode : CURRENT_SEMESTER_SUBJECTS) {
            List<AcademicUnit> units = service.getUnits(subjectCode);
            assertEquals(5, units.size(), subjectCode + " must have five syllabus units");
            for (int index = 0; index < units.size(); index++) {
                AcademicUnit unit = units.get(index);
                assertTrue(unit.getUnitName().startsWith("Unit " + (index + 1)), subjectCode + " unit order");
                assertNotNull(unit.getTopics(), subjectCode + " topics must not be null");
                assertFalse(unit.getTopics().isEmpty(), subjectCode + " each unit must contain syllabus topics");
                assertNotNull(unit.getDescription());
                assertFalse(unit.getDescription().isBlank(), subjectCode + " unit summary must not be blank");
                assertTrue(unit.getTopics().stream().allMatch(t -> t.getTopic() != null && !t.getTopic().isBlank()),
                        subjectCode + " topic labels must be present");
            }
        }
    }

    @Test
    void unsupportedSubjectsDoNotReceiveInventedSyllabus() {
        assertTrue(service.getTopics("unknown").isEmpty());
        assertTrue(service.getUnits("unknown").isEmpty());
        assertTrue(service.getUnits(null).isEmpty());
    }

    @Test
    void callsReturnIndependentLists() {
        List<AcademicTopic> first = service.getTopics("21DCS201P");
        int expected = service.getTopics("21DCS201P").size();
        first.clear();
        assertEquals(expected, service.getTopics("21DCS201P").size());
    }
}
