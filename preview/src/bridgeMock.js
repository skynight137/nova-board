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

      return failure("RUNTIME_UNAVAILABLE", "Preview mock: native runtime is unavailable", true);
    },
  };
}
