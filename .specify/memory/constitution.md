<!--
Version change: new → 1.0.0
List of modified principles: All principles added (High Code Quality, Modularity, Simple and Predictable UX, Minimal Dependencies, Backward Compatibility)
Added sections: None
Removed sections: None
Templates requiring updates: plan-template.md (Constitution Check section updated) / ✅ updated
Follow-up TODOs: None
-->
# Joshua Constitution

## Core Principles

### I. High Code Quality
All code must prioritize readability, maintainability, and consistent style. Adhere to the relevant coding standards, use meaningful variable and function names, and ensure comprehensive unit and integration tests cover all public APIs. Code reviews must verify test coverage and style compliance.

### II. Modularity
Implement clear separation of concerns with well-defined interfaces for constructor injection. Use dependency injection frameworks to manage dependencies, promoting testability and flexibility. Avoid tight coupling between components.

### III. Simple and Predictable UX
Design a simple, intuitive user interface with predictable behavior. Include help text and tooltips for all user-facing features. Ensure consistency in navigation, gestures, and visual elements across the app.

### IV. Minimal Runtime Dependencies
Favor actively maintained libraries with few transitive dependencies. Regularly audit dependencies for security and maintenance status. Prefer standard libraries over third-party solutions unless they provide significant value.

### V. Backward Compatibility
Maintain backward compatibility using semantic versioning (MAJOR.MINOR.PATCH). Major versions for breaking changes, minor for new features, patch for bug fixes. Ensure data migration paths for schema changes.

## Governance
This constitution supersedes all other practices. Amendments require pull request with rationale, approval from maintainers, and migration plan if needed. All PRs must verify compliance with these principles.

**Version**: 1.0.0 | **Ratified**: 2026-01-18 | **Last Amended**: 2026-01-18
