---
name: Release notes preset compatibility
description: Semantic-release release-notes-generator preset behavior and dependency compatibility.
---

Use the default `@semantic-release/release-notes-generator` configuration instead of explicitly forcing the `conventionalcommits` preset in this repository.

**Why:** With the current semantic-release and conventional-changelog dependency versions, the explicit preset produced only a version heading while silently dropping Features and Bug Fixes groups. The default generator produced the expected grouped notes.

**How to apply:** When changing release tooling, reproduce note generation with a representative `feat` commit and verify that the generated text contains a commit group before publishing.