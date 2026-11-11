# SRMIST HOD Placement Planner Integration

NEXORA combines the two uploaded SRMIST Tiruchirappalli placement-planner workbooks into `src/main/resources/data/placement-planner.json`. The CORE workbook supplies the pre-placement readiness criteria; the TRY workbook adds the coding/problem-solving, LeetCode, HackerRank/equivalent, coding assessment, domain-skill and related mapping benchmarks. The content is separated into 2028, 2029 and 2030 year-of-passing (YOP) cohorts and the four bands: Standard, Dream, Super Dream and Marquee.

The selected cohort uses the student's stored `batch_end_year`. If that field is not one of the supported planner years, the service tries `batch_start_year + 4` (the B.Tech four-year cohort convention). It does not silently substitute a different planner year: if neither maps to 2028, 2029 or 2030, the planner stays empty rather than showing incorrect criteria. The selected year/band is shown in Career Selection and Development GPS.

Planner values are internal readiness targets, not a guarantee of employer eligibility or placement package. The source workbook has a 2030 sheet whose title cell says `2029 YOP`; the sheet tab name is used as the cohort label for that tab.
