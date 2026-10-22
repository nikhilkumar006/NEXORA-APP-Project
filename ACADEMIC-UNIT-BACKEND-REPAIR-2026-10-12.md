# NEXORA Academic Unit Backend Repair — 12 October 2026

## Unit workspace behavior
- The unit workspace has exactly four tabs: Chapter Weightage, Topic Weightage, Prerequisites, and PYQ Topic Intelligence.
- Topic Weightage now lists every syllabus topic, including zero-evidence topics. Counts and percentages come from approved/indexed semester-PYQ question mappings; the no-evidence message remains visible until evidence exists.
- Chapter Weightage calculates each unit's evidence from topic-mapped approved/indexed semester-PYQ questions and normalizes all unit percentages to 100% when evidence exists. It does not fabricate a split when no evidence exists.
- Prerequisite relationships now combine database topic-dependency mappings with prerequisite lists declared in the syllabus objects. The panel still lists each syllabus topic and shows direct/full-chain/dependent-topic information where mapped.
- PYQ Topic Intelligence now uses indexed semester-PYQ question records for the current unit, showing topic, question count, marks, years, and question models. It clearly reports when there is no mapped evidence. It does not OCR original papers.
- Topic matching normalizes case, whitespace, and punctuation to reduce missed mappings caused by inconsistent formatting.

## Verification
- Reviewed controller/template wiring and ZIP integrity.
- Attempted `./mvnw -q -DskipTests compile`, but Maven Wrapper could not download Maven from Maven Central in this environment. A full compilation, live database test, and Google Drive/PYQ integration test are therefore not claimed as passed. Run the build in the user's development environment after network access is available.
