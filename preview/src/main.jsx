import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import rawTokens from "../../design-system/novaboard/design-tokens.json";
import { createPreviewBridge, previewSession } from "./bridgeMock";
import "./styles.css";

const tokens = {
  light: {
    background: rawTokens.primitive.color.surface0.light,
    surface: rawTokens.primitive.color.surface1.light,
    utility: rawTokens.primitive.color.surface2.light,
    pressed: rawTokens.primitive.color.surfacePressed.light,
    text: rawTokens.primitive.color.ink950.light,
    secondary: rawTokens.primitive.color.ink700.light,
    accent: rawTokens.primitive.color.blue500.light,
    accentPressed: rawTokens.primitive.color.blue700.light,
    divider: rawTokens.primitive.color.line.light,
  },
  dark: {
    background: rawTokens.primitive.color.surface0.dark,
    surface: rawTokens.primitive.color.surface1.dark,
    utility: rawTokens.primitive.color.surface2.dark,
    pressed: rawTokens.primitive.color.surfacePressed.dark,
    text: rawTokens.primitive.color.ink950.dark,
    secondary: rawTokens.primitive.color.ink700.dark,
    accent: rawTokens.primitive.color.blue500.dark,
    accentPressed: rawTokens.primitive.color.blue700.dark,
    divider: rawTokens.primitive.color.line.dark,
  },
};

const rows = [
  ["Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"],
  ["A", "S", "D", "F", "G", "H", "J", "K", "L"],
  ["⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"],
];

let previewRequestCounter = 0;

function nextRequestId(prefix) {
  previewRequestCounter += 1;
  return `${prefix}-${previewRequestCounter}`;
}

function executeBridge(bridge, request) {
  return bridge.execute({ sessionId: previewSession, ...request });
}

function Key({ label, theme, utility = false }) {
  const [pressed, setPressed] = useState(false);
  return (
    <Pressable
      accessibilityLabel={`${label} key`}
      onPressIn={() => setPressed(true)}
      onPressOut={() => setPressed(false)}
      style={[
        styles.key,
        { backgroundColor: pressed ? theme.pressed : utility ? theme.utility : theme.surface },
      ]}
    >
      <Text style={[styles.keyLabel, { color: theme.text }]}>{label}</Text>
    </Pressable>
  );
}

const preferenceRows = [
  ["show_number_row", "Number row"],
  ["autocorrect", "Autocorrect"],
  ["sound_on_keypress", "Keypress sound"],
  ["image_clipboard_history", "Image clipboard history"],
  ["incognito_mode", "Incognito mode"],
];

function StatusBadge({ theme, active, label }) {
  return (
    <View
      accessibilityLabel={`${label}: ${active ? "on" : "off"}`}
      style={[styles.badge, { backgroundColor: active ? theme.accent : theme.utility }]}
    >
      <Text style={{ color: active ? "#ffffff" : theme.secondary, fontSize: 12, fontWeight: "700" }}>
        {active ? "ON" : "OFF"}
      </Text>
    </View>
  );
}

