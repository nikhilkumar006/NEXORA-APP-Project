# Targeted manual verification checklist

Run on the same local MySQL database and SMTP settings that you will use for review. Keep NEXORA_DEV_ACCESS=false to test real authentication.

## Career Guidance / mapping
- [ ] Open `/career` and confirm role families, search, career cards, O*NET selector and directory sections render.
- [ ] Open one role detail from the card. Confirm `/career/role/{slug}` resolves instead of redirecting to the directory.
- [ ] Click **Use in Career Selection**. Confirm the selected role is pre-filled and path is unchanged until you choose it.
- [ ] Test family, company, location and skill links. Valid slugs should display the relevant view; invalid slugs should return to `/career`.
- [ ] Save a role on Career Guidance, then open Career Selection, Development GPS and Recommended Courses. Verify the same canonical role appears across the pages.
- [ ] Change to another role and verify a previously selected company/programme does not carry into the new role unless still valid.

## Career Selection
- [ ] On-campus path: target role and package band are enabled; company/programme controls are hidden/disabled; save returns to `/career-selection?saved=true`.
- [ ] Off-campus path: role and company are required; programme choices load for the selected company/role; “Not specified” remains usable when no verified programme is available.
- [ ] Change company or role rapidly; a stale asynchronous response must not replace the latest options.
- [ ] Invalid or missing role/company/programme should produce a visible error redirect, not a generic 500.

## Development GPS
- [ ] Confirm a saved role appears even for a record that has Career Guidance Profile but no new Career Selection record.
- [ ] Confirm recommendations use the mapped role's skills rather than only generic foundation skills.
- [ ] Add a project and experience, return to GPS, and confirm values/progress refresh.
- [ ] Sync GitHub/Codeforces evidence and reload GPS; values should refresh after successful sync.
- [ ] Navigate away/back quickly and confirm the page responds faster.
- [ ] Confirm no runtime type-cast error appears while calculating milestone progress.

## Recommended Courses
- [ ] Save a target role, open `/recommended-courses`, and verify role-matched recommendations appear.
- [ ] If on Free, either approve/activate Premium or use `NEXORA_PREMIUM_REVIEW_ACCESS_ENABLED=true` in an isolated local review. Do not enable this in production.
- [ ] Search by course title, provider, skill and description. Verify no-match and clear states.
- [ ] Mark an item Saved, In Progress, and Completed; refresh and verify persistence. Completed items should remain visible and be searchable.
- [ ] Search first, change status, and verify redirect preserves the query. Progress percentage should not change just because a filter is applied.

## Academic Development
- [ ] Open `/academic`. Check that it renders without loading every subject's units/questions on initial page navigation.
- [ ] Open a subject, then one unit/resource/question-bank route. Verify it loads only data relevant to the selected subject/page.
- [ ] Use explicit sync only from the intended form. Verify it clears the relevant cache and subsequent read reflects refreshed data.
- [ ] Test with Drive configured and without Drive configured; the latter should show a useful fallback/error state rather than an endless spinner.

## Status / security
- [ ] Test session redirect after logging out and retrying a protected URL.
- [ ] Confirm forms contain CSRF tokens and protected controllers require an authenticated account.
- [ ] Check server logs for SQL/schema errors and browser console/network failures.
