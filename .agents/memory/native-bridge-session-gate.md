# Native bridge session gate wiring

The service-owned `AndroidNativeBridge` must receive the shared `SessionGate`
as a constructor argument, and only `resetInputSession()` may call
`SessionGate.begin`. Do not re-begin the session inside a lazy bridge
initializer: first access after invalidation would resurrect a dead session
and mask stale-session errors.

`SessionScopedNativeBridge` accepts completion-style handlers so deferred
work (GIF search runs on `gifShareExecutor`, results post through
`mainHandler`) is re-checked against the gate at completion time; sync
handlers keep the convenience constructor.

Clipboard previews expose text only for text items; image items cross the
boundary with null text. GIF failures map through `gifSearchError`
(NOT_CONFIGURED for missing API key, PROVIDER_REJECTED otherwise). Voice
start reports PERMISSION_REQUIRED or RUNTIME_UNAVAILABLE instead of relying
on toasts, then delegates to the native voice path.

Kotlin sealed-interface properties need explicit narrowing (`when (operation)
{ is HapticOperation.Press -> ... }`); direct property access on the sealed
type does not compile.
