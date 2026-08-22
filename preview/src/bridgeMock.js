export const previewSession = 1;

const defaultPreferences = {
  show_number_row: true,
  show_arrow_keys: true,
  long_press_symbols: true,
  accented_characters: true,
  key_popups: true,
  large_key_text: false,
  image_clipboard_history: false,
  incognito_mode: false,
};

const previewStatus = { enabled: true, selected: false };

const previewEmojis = [
  "😀", "😂", "🤣", "😊", "😍", "😎", "🥳", "🤔",
  "👍", "👏", "🙏", "💪", "🤝", "👋", "✌️", "🤞",
  "❤️", "🔥", "⭐", "✨", "🎉", "💯", "💡", "🚀",
  "☕", "🍕", "🌟", "🌈", "🐱", "🐶", "🌸", "⚡",
];

const previewEmojiIndex = previewEmojis.map((emoji, index) => {
  const labels = [
    "grin happy smile", "joy laugh tears", "rofl laugh", "blush smile happy",
    "heart eyes love", "cool sunglasses", "party celebrate", "thinking hmm",
    "thumbs up approve", "clap applause", "pray thanks please", "muscle strong",
    "handshake deal", "wave hello", "victory peace", "fingers crossed luck",
    "heart love red", "fire hot lit", "star", "sparkles magic", "party celebrate",
    "hundred perfect", "idea light bulb", "rocket launch fast", "coffee drink",
    "pizza food", "glow star", "rainbow", "cat", "dog", "flower", "lightning bolt",
  ];
  return [emoji, labels[index] || ""];
});

function failure(code, message, retryable) {
  return { ok: false, error: { code, message, retryable } };
}

export function createPreviewBridge(options = {}) {
  const failNative = options.failNative === true;
  let preferences = { ...defaultPreferences };

  return {
    execute(request) {
      if (failNative) {
        return failure("RUNTIME_UNAVAILABLE", "Preview mock: native runtime is unavailable", true);
      }

      if (request.sessionId !== previewSession) {
        return failure("STALE_SESSION", "The input session is no longer active", false);
      }

      if (request.family === "theme") {
        return { ok: true, response: { type: "theme", value: "SYSTEM" } };
      }

      if (request.family === "settings") {
        if (request.operation === "readStatus") {
          return {
            ok: true,
            response: { type: "inputMethodStatus", value: { ...previewStatus } },
          };
        }
        return { ok: true, response: { type: "accepted" } };
      }

      if (request.family === "preferences") {
        if (request.operation === "readSnapshot") {
          return { ok: true, response: { type: "preferenceSnapshot", value: { ...preferences } } };
        }
        if (request.operation === "writeBoolean") {
          preferences[request.key] = request.value;
          return { ok: true, response: { type: "accepted" } };
        }
      }

      if (request.family === "emoji") {
        if (request.operation === "list") {
          return {
            ok: true,
            response: { type: "emojiItems", value: previewEmojis.map((emoji) => ({ emoji })) },
          };
        }
        if (request.operation === "search") {
          const query = (request.query || "").trim().toLowerCase();
          const matches = previewEmojiIndex
            .filter(([emoji, labels]) => query === "" || labels.includes(query) || emoji.includes(query))
            .map(([emoji]) => ({ emoji }));
          return { ok: true, response: { type: "emojiItems", value: matches } };
        }
        return failure("INVALID_REQUEST", "Unknown emoji operation", false);
      }

      return failure("RUNTIME_UNAVAILABLE", "Preview mock: native runtime is unavailable", true);
    },
  };
}
