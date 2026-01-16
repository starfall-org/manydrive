import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsService {
  static const _themeModeKey = 'theme_mode';
  static const _superDarkModeKey = 'super_dark_mode';
  static const _dynamicColorKey = 'dynamic_color';

  late final SharedPreferences _prefs;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  ThemeMode get themeMode {
    final value = _prefs.getString(_themeModeKey);
    switch (value) {
      case 'light':
        return ThemeMode.light;
      case 'dark':
        return ThemeMode.dark;
      default:
        return ThemeMode.system;
    }
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    final value = switch (mode) {
      ThemeMode.light => 'light',
      ThemeMode.dark => 'dark',
      _ => 'system',
    };
    await _prefs.setString(_themeModeKey, value);
  }

  bool get superDarkMode => _prefs.getBool(_superDarkModeKey) ?? false;

  Future<void> setSuperDarkMode(bool value) async {
    await _prefs.setBool(_superDarkModeKey, value);
  }

  bool get dynamicColor => _prefs.getBool(_dynamicColorKey) ?? true;

  Future<void> setDynamicColor(bool value) async {
    await _prefs.setBool(_dynamicColorKey, value);
  }
}
