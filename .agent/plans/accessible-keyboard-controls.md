# Make custom keyboard and cursor controls fully accessible

## What & Why

The custom KeyboardView and cursor repeat touch listener intercept touch events
without routing click activation through performClick. Screen readers and
accessibility services may not be able to activate these controls consistently.

## Done looks like

- KeyboardView implements performClick and invokes the same activation path used
  by taps where applicable.
- Cursor repeat controls preserve repeat behavior while still exposing a valid
  click action and accessibility state.
- Image preview content has a meaningful content description when it conveys
  clipboard content, or is explicitly marked decorative when it does not.
- Accessibility-focused regression coverage or a documented manual verification
  path is added.

## Relevant files

- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/res/layout/clipboard_item.xml`
