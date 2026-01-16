import 'package:flutter/material.dart';

ThemeData lightTheme(ColorScheme? lightDynamic) => ThemeData(
  useMaterial3: true,
  colorScheme:
      lightDynamic ??
      ColorScheme.fromSeed(
        seedColor: const Color.fromARGB(255, 245, 244, 244),
        brightness: Brightness.light,
      ),
);

ThemeData darkTheme(ColorScheme? darkDynamic, {bool superDark = false}) =>
    ThemeData(
      useMaterial3: true,
      colorScheme:
          darkDynamic ??
          ColorScheme.fromSeed(
            seedColor: const Color.fromARGB(255, 71, 69, 66),
            brightness: Brightness.dark,
          ),
      scaffoldBackgroundColor:
          superDark ? const Color(0xFF000000) : const Color(0xFF121212),
    );
