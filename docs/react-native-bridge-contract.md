# React Native Bridge Contract

Status: Phase 3 settings family landed; production settings hosting and
instrumentation coverage for the service-owned adapter are still pending.

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
- settings actions: open Android's keyboard settings, show the input-method
  picker, and read enabled/selected status (`MainActivity` owns this adapter;
  a selected method must also be enabled)

Provider, permission, configuration, and runtime failures have distinct error
codes so the UI can show the real unavailable state.

## Implementation rule

The contract is platform-neutral and safe to unit test.
`SessionScopedNativeBridge` provides the session and callback boundary without
owning Android objects; deferred completions are re-checked against the input
session when they arrive, so a session change while a GIF search runs yields
`STALE_SESSION`. `AndroidNativeBridge` is owned by the Android service
boundary and keeps all `InputConnection` access inside that adapter. It
handles editor commands, boolean preferences, theme reads, haptics, keyboard
metrics, clipboard listing/search/pinning/deletion through the durable
history manager, GIF search and insertion through the native KLIPY client and
editor handoff (network runs off the main thread), and voice start/stop with
typed permission and availability failures.
The preview uses `preview/src/bridgeMock.js`, a deterministic mock that returns
the same response and error shapes and explicitly reports unavailable native
runtime operations.
