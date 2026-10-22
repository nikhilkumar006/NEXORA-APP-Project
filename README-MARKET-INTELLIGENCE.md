# NEXORA Career Market Intelligence

## What the feature does

Career GPS now lets a student choose a role and view:
- current market salary snapshot
- demand/posting position
- year-on-year movement in the source dataset
- top skill and core skill cluster
- what the role actually does
- shortest credible student entry path
- future outlook
- future drivers
- watch-outs / uncertainty
- source and snapshot date

## Data policy

NEXORA does not present future salary or hiring as guaranteed. The future section is a directional evidence summary based on published labour-market research. Current salary numbers are source snapshots and can vary by city, company, experience, disclosed compensation, and methodology.

## Sources used in the current snapshot

1. Tevos — State of Tech Hiring India H1 2026: https://gettevos.com/research-india-tech-hiring.php
2. Tevos — In-Demand Tech Skills India 2026: https://gettevos.com/skills.php
3. World Economic Forum — Future of Jobs Report 2025: https://www.weforum.org/publications/the-future-of-jobs-report-2025/in-full/2-jobs-outlook/
4. U.S. Bureau of Labor Statistics — 2024–34 occupational outlook: https://www.bls.gov/opub/mlr/2026/article/industry-and-occupational-employment-projections-overview.htm
5. Press Information Bureau / NITI Aayog — Roadmap for Job Creation in the AI Economy: https://www.pib.gov.in/PressReleasePage.aspx?PRID=2177440&lang=1&reg=3

## Future enhancement

A later version can replace the static snapshot layer with a licensed/API-backed market feed. Until an official API/data license is connected, the application should keep the snapshot date and source visible rather than implying live real-time data.

## NEXORA presentation rule
The Career page labels role compensation as a p50/median source snapshot, not an average or guaranteed salary. The market pulse uses a dated live-market snapshot (25 Sep 2026 in this build) and shows its source link. The 2030 outlook is directional evidence from published labour-market research; it is not a promise or probability forecast. If a licensed market API is configured later, it can replace the built-in snapshot without changing the UI contract.
