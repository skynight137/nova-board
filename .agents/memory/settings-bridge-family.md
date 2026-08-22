# Settings bridge family

Settings actions cross the bridge through a dedicated `SettingsOperation`
family, not by reusing editor or preference requests. `MainActivity` owns the
session-gated adapter for it; `InputMethodStatusValue` enforces that a
selected input method is also enabled so the UI can never render a
contradictory activation state.

The preview settings surface must keep its loading, retryable error,
empty-group, light/dark, and narrow-phone states driven only by the mock
bridge contract; do not special-case preview chrome into production paths.
