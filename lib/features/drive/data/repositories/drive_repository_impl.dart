import 'dart:io';
import 'dart:typed_data';

import 'package:manydrive/features/drive/data/datasources/local/file_cache_datasource.dart';
import 'package:manydrive/features/drive/data/datasources/remote/google_drive_datasource.dart';
import 'package:manydrive/features/drive/data/models/drive_file_model.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';

/// Implementation of DriveRepository
class DriveRepositoryImpl implements DriveRepository {
  final GoogleDriveDataSource _remoteDataSource;
  final FileCacheDataSource _cacheDataSource;

  DriveRepositoryImpl(this._remoteDataSource, this._cacheDataSource);

  @override
  bool get isLoggedIn => _remoteDataSource.isLoggedIn;

  @override
  Future<void> login(Map<String, dynamic> credentials) {
    return _remoteDataSource.login(credentials);
  }

  @override
  Future<List<DriveFile>> listFiles({
    String? folderId,
    bool sharedWithMe = false,
    bool trashed = false,
  }) async {
    final cacheKey = _getCacheKey(folderId, sharedWithMe, trashed);

    try {
      final files = await _remoteDataSource.listFiles(
        folderId: folderId,
        sharedWithMe: sharedWithMe,
        trashed: trashed,
      );

      // Save to cache
      await _cacheDataSource.saveFileList(cacheKey, files);

      return files.map((f) => f.toEntity()).toList();
    } catch (e) {
      // Try to load from cache on error
      final cachedFiles = await _cacheDataSource.loadFileList(cacheKey);
      if (cachedFiles != null) {
        return cachedFiles.map((f) => f.toEntity()).toList();
      }
      rethrow;
    }
  }

  String _getCacheKey(String? folderId, bool sharedWithMe, bool trashed) {
    if (folderId != null) return folderId;
    if (sharedWithMe) return 'shared';
    if (trashed) return 'trashed';
    return 'root';
  }

  @override
  Future<File?> downloadFile(
    DriveFile file, {
    Function(int progress)? onProgress,
  }) {
    final model = DriveFileModel.fromEntity(file);
    return _remoteDataSource.downloadFile(model, onProgress: onProgress);
  }

  @override
  Future<Uint8List> getFileBytes(DriveFile file) {
    final model = DriveFileModel.fromEntity(file);
    return _remoteDataSource.getFileBytes(model);
  }

  @override
  Future<void> uploadFile(
    String filePath, {
    String? parentFolderId,
    Function(int bytes)? onProgress,
  }) {
    return _remoteDataSource.uploadFile(
      filePath,
      parentFolderId: parentFolderId,
      onProgress: onProgress,
    );
  }

  @override
  Future<void> createFolder(String name, {String? parentFolderId}) {
    return _remoteDataSource.createFolder(name, parentFolderId: parentFolderId);
  }

  @override
  Future<void> deleteFile(DriveFile file) {
    return _remoteDataSource.deleteFile(file.id);
  }

  @override
  Future<void> moveFile(DriveFile file, String newParentId) {
    return _remoteDataSource.moveFile(file.id, newParentId);
  }

  @override
  Future<void> copyFile(DriveFile file, String newParentId) {
    return _remoteDataSource.copyFile(file.id, newParentId);
  }

  @override
  Future<void> shareFile(
    DriveFile file,
    String email, {
    String role = 'reader',
  }) {
    return _remoteDataSource.shareFile(file.id, email, role: role);
  }
}
