# Point-in-time records

Dated records of **decisions made at a specific moment**: an ADR-style decision, a postmortem, a
vendor selection, a demoted evergreen doc. Files are named `YYYY-MM-DD-short-slug.md`. Rules about
how things work *today* live in `../evergreen/`.

A record's **body is a snapshot** and isn't rewritten. A later reader needs the reasoning as it
stood, including reasoning that was since superseded. Write it in the past tense: "the canvas
wasn't focusable when this was decided" stays true forever, "the canvas isn't focusable" is a
claim about the present that belongs in an evergreen doc.

## Front matter

Every record starts with this YAML block. Reviewers treat a missing or malformed block as a blocker.

```yaml
---
kind: prescriptive
summary: "One sentence on the decision this record captures."
amendments:
  - date: 2026-03-02
    by: "@github-handle"
    why: "One sentence on why the decision changed."
---
```

- **`kind`** is `prescriptive` or `descriptive`. A prescriptive record is a decision future work
  must follow ("new networking code uses async/await, not Combine"); reversing it silently is a
  review blocker. A descriptive record reports what happened (a postmortem, a writeup, a demoted
  evergreen doc) and constrains nothing.
- **`summary`** is one sentence, in quotes.
- **`amendments`** is the short record of every change to the decision, oldest first. Each entry
  has the date, who made it, and one sentence on why. Use `amendments: []` for a record that has
  never been amended.

## Amendments, not replacements

When a decision changes or is extended, **amend the original record**. Don't write a new record that
invalidates the old one: a reader who finds the old file first has no way to know a newer one
exists, and two records for one decision drift apart.

An amendment is two edits to the same file:

1. Add an entry to `amendments` in the front matter.
2. Append a section to the end of the body, most recent last, carrying the change itself:

```markdown
## Amendment, YYYY-MM-DD (#ticket)

**By:** @github-handle. **Why:** <one or two sentences on what prompted the change>.

<What is decided now, and which part of the record above it replaces. The original text stays.>
```

A record is **published** once it lands on the default branch. A record your own branch added is a
draft: nobody has read it, so if the decision changes before merge, rewrite or delete the draft
rather than amending it. What lands on the default branch reads as though the decision was made
once.

A genuinely new decision on a different subject still gets its own record. The test is whether a
reader of the old record would be misled by it now. If yes, amend it. If the old record is merely
silent on the new decision, write a new one.

## Altitude

A record is for a choice between real alternatives someone could re-propose a year from now, or a
product-level call about what the app *is*. Not for an implementation choice that happened to have
a reason. If violating the decision means editing a single place in the code, put a comment there
instead, and cite the ticket in it (`// #374: kept synchronous because the parent awaits it`) so
reviewers recognise it as a recorded decision rather than an ordinary comment. Most decisions live
in the PR description and never need a record.
