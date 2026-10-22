# NEXORA performance and intermittent-loading repair — 12 Oct 2026

## Changes made

1. **Database schema initialization timing**
   - Moved `DatabaseSchemaInitializer` from `ApplicationReadyEvent` to `@PostConstruct`.
   - Previously the embedded HTTP server could begin accepting requests while the post-ready schema/compatibility work was still running. That created a race where early navigation could hit missing tables/columns and work only after refreshing. Schema compatibility work now runs while the application context is being created, before the web server is initialized.

2. **Academic read cache**
   - Added `AcademicPageReadCache`, a per-key, 20-second in-memory cache for subject units, study resources, semester papers, subject PYQs and topic-specific PYQs.
   - Empty query results are deliberately not cached, so a temporary database failure cannot make a page appear empty for the full cache lifetime.
   - The cache is cleared after explicit Drive sync and resource uploads, so updates show immediately.

3. **Academic database indexes**
   - Added safe best-effort composite indexes matching resource lookups and PYQ ordering/filtering. Existing databases receive the indexes during startup; already-existing indexes are harmlessly ignored.

4. **Cheaper academic overview calculations**
   - Indexed questions by normalized topic once per subject instead of repeatedly scanning all questions for every topic and unit.

5. **Faster repeated Drive sync**
   - If an indexed Drive resource has a local copy, the stored subject/type/filename match the Drive listing, and its local byte size matches Drive's reported size, sync reuses that copy instead of downloading and re-processing it on every sync click.
   - Missing, mismatched or size-changed files still follow the download/reconcile path.

## Verification notes

- Reviewed the modified Java source structure and checked balanced braces/parentheses in the changed files.
- A full Maven package/test could not be run in this environment because the Maven wrapper distribution is not present locally and Maven Central could not be reached. Run `./mvnw -DskipTests package` in the project environment after extracting this archive, then launch and test `/academic`, a subject page, a unit page, and manual Drive sync.
- The cache improves repeat navigation, but end-to-end latency still depends on the configured MySQL instance, Google Drive API/network, and available machine resources.
