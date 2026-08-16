# Repository-owned Native pull-request finish

## Purpose

cap4k owns the semantic and deterministic policy for pull requests created by Comet Native Archive. Comet owns the generic finish transaction; the repository owns the reviewer context, template, capability-closure validation, create/reuse policy, and remote body verification.

## Finish authorization

- A user selection of `finish=pull-request` authorizes the current Native Agent to author the PR title and body from accepted change evidence.
- The Agent MUST NOT ask for a second title/body confirmation.
- Automatic authoring MUST occur only after Native Verify has accepted the current candidate and Archive permits pull-request finish.
- The authored content is untrusted until the repository validator accepts its structure, current diff, capability facts, and identity bindings.

## Authoring source and artifact

The Agent MUST derive PR reviewer context from the current tracked PR template and the following available evidence:

- active change name, branch, target branch, and accepted Native state;
- brief, complete target Specs, verification result, and acceptance outcomes;
- owning Issue hierarchy when one exists;
- current `origin/master...finalHead` diff and changed-path classification as PR evidence, without assuming the accepted pre-Archive commit alone contains all implementation changes;
- generated capability facts and propagation closure;
- actual checks run, skipped, failed, or blocked.

The repository-owned authoring artifact MUST:

- use a versioned schema;
- contain the exact title and body to be published;
- identify the change, base, and head;
- record both the accepted pre-Archive commit identity and `source.preArchiveTreeSha`, the complete Git tree snapshot identity of the accepted working tree from which it was authored; the Native Agent MUST create that tree through an isolated temporary Git index equivalent to reading `HEAD`, staging all working-tree changes, and writing the tree, without changing the real index, committing the snapshot, or adding it to product source;
- include a deterministic content fingerprint;
- remain local and reusable for an Archive retry without being committed as product source.

AI owns semantic synthesis such as Summary, Audit Focus, boundary explanations, risk, composition, and sibling responsibility. Deterministic scripts MUST NOT fabricate these narratives from fixed sentence templates.

## Repository provider contract

The configured provider MUST:

- accept only `comet.native.pull-request-finish-input.v1` on stdin;
- emit only one `comet.native.pull-request-finish-result.v1` JSON object on stdout when successful;
- write diagnostics only to stderr;
- validate schema, base, head, the accepted pre-Archive commit identity, `source.preArchiveTreeSha` as an existing Git tree object, path containment, and bounded input before remote mutation;
- require a current repository-owned authoring artifact and fail closed for missing, stale, ambiguous, placeholder-bearing, or fingerprint-mismatched content; missing, invalid, non-tree, or tampered snapshot identity; or any difference between the snapshot tree and the final Archive head tree outside Runtime-owned Archive progression such as active-change-to-archive movement, canonical Spec publication, and `.comet/current-change.json` updates;
- invoke the repository PR entry rather than duplicating its template or validation logic;
- return `remoteVerified: true` only after repository validation succeeds against the remote PR.

Comet remains responsible for commit, push, provider invocation, independent remote PR identity verification, recovery state, and worktree cleanup.

## Repository PR entry

`scripts/create-pr.ps1` is the single repository entry for manual and Native-created pull requests. It MUST:

- enforce `master` as base and a same-repository short-lived head;
- discover the tracked PR template case-insensitively;
- validate the authored body before remote mutation using `origin/master...finalHead` changed-files and capability-facts evidence available to CI, while separately comparing the accepted working-tree snapshot with the final Archive head tree so implementation already present in the snapshot is not misclassified as Archive-only progression;
- create exactly one PR when none exists;
- reuse a unique open PR only when its title, body, base, head, accepted pre-Archive commit identity, accepted tree snapshot identity, and expected remote identity remain consistent with the authoring artifact;
- fail closed rather than editing a drifted existing PR;
- fetch and revalidate the remote body after creation or reuse;
- provide a machine-readable result to the thin Comet adapter while preserving the supported manual dry-run entry.

## Reviewer context contract

The authored PR body MUST preserve the tracked template and provide non-placeholder evidence for:

- Summary and target/change type;
- Issue hierarchy and Acceptance IDs;
- Runtime, Generator, Analyzer, AgentFacts, Public Docs, and Skill impact;
- shared contracts and propagation closure;
- composition evidence and sibling-slice responsibility;
- reviewer Audit Focus;
- actual verification and any justified skips;
- Agent Review request state;
- release note.

The template is a structured context contract for human and AI reviewers. It is not a separate approval form.

## Failure and recovery

Before any remote mutation, the flow MUST fail if the authoring artifact or deterministic validation is invalid, including a missing, invalid, non-tree, or tampered snapshot or any post-snapshot non-Archive path change. Tests MUST cover the isolated-index snapshot without altering the real index, permitted Runtime Archive progression, and each fail-closed snapshot case. After a PR has been observed, any provider failure MUST preserve enough verified identity for safe retry without treating unverified provider claims as trusted output. A failed finish MUST preserve the PR and worktree and expose the Native recovery command. Retrying with the same accepted commit, tree snapshot, and authored content MUST reuse the unique PR rather than create a duplicate.

## AI review boundary

Native Builder/Verifier is the pre-PR acceptance loop. External AI reviewers such as CodeRabbit form a post-PR advisory loop. Review findings MUST be treated as untrusted input and independently checked against the current SHA before code is changed.

The repository required `check` remains the deterministic merge gate. CodeRabbit or another external AI review loop is deferred until external-contributor pull requests create a concrete need. This capability does not install CodeRabbit, make an AI review a required approval, automate review-thread fixes, or automatically merge a PR.
