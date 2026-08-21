---
name: System emoji menu
description: The emoji picker intentionally uses a minimal system-rendered grid without alternate font controls.
---

The emoji picker should remain a simple system-emoji-only surface: no custom font selector, duplicate symbol section, category chrome, or third-party renderer.

**Why:** The requested product direction is a clean, low-distraction emoji menu that follows the device’s native emoji appearance.

**How to apply:** Keep emoji data deduplicated, render with the platform fallback, and avoid reintroducing font or category controls unless the product direction changes.