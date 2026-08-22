# Native bridge session gate wiring

The service-owned `AndroidNativeBridge` must receive the shared `SessionGate`
as a constructor argument, and only `resetInputSession()` may call
`SessionGate.begin`. Do not re-begin the session inside a lazy bridge
initializer: first access after invalidation would resurrect a dead session
and mask stale-session errors.

`AndroidNativeBridge` intentionally returns `RUNTIME_UNAVAILABLE` for
clipboard, GIF, and voice requests until their provider lifecycles are
connected; keep that explicit failure instead of fake success.

Kotlin sealed-interface properties need explicit narrowing (`when (operation)
{ is HapticOperation.Press -> ... }`); direct property access on the sealed
type does not compile.
