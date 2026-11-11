# Neopat and O*NET integration notes

## Neopat / Examly
- The Connections page opens the official SRM Trichy Neopat login in a new tab. NEXORA does not request or store Neopat credentials.
- No Neopat API credentials or documented API endpoint are configured in this project. Therefore the app does not claim live automatic synchronization.
- If a student can export progress as CSV, the import accepts a header containing a module/name column (`module`, `module_name`, `lesson`, `activity`, `name`, or `title`) and a status column (`status`, `progress`, `completion`, `completed`, or `state`).
- Upload limit is 2 MB and 2,000 data rows. Rows are stored per student in `nexora_neopat_progress`; re-importing the same module updates its status. The imported records are student-provided snapshots, not provider-verified records.
- To enable real automatic sync later, obtain official provider API documentation, approved credentials and permission. Do not implement password scraping or browser automation against student accounts.

## O*NET OnLine
- Career Guidance and Connections link to https://www.onetonline.org/.
- O*NET is a U.S. occupational information resource. NEXORA labels it as a reference and does not present it as India-specific salary, demand, or employer eligibility evidence.
- The existing NEXORA Career GPS market dossier remains the source for its dated, attributed market snapshots. O*NET links complement role exploration rather than replacing NEXORA's market evidence.

## Runtime verification
The project still requires a configured MySQL database and the Java/Maven dependencies described in the main README. Validate upload persistence and all routes in a local configured runtime before deployment.


## Current UI and safe use
- The Connections page exposes a Neopat CSV import form that posts to `/integrations/neopat/import`.
- CSV imports accept up to 2 MB and 2,000 data rows. A module/activity/title column and a status/progress/completion column are required. A UTF-8 BOM on the first header is supported.
- O*NET references are available in Career Guidance for each catalogue role via the official O*NET OnLine search. These are external official searches, not an API-backed data sync. O*NET is U.S.-focused and must not be treated as Indian eligibility or salary evidence.
