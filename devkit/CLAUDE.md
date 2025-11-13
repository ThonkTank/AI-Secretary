# devkit/ - Development Tools and Infrastructure

**Purpose:** Development toolkit providing templates, testing infrastructure, build tools, and utilities.

**Status:** Setup in progress (Phase 4.5.1+)

---

## Directory Structure

```
devkit/
├── testing/                   # Test infrastructure (domain, data, integration, fixtures)
├── templates/                 # Reusable documentation and code templates
├── build/                     # Build scripts and utilities (future)
├── utilities/                 # Development utilities (future)
└── CLAUDE.md                  # This file
```

---

## Purpose

This directory contains **development infrastructure** that is not part of the application code but supports the development process.

**Key Goals:**
- Enable testing (70%+ coverage target)
- Standardize documentation (templates)
- Streamline builds (scripts)
- Provide utilities (code generators, validators)

---

## Subdirectories

### testing/

Test infrastructure for all layers (domain, data, presentation, integration).

**See:** [devkit/testing/CLAUDE.md](testing/CLAUDE.md)

**Contents:**
- Unit test structure for domain layer
- Integration test structure for data layer
- End-to-end test scenarios
- Test fixtures and mock data

**Status:** Structure created (Phase 4.5.1), tests will be added in Phase 4.5.4-4.5.6

---

### templates/

Reusable templates for consistency across the project.

**See:** [devkit/templates/CLAUDE.md](templates/CLAUDE.md)

**Contents:**
- `CLAUDE_TEMPLATE.md` - Template for per-directory CLAUDE.md files

**Future templates:**
- `UseCase_TEMPLATE.java` - Use Case boilerplate
- `Repository_TEMPLATE.kt` - Repository pattern template
- `ViewModel_TEMPLATE.kt` - ViewModel boilerplate

---

### build/ (Future)

Build scripts and utilities for local and CI builds.

**Planned contents:**
- Enhanced build.sh with dependency support
- APK signing scripts
- Version bumping utilities
- GitHub Actions workflow helpers

**Status:** Not yet created

---

### utilities/ (Future)

Development utilities and tools.

**Planned contents:**
- Code generators (Use Case generator, etc.)
- Documentation validators (check CLAUDE.md compliance)
- Database migration scripts
- Performance profiling tools

**Status:** Not yet created

---

## Usage

### Using Templates

**Per-Directory CLAUDE.md:**
```bash
# Copy template to new directory
cp devkit/templates/CLAUDE_TEMPLATE.md features/new-feature/CLAUDE.md

# Edit and fill in placeholders
vim features/new-feature/CLAUDE.md
```

**See:** [devkit/templates/CLAUDE_TEMPLATE.md](templates/CLAUDE_TEMPLATE.md) for full template.

---

### Running Tests (Future - Phase 4.5.4+)

```bash
# Run all tests on GitHub Actions
git push origin refactoring/phase-4.5-architecture
gh run watch

# View test results
gh run view --log
```

**See:** [devkit/testing/CLAUDE.md](testing/CLAUDE.md) for testing strategy.

---

## Standards

**Documentation:**
- All tools MUST have documentation in their directory
- Scripts MUST have proper headers (see ~/CLAUDE.md "Script Documentation")

**Testing:**
- Test structure matches source structure (domain/, data/, presentation/)
- 70%+ coverage for domain layer (MANDATORY)
- 50%+ coverage for data layer

**Templates:**
- Templates MUST be kept up-to-date with standards
- All placeholders clearly marked with [DESCRIPTION]

---

## Related Documentation

- [Project CLAUDE.md](../CLAUDE.md) - Development standards
- [devkit/testing/CLAUDE.md](testing/CLAUDE.md) - Testing infrastructure
- [devkit/templates/CLAUDE_TEMPLATE.md](templates/CLAUDE_TEMPLATE.md) - CLAUDE.md template
- [ROADMAP.md](../ROADMAP.md) - Phase 4.5 plan

---

**Last Updated:** 2025-11-13
**Status:** In development (Phase 4.5.1+)
**Maintainer:** AI Secretary Development Team
