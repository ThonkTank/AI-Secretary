# docs/ - Technical Documentation Hub

**Purpose:** Central repository for all deep-dive technical documentation.

**Organization:** System documentation (logging, updates) and process documentation (refactoring, testing).

---

## Directory Structure

```
docs/
├── LOGGING_SYSTEM.md          # HTTP logging system implementation
├── UPDATE_SYSTEM.md           # Auto-update via GitHub Releases
├── REFACTORING_BASELINE.md    # System behavior baseline for regression testing
└── CLAUDE.md                  # This file
```

**Total:** 3 technical documents

---

## Purpose

This directory contains **detailed technical documentation** that is too extensive for CLAUDE.md files in code directories.

**What belongs here:**
- System architecture deep-dives
- Feature implementation details
- Algorithm explanations
- Design decisions and rationale
- Migration guides
- Process documentation

**What does NOT belong here:**
- Per-directory code documentation (use directory CLAUDE.md)
- Project management (use ROADMAP.md)
- User-facing documentation (use README.md)

---

## Document Categories

### System Documentation

**LOGGING_SYSTEM.md** - HTTP Logging Implementation
- AppLogger.java and SimpleHttpServer.java architecture
- In-memory circular buffer design
- HTTP endpoints and usage
- Integration with Termux/Claude Code

**UPDATE_SYSTEM.md** - Auto-Update Mechanism
- UpdateChecker.java and UpdateInstaller.java architecture
- GitHub Releases API integration
- APK download and installation flow
- Version comparison logic

### Process Documentation

**REFACTORING_BASELINE.md** - Phase 4.5 Baseline
- System behavior before refactoring
- 10 critical user flows
- Database schema documentation
- Regression test scenarios

---

## Documentation Standards

All docs in this directory MUST follow the template in ~/CLAUDE.md:

**Required Fields:**
- Created, Last Updated, Status
- Overview (2-3 sentences)
- Problem Statement (what problem does this solve?)
- Solution Design (how does it work?)
- Implementation Details (components with code references)
- Usage Examples (runnable code/commands)
- Testing (how to verify it works)
- Related Documentation (links to other docs)

**Template:** See ~/CLAUDE.md "Documentation Standards → Deep Documentation in docs/"

---

## Naming Conventions

- **Systems:** `SYSTEM_NAME_SYSTEM.md` (e.g., LOGGING_SYSTEM.md)
- **Features:** `FEATURE_NAME.md` (e.g., TASK_COMPLETION_FLOW.md when created)
- **Processes:** `PROCESS_NAME.md` (e.g., REFACTORING_BASELINE.md)
- **Always UPPERCASE with underscores**

---

## Future Documentation (Planned)

As Phase 4.5 progresses, these docs will be created:

- `ARCHITECTURE.md` - Overall system architecture post-refactoring
- `TASK_COMPLETION_FLOW.md` - Task completion Use Case flow
- `RECURRENCE_SYSTEM.md` - INTERVAL and FREQUENCY logic deep-dive
- `DATABASE_SCHEMA.md` - Room migration and schema guide
- `TESTING_STRATEGY.md` - Comprehensive testing approach
- `MVVM_IMPLEMENTATION.md` - ViewModel and LiveData patterns

---

## Quick Reference

| Topic | Document | Key Sections |
|-------|----------|--------------|
| How to access logs | LOGGING_SYSTEM.md | Usage → Curl commands |
| How updates work | UPDATE_SYSTEM.md | User Flow |
| System baseline | REFACTORING_BASELINE.md | Critical User Flows |

---

## Related Documentation

- [Project CLAUDE.md](../CLAUDE.md) - Development standards and workflow
- [ROADMAP.md](../ROADMAP.md) - Feature roadmap and phases
- [devkit/testing/CLAUDE.md](../devkit/testing/CLAUDE.md) - Testing infrastructure

---

**Last Updated:** 2025-11-13
**Maintainer:** AI Secretary Development Team
