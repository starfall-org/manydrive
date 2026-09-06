import 'package:flutter/material.dart';

// Google apps-style seed (Drive blue); dynamic color overrides it on Android 12+
const Color _seedColor = Color(0xFF1A73E8);

ThemeData lightTheme(ColorScheme? lightDynamic) => ThemeData(
  useMaterial3: true,
  colorScheme:
      lightDynamic ??
      ColorScheme.fromSeed(
        seedColor: _seedColor,
        brightness: Brightness.light,
      ),
);

ThemeData darkTheme(ColorScheme? darkDynamic, {bool superDark = false}) =>
    ThemeData(
      useMaterial3: true,
      colorScheme:
          darkDynamic ??
          ColorScheme.fromSeed(
            seedColor: _seedColor,
            brightness: Brightness.dark,
          ),
      scaffoldBackgroundColor:
          superDark ? const Color(0xFF000000) : const Color(0xFF121212),
    );
