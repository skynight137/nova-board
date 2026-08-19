---
name: Overlay state boundary
description: The floating panel must decide whether a session can resume from service state, not marker rendering state.
---

The accessibility service's live gesture list is the source of truth for whether a session has work ready to dispatch. Overlay marker count is only presentation state and can be zero when a loaded setup is hidden or its markers are unavailable.

**Why:** A saved tap can be present in the running service while the floating panel's marker-count guard rejects Resume, making manually added taps work while loaded presets appear inert.

**How to apply:** Use service-owned gesture/session state for execution guards. Treat marker visibility, touchability, and count as UI concerns only.