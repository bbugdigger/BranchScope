<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Branch Scope logo" width="120" />
</p>

<h1 align="center">Branch Scope</h1>

<p align="center"><em>Scope the IntelliJ project view to files modified in your current branch.</em></p>

<!-- Marketplace badges (uncomment after the plugin is published)
<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/branch-scope">
    <img src="https://img.shields.io/jetbrains/plugin/v/branch-scope?label=JetBrains%20Marketplace&color=2BA1E2&logo=jetbrains" alt="JetBrains Marketplace" />
  </a>
  <a href="https://plugins.jetbrains.com/plugin/branch-scope">
    <img src="https://img.shields.io/jetbrains/plugin/d/branch-scope?label=downloads&color=2BA1E2" alt="Downloads" />
  </a>
</p>
-->

Open the Project view selector and pick **Branch Modified Files** to focus the tree on exactly the files that make up your work-in-progress branch — commits on top of the base branch plus uncommitted edits and new untracked files. Treat it as the project view of your pull request.

## Features

- **"Branch Modified Files" entry** in the Project view selector dropdown, alongside _Project_, _Production_, _Tests_, _Open Files_ and _All Changed Files_. Powered by a custom `NamedScope`.
- **Smart base-branch detection.** The diff base is the merge-base of `HEAD` with `origin/HEAD` → `main` → `master` → `develop`, in that order. Override per project from settings if your team uses a different base.
- **Committed + uncommitted + untracked.** Files that you committed on this branch, files staged or modified in the working tree, and untracked-but-not-ignored files all appear in the same view.
- **VCS status colors everywhere.** A `ProjectViewNodeDecorator` paints branch-modified files with IntelliJ's standard green/blue file-status colors in _every_ project view pane, so you can spot them at a glance even from the regular _Project_ view.
- **Auto-refresh.** The view recomputes on Git repository state changes (commits, checkouts, fetches) and on working-tree updates from `ChangeListManager`. Updates are debounced.
- **Public-API only.** No `com.intellij.*.impl.*` types and no `@ApiStatus.Internal` usage — the plugin should remain compatible with future IntelliJ versions without binary breakage.

## Install

Requires IntelliJ Platform 2026.1 or newer. The plugin depends on the bundled **Git** integration (`Git4Idea`), which ships with all IntelliJ-based IDEs.

**From disk** (current local-build flow):

1. Build the plugin ZIP:
   ```bash
   ./gradlew buildPlugin
   ```
   On Windows: `./gradlew.bat buildPlugin`. The artifact lands at `build/distributions/BranchScope-1.0.0-SNAPSHOT.zip`.
2. In IntelliJ IDEA: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick the ZIP.

A JetBrains Marketplace listing is in preparation.

## Usage

1. Open a Git project and check out a feature branch with at least one change vs. `main` / `master` / `develop`.
2. In the Project tool window, click the pane selector (the one that defaults to _Project_) and choose **Branch Modified Files**.
3. The tree collapses to just the files in your branch's diff. Status colors mark them as added / modified / etc.

The view updates automatically as you commit, switch branches, edit, or run `git fetch`.

## Settings

Under **Settings → Tools → Branch Scope**:

- **Base branch** — `auto` for automatic detection (recommended), or a specific ref name like `develop`, `release/v3`, or `origin/staging`.
- **Include uncommitted working-tree changes** — on by default. Turn off to see only files that are part of your branch's commit history.

## How it works

On project open and on every relevant VCS event, a project-level service runs:

```text
mergeBase = git merge-base HEAD <baseBranch>
fileSet   = git diff --name-status -M <mergeBase>          # working tree vs base, when "include uncommitted" is on
        ∪   ChangeListManager.unversionedFilesPaths        # untracked files
        OR
fileSet   = git diff --name-status -M <mergeBase>..HEAD    # committed-only, when "include uncommitted" is off
```

The result is published as a snapshot consumed by:

- **`BranchScopePackageSet`** — the `PackageSetBase` whose `contains(...)` is queried by IntelliJ's project-view scope filter.
- **`BranchScopeNodeDecorator`** — the `ProjectViewNodeDecorator` that colors branch-modified files in every pane.

After each recompute, the service fires `NamedScopeManager.fireScopeListeners()` and `ProjectView.refresh()` so cached scope membership is invalidated.

## Build from source

Requires JDK 21 (matches the JBR shipped with 2026.1).

```bash
./gradlew clean test            # unit + IntelliJ-fixture tests
./gradlew runIde                # spin up a sandbox IDE with the plugin
./gradlew buildPlugin           # produce the marketplace ZIP
./gradlew verifyPlugin          # run the plugin verifier
```

The IDE version targeted by `runIde` is set in `build.gradle.kts` (`intellijIdea("2026.1.1")`).

## Limitations / known issues

- The scope can only filter files that exist on disk, so files **deleted** in the branch don't appear in the tree. Their absence from the view is by design of `NamedScope`.
- On very large repos with thousands of modified files, the first `git diff` may take a beat to populate; the project view stays empty until that finishes.

## License

[MIT](LICENSE).

