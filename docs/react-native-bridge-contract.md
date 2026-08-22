# React Native Bridge Contract

Status: Phase 2 native adapter foundation complete; provider-specific operations
and instrumentation coverage are still pending.

## Boundary

React Native communicates with native code through `NativeBridgeRequest` and
receives either a typed `NativeBridgeResponse` or a visible `BridgeError`.
React Native never receives an Android `InputConnection`, service reference, or
unrestricted editor object.

Editor mutations are explicit commands:

- commit text
- replace the current selection
- delete the previous code point
- delete the previous word
- move the cursor

All requests carry an `InputSessionId` and `BridgeRequestId`. Native code must
reject a request whose session is no longer active with `STALE_SESSION`; it must
not report fake success.

## Request families

The contract has typed request families for:

- editor commands
- clipboard listing, search, pinning, and deletion
- GIF search and insertion
- voice start and stop
- boolean preference reads/writes and snapshots
- theme reads
- haptic feedback
- keyboard metrics

Provider, permission, configuration, and runtime failures have distinct error
codes so the UI can show the real unavailable state.

## Implementation rule

The contract is platform-neutral and safe to unit test.
`SessionScopedNativeBridge` provides the session and callback boundary without
owning Android objects. `AndroidNativeBridge` is owned by the Android service
boundary and keeps all `InputConnection` access inside that adapter. It handles
editor commands, boolean preferences, theme reads, haptics, and keyboard
metrics. Clipboard, GIF, and voice operations currently return an explicit
runtime-unavailable error until their provider lifecycles are connected.
The preview uses `preview/src/bridgeMock.js`, a deterministic mock that returns
the same response and error shapes and explicitly reports unavailable native
runtime operations.
