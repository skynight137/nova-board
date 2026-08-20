---
name: Strict JSON import fields
description: Validation rules for optional fields in user-imported preset JSON.
---

Optional JSON fields may have defaults only when they are absent. If a field is
present, its type and value must be validated before applying a default-like
sentinel. Validate numeric bounds before narrowing into a smaller in-memory
type, and normalize parser-library errors at the import boundary.

**Why:** JSON convenience helpers such as `optInt`, `optLong`, and `optBoolean`
can turn malformed present values into valid-looking defaults, allowing damaged
or hostile imports to bypass later semantic validation.

**How to apply:** At import boundaries, use strict typed readers for present
strings, arrays, booleans, and whole numbers, then run the shared semantic
validator before narrowing, merging, or converting imported data.

Android JVM tests that exercise `org.json` need the real test-runtime
implementation wired from the version catalog; compileSdk framework stubs can
otherwise return placeholder values instead of parsing JSON.

**Why:** The Android test classpath can resolve framework signatures without
providing working `JSONObject` behavior, causing valid persistence tests to
fail misleadingly.

**How to apply:** Keep the production API on Android's `org.json`, but add the
catalog JSON artifact as `testImplementation` for pure persistence-contract
tests.
