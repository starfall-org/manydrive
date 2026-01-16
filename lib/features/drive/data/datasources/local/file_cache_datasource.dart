import 'dart:convert';

import 'package:manydrive/features/drive/data/models/drive_file_model.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Local data source for caching file lists
class FileCacheDataSource {
  Future<void> saveFileList(String key, List<DriveFileModel> files) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final fileJsonList = files.map((file) => file.toJson()).toList();
      await prefs.setString('cached_files_$key', jsonEncode(fileJsonList));
    } catch (_) {
      // Ignore cache save errors
    }
  }

  Future<List<DriveFileModel>?> loadFileList(String key) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final cachedJson = prefs.getString('cached_files_$key');

      if (cachedJson == null) return null;

      final List<dynamic> fileJsonList = jsonDecode(cachedJson);
      return fileJsonList
          .map((json) => DriveFileModel.fromJson(json as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return null;
    }
  }
}
