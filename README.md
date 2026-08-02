# Devin Remediation Orchestrator

A Spring Boot service that keeps a fork of Apache Superset maintained. Label a
GitHub issue `devin-fix` and it starts a Devin session to fix it, tracks the
resulting pull request, and feeds CI failures back into that same session so the
agent can repair its own work.

## Why

Maintenance work on a fork (missing regression tests, deprecation warnings, CVE
bumps) never wins prioritisation against feature work, so it accumulates as
invisible risk. This service makes that work self-servicing, with a daily budget
cap, a dedupe gate, and an audit trail of every state change.

## Architecture

```text
      Devin Scout                         GitHub Issue
    (scheduled scan)                   (label: devin-fix)
           |                                   |
           | files issues                      | webhook
           +---------------+-------------------+
                           |
                           v
                Webhook Receiver  (HMAC verified)
                           |
                           v
             Policy Gate  (dedupe + daily budget)
                           |
                           v
                     Devin Session  <----- playbook + knowledge note
                           |     ^
                           v     | repair message
                     Pull Request |
                           |     |
                     CI check runs
                           |
                           v
                Reconciliation Loop
                           |
                           v
                    State Machine
                    /            \
           Status Comment    Dashboard
```

The service only touches GitHub metadata: comments, labels and check results.
Devin writes the code inside its own VMs. The orchestrator never creates a
commit, branch or pull request itself.

## The repair loop

When CI fails on a PR that Devin opened:

1. The `check_run` webhook arrives and is matched to the remediation tracking
   that PR.
2. State moves `PR_OPEN` to `CI_FAILED`, then to `REPAIR_DISPATCHED` if the
   attempt counter is still under its cap.
3. The tail of the CI log goes back into the same Devin session, with
   instructions to reproduce it, fix the root cause and push to the same branch.
   Skipping or xfailing a test to get green is explicitly forbidden.
4. The session resumes with its original working context and pushes a fix.
5. Green means merged. Red again means another attempt, then escalation to a
   human once the cap is reached.

A script can bump a version and a single model call can suggest a patch, but
neither can be handed a CI failure twenty minutes later and carry on from where
it left off. A Devin session can, because it is addressable and holds its own
state. That is what makes it worth orchestrating.

One thing the live API does differently from the docs: a session that has
finished and is waiting for review reports `status: running` with
`status_detail: waiting_for_user`, not `suspended`, and its pull requests come
back as `pr_url` entries. The reconciler treats waiting-for-user as finished.
Getting that wrong was the only bug that surfaced during live integration.

## Quick start

Simulate mode needs no credentials. Postgres is the only dependency.

```bash
make simulate        # SPRING_PROFILES_ACTIVE=simulate docker compose up --build
open http://localhost:8080
```

A replay client serves recorded Devin session timelines and a scenario driver
plays GitHub's part, so the four buttons on the dashboard walk the state machine
through a full run at a watchable pace. Repair loop is the one to click.

```bash
make test            # includes the Testcontainers integration test
```

With real credentials:

```bash
cp .env.example .env # Devin + GitHub values
make up
```

On startup the service calls `GET /v3/self` and fails fast if the Devin key is
missing or rejected. You also need a webhook on the fork pointing at
`https://<host>/webhooks/github` (JSON, secret `GITHUB_WEBHOOK_SECRET`, events:
Issues, Check runs, Pull requests), and the remediation playbook and knowledge
note created in the Devin UI, with their IDs in `.env`. `POST /admin/scout` runs
a scout session on demand; the scheduled one is a native Devin Automation, not
part of this service.

## Dashboard

Every state change is a row in `state_transitions`, and every number on the
dashboard is a query over that table. `GET /api/dashboard`, rendered at `/`:

- **repairLoopRecoveries**, merged PRs that failed CI first and were repaired in
  the same session. This is the number that shows whether the orchestration
  layer earned its keep.
- **successRate** and **firstPassCiSuccessRate**. The gap between them is the
  repair loop's contribution.
- **medianCycleTimeMinutes**, label to merge.
- **totalAcus** and **estimatedCostPerMergedPr**, the cost of a merged fix.
- **escalationsByReason**, where a human still has to step in. A session that
  hits its ACU cap appears here as `usage_limit_exceeded`, which is the
  guardrail working rather than a failure.

