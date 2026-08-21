# Clipboard panel layout and search states

## Goal

Make clipboard history readable above the keyboard and match the supplied
reference flow.

## Scope

- Default Clipboard state shows compact, readable rows without an embedded
  keyboard covering content.
- A search action opens a dedicated search-entry state with the NovaBoard
  keyboard focused below the query field.
- Enter completes the search and shows a Search results state with the keyboard
  hidden; back returns to the previous state.
- Preserve clipboard pin, delete, paste, image preview, and filtering behavior.
- Add accessible labels for search, clear, row actions, and image content.

## Verification

- Rows remain fully visible in the default state.
- Search entry accepts taps and keyboard input.
- Enter hides the search keyboard and leaves filtered results visible.
- Empty, matching, and non-matching searches render the correct state.
- Unit tests and debug compilation pass.
