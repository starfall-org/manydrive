import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsService {
  static const _themeModeKey = 'theme_mode';
  static const _superDarkModeKey = 'super_dark_mode';
  static const _dynamicColorKey = 'dynamic_color';
  static const _s3PresignedUrlKey = 's3_presigned_url';
  static const _fileCacheKey = 'enable_file_cache';
  static const _uploadModeKey = 'upload_mode';
  static const _backgroundPlaybackKey = 'background_playback';
  static const _sortTypeKey = 'sort_type';
  static const _sortAscendingKey = 'sort_ascending';
  static const _sidebarHeaderImageKey = 'sidebar_header_image';
  static const _enableNotificationsKey = 'enable_notifications';

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

  bool get useS3PresignedUrl => _prefs.getBool(_s3PresignedUrlKey) ?? true;

  Future<void> setUseS3PresignedUrl(bool value) async {
    await _prefs.setBool(_s3PresignedUrlKey, value);
  }

  bool get enableFileCache => _prefs.getBool(_fileCacheKey) ?? true;

  Future<void> setEnableFileCache(bool value) async {
    await _prefs.setBool(_fileCacheKey, value);
  }

  String get uploadMode => _prefs.getString(_uploadModeKey) ?? 'sequential';

  Future<void> setUploadMode(String mode) async {
    await _prefs.setString(_uploadModeKey, mode);
  }

  bool get backgroundPlayback => _prefs.getBool(_backgroundPlaybackKey) ?? true;

  Future<void> setBackgroundPlayback(bool value) async {
    await _prefs.setBool(_backgroundPlaybackKey, value);
  }

  String get sortType => _prefs.getString(_sortTypeKey) ?? 'name';

  Future<void> setSortType(String type) async {
    await _prefs.setString(_sortTypeKey, type);
  }

  bool get sortAscending => _prefs.getBool(_sortAscendingKey) ?? true;

  Future<void> setSortAscending(bool ascending) async {
    await _prefs.setBool(_sortAscendingKey, ascending);
  }

  bool get enableNotifications => _prefs.getBool(_enableNotificationsKey) ?? true;

  Future<void> setEnableNotifications(bool value) async {
    await _prefs.setBool(_enableNotificationsKey, value);
  }

  String? get sidebarHeaderImage => _prefs.getString(_sidebarHeaderImageKey);

  Future<void> setSidebarHeaderImage(String? imagePath) async {
    if (imagePath == null) {
      await _prefs.remove(_sidebarHeaderImageKey);
    } else {
      await _prefs.setString(_sidebarHeaderImageKey, imagePath);
    }
  }
}
