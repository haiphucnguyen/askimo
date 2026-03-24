# Git Hooks

## Pre-commit Hook

The pre-commit hook automatically runs `./gradlew spotlessApply` before each commit to ensure code is properly formatted.

### Installation

Run the installation script:

```bash
./tools/git/pre-commit
```

This will:
1. Create the pre-commit hook in `.git/hooks/pre-commit`
2. Make it executable
3. Configure it to run spotlessApply before each commit

### What It Does

When you run `git commit`, the hook will:
1. Run `./gradlew spotlessApply` to format all code
2. Re-add any files that were changed by Spotless
3. Continue with the commit

### Manual Installation

If you need to install it manually:

```bash
chmod +x tools/git/pre-commit
./tools/git/pre-commit
```

### Disabling the Hook

If you need to temporarily skip the hook:

```bash
git commit --no-verify
```

### Uninstalling

To remove the hook:

```bash
rm .git/hooks/pre-commit
```

## CI Safety Net (GitHub Actions)

Even if the local hook is not installed, every push and pull request runs
`spotlessCheck` via GitHub Actions (`.github/workflows/ci.yml`).

- **PRs with formatting violations are blocked** — the `Format Check` job must
  pass before `Build & Test` runs.
- Fix locally by running `./gradlew spotlessApply`, then push again.
- The local pre-commit hook is the recommended way to avoid the round-trip, but
  the CI check ensures no unformatted code ever reaches `main`.

