---

description: "Task list for Joshua project implementation"
---

# Tasks: Project Specification

**Input**: Design documents from `/specs/001-project-spec/`
**Prerequisites**: plan.md (required), spec.md (required), data-model.md, contracts/

**Tests**: Included as per plan requirements.

**Organization**: Tasks are grouped by milestones to enable incremental delivery.

## Format: `[ID] [P?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- Include exact file paths in descriptions
- Each task includes: work description, complexity (S/M/L) with time estimate, dependencies, acceptance criteria

<!-- 
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.
  
  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/
  
  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment
  
  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Milestone 1: Project Scaffolding (Modernising)

**Purpose**: Update existing project to match clean architecture and modern dependencies

- [ ] T001 Update app/build.gradle.kts with Hilt, Room, and testing dependencies. Complexity: S (1h). Dependencies: None. Acceptance: Gradle syncs successfully, no conflicts.
- [ ] T002 Create package structure: data/, domain/, presentation/ in app/src/main/kotlin/me/xizzhu/. Complexity: S (30m). Dependencies: T001. Acceptance: Directories created, existing code moved if needed.
- [ ] T003 Add Hilt DI modules for data and domain layers in app/src/main/kotlin/me/xizzhu/di/. Complexity: M (2h). Dependencies: T002. Acceptance: @Module classes compile, can inject basic dependencies.
- [ ] T004 Configure Room database with entities in app/src/main/kotlin/me/xizzhu/data/entities/. Complexity: M (3h). Dependencies: T001. Acceptance: Database schema matches data-model.md, migrations defined.
- [ ] T005 Update AndroidManifest.xml for storage and network permissions. Complexity: S (1h). Dependencies: None. Acceptance: App requests permissions appropriately.

## Milestone 2: Core Library

**Purpose**: Implement core business logic and data access

- [ ] T006 Implement data entities (Translation, Book, Verse) with Room annotations. Complexity: M (4h). Dependencies: T004. Acceptance: Entities compile, match data-model.md schema.
- [ ] T007 Implement DAOs for Bible data access in app/src/main/kotlin/me/xizzhu/data/dao/. Complexity: M (3h). Dependencies: T006. Acceptance: CRUD operations work with test data.
- [ ] T008 Implement repositories (BibleRepository, BookmarkRepository) with suspend functions. Complexity: M (4h). Dependencies: T007, T003. Acceptance: Interfaces match contracts/, can be injected.
- [ ] T009 Implement domain use cases (ReadBibleUseCase, SearchVersesUseCase) in domain/usecase/. Complexity: M (4h). Dependencies: T008. Acceptance: Use cases handle success/error cases.
- [ ] T010 Implement ViewModels (ReadingViewModel, SearchViewModel) with StateFlow. Complexity: M (4h). Dependencies: T009. Acceptance: UI state updates correctly on data changes.

## Milestone 3: Testing & CI

**Purpose**: Add comprehensive testing and automated CI/CD

- [ ] T011 Add unit tests for repositories with Mockito mocking. Complexity: M (3h). Dependencies: T008. Acceptance: Tests cover happy path and error cases, >80% coverage.
- [ ] T012 Add unit tests for use cases with contract validation. Complexity: M (3h). Dependencies: T009. Acceptance: Tests validate input/output contracts.
- [ ] T013 Add integration tests for full reading flow with Espresso. Complexity: L (6h). Dependencies: T010. Acceptance: Tests run on emulator, validate UI interactions.
- [ ] T014 Setup GitHub Actions workflow for build, test, and lint. Complexity: M (2h). Dependencies: None. Acceptance: CI passes on PRs, reports coverage.
- [ ] T015 Add Detekt/Ktlint for code quality checks. Complexity: S (1h). Dependencies: T014. Acceptance: Linting rules match constitution, CI fails on violations.

## Milestone 4: Demo & Docs

**Purpose**: Create working demo and comprehensive documentation

- [ ] T016 Update README.md with setup instructions and features. Complexity: S (2h). Dependencies: None. Acceptance: README matches quickstart.md, includes screenshots.
- [ ] T017 Create debug APK for demo distribution. Complexity: S (1h). Dependencies: All previous. Acceptance: APK installs and runs basic features.
- [ ] T018 Add sample data (KJV excerpts) for demo purposes. Complexity: M (3h). Dependencies: T004. Acceptance: Demo shows realistic Bible content.
- [ ] T019 Update docs/CHANGELOG.md with new features. Complexity: S (1h). Dependencies: None. Acceptance: Changelog follows semantic versioning.
- [ ] T020 Add troubleshooting guide in docs/. Complexity: S (1h). Dependencies: None. Acceptance: Common issues documented with solutions.

## Dependencies Graph

```
T001 → T002 → T003
T001 → T004 → T006 → T007 → T008 → T009 → T010
T003 → T008
T008 → T011
T009 → T012
T010 → T013
T014 → T015
All → T017
```

## Parallel Execution Examples

**Milestone 1**: T001, T005 can run in parallel
**Milestone 2**: T006, T008 (after T007)
**Milestone 3**: T011, T012, T014 in parallel
**Milestone 4**: T016, T019, T020 in parallel

## MVP Scope

Complete Milestone 1 + core of Milestone 2 (T006-T010) for basic offline reading (US1)
