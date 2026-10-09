# Evergreen docs

Living documents describing **how things work today**. Each one covers a single concept, system,
or feature, and is edited in place when the behaviour it describes changes. Point-in-time records
(dated decisions) live in `../point-in-time/`.

Evergreen docs are **constitutional**: a reader is entitled to trust one without checking the code.
That trust is what the rules below protect. A doc here is either current or it leaves.

## Front matter

Every doc in this directory starts with this YAML block. Reviewers treat a missing or malformed
block as a blocker.

```yaml
---
kind: prescriptive
summary: "One sentence on what this document covers."
last-human-review: 2026-01-15
last-human-reviewer: "@github-handle"
review-note: "Optional. One sentence from the reviewer, or omit the key."
---
```

- **`kind`** is `prescriptive` or `descriptive`, and it decides who is wrong when the doc and the
  code disagree.
  - `prescriptive`: the doc is the rule. If the code disagrees, **the code is wrong** and gets
    fixed. Changing the rule is a deliberate act: the PR says so, edits the doc, and explains why.
  - `descriptive`: the doc reports on the code. If the code disagrees, **the doc is wrong** and gets
    updated in the same PR, with the reasoning for the change explained.
- **`summary`** is one sentence, in quotes. It's what a reader scans to decide whether to open the
  doc, so say what it covers, not why it exists.
- **`last-human-review`** and **`last-human-reviewer`** record the last time a *person* read the
  whole doc and vouched for it. An agent never writes to these fields. If you're an agent creating
  a doc, leave them empty; the human who reviews the PR fills them in after reading.
- **`review-note`** is optional. It's for the reviewer's own comment ("skimmed, section 3 is due for
  a rewrite"). Omit the key when there's nothing to say.

## Demotion

An evergreen doc that has gone stale and can't be brought current is **demoted** to a
point-in-time record rather than left here. That happens when the thing it describes is gone, or
the rule it states was retired without replacement. Fixing a descriptive doc that drifted is an
edit, not a demotion.

To demote:

1. Move it: `git mv docs/evergreen/<slug>.md docs/point-in-time/YYYY-MM-DD-<slug>.md`, dated today.
2. Leave the body as it stood. It's now a snapshot of what was true.
3. Set `kind: descriptive` — the doc no longer constrains anything, it records what the rule was —
   and replace the review fields with an `amendments` list whose first entry records the demotion
   (see `../point-in-time/README.md`).
4. Append an `## Amendment` section saying the doc was demoted, why, and what replaces it, if
   anything.

## Backlinks

The code a doc governs carries a `// Doc: <path> — <what it constrains>` comment at the tightest
scope covering the constraint, so a reader editing that code finds the doc. See `doc-backlinks.md`
if this repo has one.
