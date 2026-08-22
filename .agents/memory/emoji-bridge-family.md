# Emoji bridge family

The emoji bridge family (list and search) is served from the shared
`EmojiData` source on the service side, so the native `EmojiPanel` and any
future hosted panel read the same emoji provider and keyword index. Keep the
emoji source authoritative in `EmojiData`; the bridge only maps operations to
`EmojiPreviewItem` responses. Missing handlers stay an explicit
runtime-unavailable error, matching the other provider seams.
