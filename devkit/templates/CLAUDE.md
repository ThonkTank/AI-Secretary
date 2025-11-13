# devkit/templates/ - Reusable Project Templates

**Purpose:** Centralized templates for consistent documentation and code structure.

**Status:** Active (Phase 4.5.1+)

---

## Directory Structure

```
devkit/templates/
├── CLAUDE_TEMPLATE.md          # Template for per-directory CLAUDE.md files
└── CLAUDE.md                   # This file
```

**Future templates (planned):**
- `UseCase_TEMPLATE.java` - Use Case boilerplate (Phase 4.5.4+)
- `Repository_TEMPLATE.kt` - Repository pattern template (Phase 4.5.3+)
- `ViewModel_TEMPLATE.kt` - ViewModel boilerplate (Phase 4.5.5+)

---

## Purpose

This directory provides **reusable templates** to maintain consistency across the project. Templates reduce duplication, enforce standards, and speed up development.

**Key Goals:**
- Standardize documentation structure (CLAUDE.md files)
- Provide code boilerplate for common patterns
- Ensure compliance with architecture standards
- Reduce copy-paste errors

---

## Available Templates

### CLAUDE_TEMPLATE.md

**Purpose:** Template for per-directory CLAUDE.md files

**When to use:**
- Creating documentation for new directories
- Documenting existing code directories
- Ensuring compliance with documentation standards

**Usage:**
```bash
# Copy template to new directory
cp devkit/templates/CLAUDE_TEMPLATE.md features/new-feature/CLAUDE.md

# Edit and fill in placeholders
vim features/new-feature/CLAUDE.md

# Replace all [PLACEHOLDERS] with actual content:
# - [DIRECTORY_NAME]
# - [PURPOSE_DESCRIPTION]
# - [STATUS_DESCRIPTION]
# - etc.
```

**Template Structure:**
- Directory structure overview
- Purpose and status
- Key workflows with code references
- Important conventions and patterns
- Testing approach
- Related documentation links

**See:** [CLAUDE_TEMPLATE.md](./CLAUDE_TEMPLATE.md) for full template content

---

## Template Standards

**All templates MUST:**
- Include clear placeholders marked with [BRACKETS]
- Provide inline documentation explaining each section
- Include real examples where helpful
- Be kept up-to-date with current standards
- Reference relevant documentation

**Documentation templates:**
- Max 40k characters per CLAUDE.md (enforced)
- Must include: Structure, Purpose, Workflows, Code References, Related Docs
- Use code references format: `file_path:line_number`

**Code templates (future):**
- Follow Single Responsibility Principle (max 200 lines)
- Include proper logging statements
- Have clear comments explaining each section
- Provide example usage in header comment

---

## Usage Patterns

### Creating Directory Documentation

**Scenario:** New feature directory needs CLAUDE.md

```bash
# 1. Copy template
cd ~/AI-Secretary-latest
cp devkit/templates/CLAUDE_TEMPLATE.md features/notifications/CLAUDE.md

# 2. Edit with placeholders replaced
vim features/notifications/CLAUDE.md

# 3. Verify compliance
# - Structure matches template
# - All placeholders replaced
# - Under 40k characters
# - Code references accurate
```

### Creating Use Case (Future)

**Scenario:** Need new Use Case following Clean Architecture

```bash
# 1. Copy template (when available)
cp devkit/templates/UseCase_TEMPLATE.java \
   features/tasks/domain/usecases/CompleteTaskUseCase.java

# 2. Edit with specific logic
vim features/tasks/domain/usecases/CompleteTaskUseCase.java

# 3. Replace placeholders:
# - CLASS_NAME
# - INPUT_PARAMS
# - RETURN_TYPE
# - BUSINESS_LOGIC
```

---

## Maintenance

**When to update templates:**
- Standards change (architecture, documentation)
- New patterns emerge in codebase
- Feedback from template usage
- Phase milestones (e.g., Phase 4.5 completion)

**Update process:**
1. Identify need (outdated template, missing pattern)
2. Update template with improvements
3. Document changes in template header
4. Update Last Updated timestamp
5. Notify team (if applicable)

**Template versioning:**
- Templates are versioned with project (not separately)
- Major changes noted in ROADMAP.md
- Breaking changes require migration guide

---

## Quality Checklist

**Before adding new template:**
- [ ] Clear purpose and use case documented
- [ ] All placeholders clearly marked
- [ ] Real example provided (if applicable)
- [ ] Inline documentation explains each section
- [ ] Follows current project standards
- [ ] Tested by creating real file from template
- [ ] Referenced in relevant documentation

**Periodic review (monthly or per phase):**
- [ ] All templates still follow current standards
- [ ] No outdated patterns or anti-patterns
- [ ] Placeholders are clear and discoverable
- [ ] Examples are accurate and helpful
- [ ] Referenced documentation links work

---

## Related Documentation

- [Project CLAUDE.md](../../CLAUDE.md) - Documentation standards
- [devkit/CLAUDE.md](../CLAUDE.md) - Devkit overview
- [~/CLAUDE.md](../../../CLAUDE.md) - Home directory guide
- [CLAUDE_TEMPLATE.md](./CLAUDE_TEMPLATE.md) - Full template content

**Template usage examples:**
- [src/com/secretary/CLAUDE.md](../../src/com/secretary/CLAUDE.md) - Created from template
- [docs/CLAUDE.md](../../docs/CLAUDE.md) - Created from template
- [devkit/CLAUDE.md](../CLAUDE.md) - Created from template

---

**Last Updated:** 2025-11-13
**Status:** Active - Documentation templates available, code templates planned
**Maintainer:** AI Secretary Development Team
