# Drawable Resources Review Backlog

## Open Issues

### [warning] Launcher icon pathData duplication across foreground/monochrome variants
**Files:** src/main/res/drawable/ic_launcher_foreground.xml (lines 12-26), src/main/res/drawable/ic_launcher_monochrome.xml (lines 11-25)

**What the smell is:**
The four vector paths (background rectangle, top bar, side handles, checkmark) have identical pathData in both files, but use different color strategies. Already documented with maintenance warnings, but the duplication creates a volatile coupling.

**Why it will cause problems:**
- When updating the icon design, developers must remember to edit BOTH files
- If one file is updated and the other forgotten, they will silently diverge
- The visual inconsistency may not be caught in testing because both variants render independently
- Over time, if the icon design evolves, the files will likely drift apart without explicit governance

**Concrete fix suggestion:**
XML format doesn't support constants/includes for pathData. Realistic options:
1. Add build-time validation script that compares pathData between the two files (external to drawable scope)
2. Create a custom icon asset processor (over-engineering for current scope)
3. Extract to a dedicated icon SVG file and process with build tools (significant refactoring)
4. Keep current warnings but add stricter enforcement (e.g., CI check, code review requirement)

Current status: Warnings are present and explicit. This is a format limitation of Android XML resources. Defer unless icon design changes become frequent.
