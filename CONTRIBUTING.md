# Contributing to JSON Finder

Thanks for taking the time to contribute!

## Getting started

1. Fork the repository and clone it locally
2. Open the project in IntelliJ IDEA
3. Run `./gradlew test` to verify everything builds and tests pass

## Development

**Run the plugin in a sandbox IDE:**

```bash
./gradlew runIde
```

**Run tests:**

```bash
./gradlew test
```

**Run static analysis:**

```bash
./gradlew runInspections
```

## Submitting changes

1. Create a branch from `main`: `git checkout -b my-feature`
2. Make your changes — all new code must be covered by tests
3. Ensure `./gradlew test` passes with no failures
4. Open a pull request against `main` with a clear description of what and why

## Reporting bugs

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md) and include your IDE version and plugin version.

## Code style

- Kotlin, following the project's existing conventions
- No comments explaining *what* the code does — only *why* when non-obvious
- Tests live next to the source they cover under `src/test/`
