## Summary

<!-- 1–3 bullet points describing the change -->
-
-
-

## Type of change

<!-- Mark the relevant option(s) with [x] -->
- [ ] 🐛 Bug fix (non-breaking; fixes a specific issue)
- [ ] ✨ New feature (non-breaking; adds functionality)
- [ ] 💥 Breaking change (changes existing behaviour; requires migration)
- [ ] 🔒 Security fix / hardening
- [ ] ♻️ Refactoring (no functional change)
- [ ] 📈 Performance improvement
- [ ] 🔧 CI/CD / build system change
- [ ] 📝 Documentation only

## Related issues / tickets

<!-- Link issues this PR closes. Use "Closes #123" for automatic closure. -->
Closes #

## Testing

<!-- Describe how you tested the change -->
- [ ] Unit tests added / updated
- [ ] Integration tests pass (`mvn test -Dgroups=integration`)
- [ ] Frontend tested locally in Chrome / Firefox
- [ ] Security change: threat model reviewed and test cases cover adversarial input

## Domain impact (for financial logic changes)

<!-- Required for any change touching domain/, application/, or database migrations -->

| Question | Answer |
|----------|--------|
| Does this change the allocation algorithm? | <!-- Yes / No --> |
| Does this change monetary precision or rounding? | <!-- Yes / No --> |
| Does this affect the audit log schema or entries? | <!-- Yes / No --> |
| Is there a data migration needed for existing records? | <!-- Yes / No --> |

## Security checklist

<!-- Required for all PRs; skip items that are clearly not applicable -->
- [ ] No secrets, credentials, or PII added to the codebase
- [ ] Input validation is present for all new user-supplied fields
- [ ] No new `nativeQuery` with string concatenation (SQL injection risk)
- [ ] New API endpoints have appropriate RBAC (`hasAnyRole()`)
- [ ] OWASP Dependency Check passes if new dependencies added
- [ ] Error messages do not expose stack traces or internal paths

## Database migrations

<!-- Required if db/migration/ files are changed -->
- [ ] Migration is idempotent (safe to re-run)
- [ ] Rollback strategy documented in the migration file header
- [ ] `flyway:validate` tested against the migration
- [ ] No `DROP` or `TRUNCATE` without a data backup plan

## CI gate

The following checks must be green before this PR can merge:
- CI Gate (required)
- CodeQL Security Analysis
- Dependency Review (no new HIGH/CRITICAL CVEs)

## Screenshot / demo (optional)

<!-- Paste a screenshot or a link to a screen recording for UI changes -->
