# CI and release pipeline

Masroof uses GitHub Actions for continuous integration and automated slash-command-driven releases.

| Workflow | When | What |
|----------|------|------|
| **CI** (`ci.yml`) | Pull request or push → `main` | Unit tests, static analysis, and two device jobs: full production instrumentation; full Beta instrumentation followed by lifecycle and signed-update checks |
| **PR Slash Commands** (`pr-commands.yml`) | Comment on PR (`/release`, `/nightly`) | Authenticates commenter, checks PR merge status, dispatches Release |
| **Release** (`release.yml`) | Workflow dispatch or PR slash command | Verifies green CI on target commit, builds signed APK, tags commit, publishes GitHub Release |

---

## Device job scheduling

The existing two emulator jobs share the device workload. `emulator-smoke` runs the complete production instrumentation suite with its existing orchestrator/isolation. `emulator-lifecycle` runs the complete Beta native suite first, then the existing Beta permission/process-death, production process-death and signed-update journeys. Existing `notPackage` runner arguments keep lifecycle phases out of the complete native suites, so those phases still run once through their dedicated scripts.

Both jobs assemble their required APKs before starting the emulator, avoiding compilation competing with the emulator for runner CPU/memory. Both retain the latest-stable Android image and the Pixel 2 profile needed by unfolded-window tests. No suite is sampled, no timeout/assertion is relaxed, and release does not rerun the tests.

Production reports remain in `instrumentation-reports` and `m19-statement-screenshots`. Beta native reports/screenshots are in `beta-instrumentation-reports`; lifecycle diagnostics keep their existing artifact names. Screenshot collection runs even after test failure, and the wrapper preserves the original Gradle failure status. The fast `scripts/test-instrumentation-smoke.py` cases cover suite routing and failure handling.

Before this redistribution, a successful PR #300 run spent about 28 minutes in the combined device-smoke job versus 15 minutes in lifecycle; Beta native cases alone occupied over six minutes before production could start. The redistribution targets that serial wait. Actual savings depend on runner/cache speed and must be measured in the resulting GitHub run.

---

## Daily development workflow

1. **Feature branch**: Create branch and commit code changes. No manual version bumping in `app/build.gradle.kts`.
2. **Open PR**: Open a PR targeting `main`. **CI** runs tests and lint checks automatically.
3. **Review & Merge**: Review changes and merge the PR into `main`. **CI runs again on `main`** after merge.
4. **Publish**: Comment `/nightly` or `/release` on the merged PR. If main CI is still running, the release job waits for it. A failed check still blocks publishing.

---

## Publishing builds after merge

After merging a PR, wait for **main** CI checks (`unit-test` and `static-analysis`) to succeed, then comment on the merged PR to publish. Release builds **do not re-run** the full test suite; they verify the target commit already has green CI, then build and sign the APK.

### Want a test build?
Comment:
```text
/nightly
```
- Builds pre-release APK tagged `v<next-version>-nightly.<n>`.
- Monotonically increments global `versionCode`.
- Does not notify regular stable users.

### Ready for stable?
Comment:
```text
/release
```
- Or `/release patch` (e.g. `v0.3.17` $\rightarrow$ `v0.3.18`).
- For minor bumps: `/release minor` (e.g. `v0.3.17` $\rightarrow$ `v0.4.0`).
- For major bumps: `/release major` (e.g. `v0.3.17` $\rightarrow$ `v1.0.0`).
- Publishes official release with signed APK and `version.json`.
- Notifies users via in-app update checker.

---

## Safety and concurrency

- **Zero-code bumps**: Version names and version codes are dynamically resolved and passed via Gradle CLI (`-PappVersionName` and `-PappVersionCode`). No version commits are pushed back to the repo.
- **Unmerged PR protection**: `/release` and `/nightly` commands on open/unmerged PRs are rejected.
- **CI gate before publish**: Release verifies the target commit has successful `unit-test` and `static-analysis` checks from the **CI** workflow (within 7 days). If those checks are still running, publish waits for them. If a check failed, publish is blocked.
- **Serialized publishing**: All publishing jobs run under the `masroof-publish` concurrency group with `cancel-in-progress: false` to ensure no two concurrent builds can generate conflicting versions or version codes.
- **Tagging safety**: Git tags are created and pushed only **after** the APK is signed and verification succeeds.
- **Authorization**: Only repository owners, members, and write collaborators can trigger release workflows.
- **Emergency manual dispatch**: Workflow dispatch can set `skip_ci_check` to bypass the CI gate (use only when necessary).
