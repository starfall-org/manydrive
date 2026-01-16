import 'dart:io';
import 'dart:typed_data';

import 'package:manydrive/features/drive/domain/entities/drive_file.dart';

/// Abstract repository defining drive operations
/// Implementation details are in the data layer
abstract class DriveRepository {
  /// Check if user is logged in
  bool get isLoggedIn;

  /// Login with service account credentials
  Future<void> login(Map<String, dynamic> credentials);

  /// List files in a folder or root
  Future<List<DriveFile>> listFiles({
    String? folderId,
    bool sharedWithMe = false,
    bool trashed = false,
  });

  /// Download a file and return the local file
  Future<File?> downloadFile(
    DriveFile file, {
    Function(int progress)? onProgress,
  });

  /// Get file bytes (for preview)
  Future<Uint8List> getFileBytes(DriveFile file);

  /// Upload a file
  Future<void> uploadFile(
    String filePath, {
    String? parentFolderId,
    Function(int bytes)? onProgress,
  });

  /// Create a new folder
  Future<void> createFolder(String name, {String? parentFolderId});

  /// Delete a file or folder
  Future<void> deleteFile(DriveFile file);

  /// Move a file to another folder
  Future<void> moveFile(DriveFile file, String newParentId);

  /// Copy a file to another folder
  Future<void> copyFile(DriveFile file, String newParentId);

  /// Share a file or folder with a specific email
  Future<void> shareFile(
    DriveFile file,
    String email, {
    String role = 'reader',
  });
}