function SettingsScreen({ theme, failNative }) {
  const bridge = useMemo(() => createPreviewBridge({ failNative }), [failNative]);
  const [status, setStatus] = useState("loading");
  const [statusValue, setStatusValue] = useState(null);
  const [preferences, setPreferences] = useState(null);
  const [errorCode, setErrorCode] = useState("");
  const [loadCount, setLoadCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    const timer = setTimeout(() => {
      if (cancelled) return;
      const statusResult = executeBridge(bridge, {
        requestId: nextRequestId("settings-status"),
        family: "settings",
        operation: "readStatus",
      });
      const snapshotResult = executeBridge(bridge, {
        requestId: nextRequestId("prefs-snapshot"),
        family: "preferences",
        operation: "readSnapshot",
      });
      if (!statusResult.ok) {
        setStatus("error");
        setErrorCode(statusResult.error.code);
        return;
      }
      if (!snapshotResult.ok) {
        setStatus("error");
        setErrorCode(snapshotResult.error.code);
        return;
      }
      setStatusValue(statusResult.response.value);
      setPreferences(snapshotResult.response.value);
      setStatus("ready");
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [bridge, loadCount]);

  const writePreference = (key, value) => {
    const result = executeBridge(bridge, {
      requestId: nextRequestId("prefs-write"),
      family: "preferences",
      operation: "writeBoolean",
      key,
      value,
    });
    if (result.ok && preferences) {
      setPreferences({ ...preferences, [key]: value });
    }
  };

  if (status === "loading") {
    return (
      <View style={[styles.settingsCard, { backgroundColor: theme.surface, borderColor: theme.divider }]}>
        <Text accessibilityLabel="Settings loading" style={[styles.sectionTitle, { color: theme.text }]}>
          Loading settings…
        </Text>
        {[0, 1, 2].map((row) => (
          <View key={row} style={[styles.skeletonRow, { backgroundColor: theme.utility }]} />
        ))}
      </View>
    );
  }

  if (status === "error") {
    return (
      <View style={[styles.settingsCard, { backgroundColor: theme.surface, borderColor: theme.divider }]}>
        <Text accessibilityRole="alert" style={[styles.sectionTitle, { color: theme.text }]}>
          Settings are unavailable
        </Text>
        <Text style={{ color: theme.secondary, marginTop: 4 }}>Bridge error: {errorCode}</Text>
        <Pressable
          accessibilityLabel="Retry loading settings"
          onPress={() => setLoadCount((count) => count + 1)}
          style={[styles.primaryButton, { backgroundColor: theme.accent }]}
        >
          <Text style={{ color: "#ffffff", fontWeight: "700" }}>Retry</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.settingsScroll}
      contentContainerStyle={[styles.settingsCard, { backgroundColor: theme.surface, borderColor: theme.divider }]}
    >
      <Text style={[styles.sectionTitle, { color: theme.text }]}>Keyboard activation</Text>
      <View style={styles.statusRow}>
        <Text style={{ color: theme.text }}>Enable NovaBoard</Text>
        <StatusBadge theme={theme} active={statusValue.enabled} label="NovaBoard enabled" />
      </View>
      <View style={styles.statusRow}>
        <Text style={{ color: theme.text }}>Active input method</Text>
        <StatusBadge theme={theme} active={statusValue.selected} label="NovaBoard selected" />
      </View>
      <View style={styles.actionRow}>
        <Pressable
          accessibilityLabel="Open Android keyboard settings"
          onPress={() =>
            executeBridge(bridge, {
              requestId: nextRequestId("settings-enable"),
              family: "settings",
              operation: "openImeSettings",
            })
          }
          style={[styles.primaryButton, { backgroundColor: theme.accent }]}
        >
          <Text style={{ color: "#ffffff", fontWeight: "700" }}>Enable NovaBoard</Text>
        </Pressable>
        <Pressable
          accessibilityLabel="Switch input method"
          onPress={() =>
            executeBridge(bridge, {
              requestId: nextRequestId("settings-picker"),
              family: "settings",
              operation: "showImePicker",
            })
          }
          style={[styles.secondaryButton, { borderColor: theme.divider }]}
        >
          <Text style={{ color: theme.text, fontWeight: "600" }}>Switch input method</Text>
        </Pressable>
      </View>

      <Text style={[styles.sectionTitle, { color: theme.text }]}>Preferences</Text>
      {preferenceRows.map(([key, label]) => (
        <View key={key} style={styles.prefRow}>
          <Text style={{ flex: 1, color: theme.text }}>{label}</Text>
          <Pressable
            accessibilityLabel={`${label} preference`}
            accessibilityRole="switch"
            accessibilityState={{ checked: Boolean(preferences[key]) }}
            onPress={() => writePreference(key, !preferences[key])}
            style={[
              styles.switchTrack,
              preferences[key] ? { backgroundColor: theme.accent } : { backgroundColor: theme.utility },
            ]}
          >
            <View
              style={[
                styles.switchThumb,
                preferences[key] ? styles.switchThumbOn : null,
                { backgroundColor: preferences[key] ? "#ffffff" : theme.secondary },
              ]}
            />
          </Pressable>
        </View>
      ))}

      <Text style={[styles.sectionTitle, { color: theme.text }]}>Gesture typing</Text>
      <Text style={{ color: theme.secondary }}>
        No options in this group yet. Gesture controls stay on the native keyboard.
      </Text>
    </ScrollView>
  );
}

function KeyboardSurface({ theme }) {
  return (
    <View style={[styles.preview, { backgroundColor: theme.surface, borderColor: theme.divider }]}>
      <View style={styles.toolbar}>
        {["Clipboard", "Emoji", "GIF", "Tools"].map((item, index) => (
          <Pressable
            accessibilityLabel={`${item} toolbar action`}
            key={item}
            style={[
              styles.toolbarAction,
              { backgroundColor: index === 0 ? theme.utility : "transparent" },
            ]}
          >
            <Text style={[styles.utilityLabel, { color: theme.secondary }]}>{item}</Text>
          </Pressable>
        ))}
      </View>
      <View style={[styles.suggestions, { borderTopColor: theme.divider, borderBottomColor: theme.divider }]}>
        {["the", "to", "this"].map((suggestion) => (
          <Text key={suggestion} style={[styles.suggestion, { color: theme.secondary }]}>
            {suggestion}
          </Text>
        ))}
      </View>
      <View style={styles.keyboard}>
        {rows.map((row) => (
          <View key={row.join("")} style={styles.row}>
            {row.map((label) => (
              <Key key={label} label={label} theme={theme} utility={label === "⇧" || label === "⌫"} />
            ))}
          </View>
        ))}
        <View style={styles.row}>
          <Key label="123" theme={theme} utility />
          <Key label="space" theme={theme} />
          <Key label="↵" theme={theme} utility />
        </View>
      </View>
    </View>
  );
}

function App() {
  const [dark, setDark] = useState(false);
  const [surface, setSurface] = useState("keyboard");
  const [narrow, setNarrow] = useState(false);
  const [failNative, setFailNative] = useState(false);
  const theme = dark ? tokens.dark : tokens.light;
  const chromeBridge = createPreviewBridge();
  const themeBridgeState = chromeBridge.execute({
    family: "theme",
    sessionId: previewSession,
    requestId: "preview-theme",
  });

  return (
    <View style={[styles.page, { backgroundColor: theme.background }]}>
      <View style={styles.header}>
        <View>
          <Text style={[styles.eyebrow, { color: theme.accent }]}>NOVABOARD / PREVIEW</Text>
          <Text style={[styles.title, { color: theme.text }]}>
            {surface === "keyboard" ? "Keyboard surface" : "Settings surface"}
          </Text>
          <Text style={[styles.supporting, { color: theme.secondary }]}>
            React Native Web · token-backed component
          </Text>
        </View>
        <Pressable
          accessibilityLabel="Toggle theme"
          accessibilityRole="switch"
          accessibilityState={{ checked: dark }}
          onPress={() => setDark((value) => !value)}
          style={[styles.themeButton, { borderColor: theme.divider }]}
        >
          <Text style={{ color: theme.text }}>{dark ? "Light" : "Dark"} mode</Text>
        </Pressable>
      </View>

      <View style={styles.chromeRow}>
        {[
          ["keyboard", "Keyboard"],
          ["settings", "Settings"],
        ].map(([id, label]) => (
          <Pressable
            key={id}
            accessibilityLabel={`Show ${label.toLowerCase()} surface`}
            accessibilityRole="tab"
            accessibilityState={{ selected: surface === id }}
            onPress={() => setSurface(id)}
            style={[
              styles.tabButton,
              surface === id ? { backgroundColor: theme.accent } : { backgroundColor: theme.utility },
            ]}
          >
            <Text
              style={{
                color: surface === id ? "#ffffff" : theme.secondary,
                fontWeight: "700",
                fontSize: 12,
              }}
            >
              {label.toUpperCase()}
            </Text>
          </Pressable>
        ))}
        <Pressable
          accessibilityLabel="Toggle narrow phone frame"
          accessibilityRole="switch"
          accessibilityState={{ checked: narrow }}
          onPress={() => setNarrow((value) => !value)}
          style={[styles.tabButton, { backgroundColor: narrow ? theme.accent : theme.utility }]}
        >
          <Text style={{ color: narrow ? "#ffffff" : theme.secondary, fontWeight: "700", fontSize: 12 }}>
            NARROW PHONE
          </Text>
        </Pressable>
        {surface === "settings" ? (
          <Pressable
            accessibilityLabel="Simulate unavailable native runtime"
            accessibilityRole="switch"
            accessibilityState={{ checked: failNative }}
            onPress={() => setFailNative((value) => !value)}
            style={[styles.tabButton, { backgroundColor: failNative ? theme.accentPressed : theme.utility }]}
          >
            <Text style={{ color: failNative ? "#ffffff" : theme.secondary, fontWeight: "700", fontSize: 12 }}>
              SIMULATE ERROR
            </Text>
          </Pressable>
        ) : null}
      </View>

      <View style={narrow ? styles.narrowFrame : null}>
        {surface === "keyboard" ? (
          <KeyboardSurface theme={theme} />
        ) : (
          <SettingsScreen theme={theme} failNative={failNative} />
        )}
      </View>

      <Text style={[styles.caption, { color: theme.secondary }]}>
        Touch targets use the shared {rawTokens.component.touchTargetMinDp}dp minimum.
      </Text>
      <Text style={[styles.caption, { color: theme.secondary }]}>
        Bridge mock: {themeBridgeState.ok ? "theme response available" : themeBridgeState.error.code}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, minHeight: "100vh", padding: 32, fontFamily: "sans-serif" },
  header: { width: "100%", maxWidth: 960, alignSelf: "center", flexDirection: "row", justifyContent: "space-between", gap: 24, marginBottom: 16 },
  eyebrow: { fontSize: 12, fontWeight: "700", letterSpacing: 1.4 },
  title: { fontSize: 28, fontWeight: "700", marginTop: 6 },
  supporting: { fontSize: 13, marginTop: 6 },
  themeButton: { alignSelf: "flex-start", borderWidth: 1, borderRadius: 999, minHeight: 44, paddingHorizontal: 16, justifyContent: "center" },
  chromeRow: { width: "100%", maxWidth: 960, alignSelf: "center", flexDirection: "row", gap: 8, marginBottom: 16, flexWrap: "wrap" },
  tabButton: { minHeight: 36, paddingHorizontal: 14, borderRadius: 999, justifyContent: "center" },
  narrowFrame: { width: "100%", maxWidth: 360, alignSelf: "center", borderWidth: 1, borderColor: "#8884", borderRadius: 18, overflow: "hidden", padding: 8 },
  preview: { width: "100%", maxWidth: 960, alignSelf: "center", borderWidth: 1, borderRadius: 10, padding: 12 },
  toolbar: { flexDirection: "row", gap: 8, minHeight: 44, alignItems: "center" },
  toolbarAction: { minHeight: 44, paddingHorizontal: 12, borderRadius: 6, justifyContent: "center" },
  utilityLabel: { fontSize: 12, fontWeight: "600" },
  suggestions: { flexDirection: "row", justifyContent: "space-around", paddingVertical: 10, borderTopWidth: 1, borderBottomWidth: 1 },
  suggestion: { fontSize: 16 },
  keyboard: { gap: 8, paddingTop: 12 },
  row: { flexDirection: "row", justifyContent: "center", gap: 6 },
  key: { flex: 1, maxWidth: 76, minWidth: 44, minHeight: 48, borderRadius: 6, alignItems: "center", justifyContent: "center", paddingHorizontal: 8 },
  keyLabel: { fontSize: 20, fontWeight: "500" },
  caption: { width: "100%", maxWidth: 960, alignSelf: "center", fontSize: 13, marginTop: 12, lineHeight: 20 },
  settingsCard: { width: "100%", maxWidth: 960, alignSelf: "center", borderWidth: 1, borderRadius: 10, padding: 16, gap: 4 },
  settingsScroll: { flexGrow: 0 },
  sectionTitle: { fontSize: 16, fontWeight: "700", marginTop: 12 },
  statusRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", minHeight: 44 },
  badge: { borderRadius: 999, paddingHorizontal: 10, minHeight: 24, justifyContent: "center" },
  actionRow: { flexDirection: "row", gap: 8, marginVertical: 12, flexWrap: "wrap" },
  primaryButton: { minHeight: 44, paddingHorizontal: 16, borderRadius: 8, justifyContent: "center" },
  secondaryButton: { minHeight: 44, paddingHorizontal: 16, borderRadius: 8, borderWidth: 1, justifyContent: "center" },
  prefRow: { flexDirection: "row", alignItems: "center", minHeight: 52 },
  switchTrack: { width: 48, height: 28, borderRadius: 999, justifyContent: "center", paddingHorizontal: 3 },
  switchThumb: { width: 22, height: 22, borderRadius: 11 },
  switchThumbOn: { alignSelf: "flex-end" },
  skeletonRow: { height: 40, borderRadius: 8, marginTop: 10 },
});

createRoot(document.getElementById("root")).render(<App />);
