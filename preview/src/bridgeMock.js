export const previewSession = 1;

export function createPreviewBridge() {
  return {
    execute(request) {
      if (request.sessionId !== previewSession) {
        return {
          ok: false,
          error: {
            code: "STALE_SESSION",
            message: "The input session is no longer active",
            retryable: false,
          },
        };
      }

      if (request.family === "theme") {
        return { ok: true, response: { type: "theme", value: "SYSTEM" } };
      }

      return {
        ok: false,
        error: {
          code: "RUNTIME_UNAVAILABLE",
          message: "Preview mock: native runtime is unavailable",
          retryable: true,
        },
      };
    },
  };
}