On this account the session API reports `acus_consumed: 0.0` for every session
and the organization consumption endpoint returns an empty ledger, while Devin's
billing console shows real amounts. The spend tiles therefore render as
unavailable instead of a misleading $0.00, and any cost quoted below comes from
the console. On a plan that reports ACUs they populate with no code change.

## Results

Real runs against
[`razachoudhary/devin-superset`](https://github.com/razachoudhary/devin-superset):

| Issue | Finding | Outcome | PR |
|---|---|---|---|
| [#9](https://github.com/razachoudhary/devin-superset/issues/9) | `modify_url_query` dropped repeated and blank query params (upstream fix `9617155`) | Merged, first pass, fail-on-revert verified | [#10](https://github.com/razachoudhary/devin-superset/pull/10) |
| [#12](https://github.com/razachoudhary/devin-superset/issues/12) | boxplot MINMAX FutureWarning fix (upstream `e075133a`) | PR open, gate green | [#13](https://github.com/razachoudhary/devin-superset/pull/13) |
| [#19](https://github.com/razachoudhary/devin-superset/issues/19) | leftover deprecated `datetime.utcnow()` calls, plus a conformance test | PR open, gate green | [#20](https://github.com/razachoudhary/devin-superset/pull/20) |
| #14, #15, #16 (filed by the scout) | APP_ICON prefixing, stale ant-modal selectors, export/delete/fav-star ids | PRs open | [#18](https://github.com/razachoudhary/devin-superset/pull/18), [#22](https://github.com/razachoudhary/devin-superset/pull/22), [#17](https://github.com/razachoudhary/devin-superset/pull/17) |
| #7 (manual pilot, before the orchestrator) | schema field-renaming regression test | PR open | [#8](https://github.com/razachoudhary/devin-superset/pull/8) |

A merged, CI-verified regression test costs about a dollar and lands in under
twenty minutes with nobody watching. One `POST /admin/scout` call produced three
findings, filed them as labelled issues, and three remediation sessions
dispatched themselves off the resulting webhooks.

### Running the real test suite: PR #24

Could the gate run Superset's own unit tests instead of the isolated
`devin_gate_tests/`? A manual Devin session, deliberately outside the
orchestrator (whose prompt forbids agents from editing CI workflows), produced
[PR #24](https://github.com/razachoudhary/devin-superset/pull/24): it reuses the
repository's `.github/actions/setup-backend/` action to install the full backend
and run `tests/unit_tests` for real. It works, but takes around 15 minutes per
pull request against roughly 20 seconds for the isolated gate, and the repair
loop needs a verdict on every attempt. Hence left open rather than merged.
Running this for real, it is the version to merge.

### What this does not prove

No repair message has gone through the live `messages` endpoint yet: every
orchestrated run passed the gate first time, which the narrow gate makes likely.
The repair logic (capped attempts, same-session messaging, escalation on
exhaustion) is covered by the simulate scenarios and the integration test, which
drive the same code.

That gate runs self-contained tests, since Superset's dependencies do not
install inside a Devin session, so it shows a new test discriminates between
pre-fix and post-fix behaviour rather than that it guards the live source. PR
#24 is the fix.

## Scope decisions

**Devin Review Auto-Fix.** Auto-Fix already responds to CI failures on its own,
and was deliberately not used. Here every repair passes the policy gate,
increments a capped counter, and is recorded as a state transition, so a
customer who needs automated changes to be traceable in their own system has
somewhere to hook in. Auto-Fix is internal to the agent and offers no such seam.

**Custom webhook receiver rather than a GitHub-triggered Devin Automation.**
Those only support private repositories, and this fork is public.

**Agents may not edit CI workflows.** The shortest path to a green check is to
weaken the check, so the remediation prompt forbids it, which is why PR #24 was
a manual session.

**No Prometheus or Micrometer.** The transitions table already answers
everything the dashboard asks at this scale.

**No Code Scans, Guardrail Violations or usage analytics.** Enterprise tier, not
available on this account.

## Next steps

1. Cache dependencies and select tests by impact so the deep gate from PR #24
   runs in a couple of minutes, and add a Blueprint so the agent can verify
   locally before pushing.
2. Multiple repositories behind one orchestrator, with per-repo budgets and
   playbooks.
3. Deprecations and CVE bumps as first-class finding types with their own
   playbooks.
4. Per-finding ACU budgets learned from history, and alerts on unusual spend.
5. Route Devin Review findings on the agent's own PRs back through the same
   governed repair loop.
