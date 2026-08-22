import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { Pressable, StyleSheet, Text, View } from "react-native";
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

function App() {
  const [dark, setDark] = useState(false);
  const theme = dark ? tokens.dark : tokens.light;
  const bridgeState = createPreviewBridge().execute({
    family: "theme",
    sessionId: previewSession,
    requestId: "preview-theme",
  });
  return (
    <View style={[styles.page, { backgroundColor: theme.background }]}>
      <View style={styles.header}>
        <View>
          <Text style={[styles.eyebrow, { color: theme.accent }]}>NOVABOARD / PREVIEW</Text>
          <Text style={[styles.title, { color: theme.text }]}>Keyboard surface</Text>
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
      <Text style={[styles.caption, { color: theme.secondary }]}>
        Touch targets use the shared {rawTokens.component.touchTargetMinDp}dp minimum. Press a key to preview its state.
      </Text>
      <Text style={[styles.caption, { color: theme.secondary }]}>
        Bridge mock: {bridgeState.ok ? "theme response available" : bridgeState.error.code}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, minHeight: "100vh", padding: 32, fontFamily: "sans-serif" },
  header: { width: "100%", maxWidth: 960, alignSelf: "center", flexDirection: "row", justifyContent: "space-between", gap: 24, marginBottom: 24 },
  eyebrow: { fontSize: 12, fontWeight: "700", letterSpacing: 1.4 },
  title: { fontSize: 28, fontWeight: "700", marginTop: 6 },
  supporting: { fontSize: 13, marginTop: 6 },
  themeButton: { alignSelf: "flex-start", borderWidth: 1, borderRadius: 999, minHeight: 44, paddingHorizontal: 16, justifyContent: "center" },
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
});

createRoot(document.getElementById("root")).render(<App />);