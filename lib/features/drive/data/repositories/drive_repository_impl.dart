import 'dart:io';
import 'dart:typed_data';

import 'package:manydrive/features/drive/data/datasources/local/file_cache_datasource.dart';
import 'package:manydrive/features/drive/data/datasources/remote/google_drive_datasource.dart';
import 'package:manydrive/features/drive/data/datasources/remote/s3_drive_datasource.dart';
import 'package:manydrive/features/drive/data/models/drive_file_model.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';

/// Implementation of DriveRepository
class DriveRepositoryImpl implements DriveRepository {
  final GoogleDriveDataSource _googleDataSource;
  final S3DriveDataSource _s3DataSource;
  final FileCacheDataSource _cacheDataSource;
  bool _isS3 = false;

  DriveRepositoryImpl(
    this._googleDataSource,
    this._s3DataSource,
    this._cacheDataSource,
  );

  @override
  bool get isLoggedIn =>
      _isS3 ? _s3DataSource.isLoggedIn : _googleDataSource.isLoggedIn;

  @override
  Future<void> login(Map<String, dynamic> credentials) async {
    _isS3 = credentials.containsKey('s3_endpoint');
    if (_isS3) {
      await _s3DataSource.login(credentials);
    } else {
      await _googleDataSource.login(credentials);
    }
  }

  @override
  Future<List<DriveFile>> listFiles({
    String? folderId,
    bool sharedWithMe = false,
    bool trashed = false,
  }) async {
    final cacheKey =
        '${_isS3 ? 's3' : 'gdrive'}_${_getCacheKey(folderId, sharedWithMe, trashed)}';

    try {
      final List<DriveFileModel> files;
      if (_isS3) {
        files = await _s3DataSource.listFiles(
          folderId: folderId,
          sharedWithMe: sharedWithMe,
          trashed: trashed,
        );
      } else {
        files = await _googleDataSource.listFiles(
          folderId: folderId,
          sharedWithMe: sharedWithMe,
          trashed: trashed,
        );
      }

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
    if (_isS3) {
      return _s3DataSource.downloadFile(model, onProgress: onProgress);
    }
    return _googleDataSource.downloadFile(model, onProgress: onProgress);
  }

  @override
  Future<Uint8List> getFileBytes(DriveFile file) {
    final model = DriveFileModel.fromEntity(file);
    if (_isS3) {
      return _s3DataSource.getFileBytes(model);
    }
    return _googleDataSource.getFileBytes(model);
  }

  @override
  Future<void> uploadFile(
    String filePath, {
    String? parentFolderId,
    Function(int bytes)? onProgress,
  }) {
    if (_isS3) {
      return _s3DataSource.uploadFile(
        filePath,
        parentFolderId: parentFolderId,
        onProgress: onProgress,
      );
    }
    return _googleDataSource.uploadFile(
      filePath,
      parentFolderId: parentFolderId,
      onProgress: onProgress,
    );
  }

  @override
  Future<void> createFolder(String name, {String? parentFolderId}) {
    if (_isS3) {
      return _s3DataSource.createFolder(name, parentFolderId: parentFolderId);
    }
    return _googleDataSource.createFolder(
      name,
      parentFolderId: parentFolderId,
    );
  }

  @override
  Future<void> deleteFile(DriveFile file) {
    if (_isS3) {
      return _s3DataSource.deleteFile(file.id);
    }
    return _googleDataSource.deleteFile(file.id);
  }

  @override
  Future<void> moveFile(DriveFile file, String newParentId) {
    if (_isS3) {
      return _s3DataSource.moveFile(file.id, newParentId);
    }
    return _googleDataSource.moveFile(file.id, newParentId);
  }

  @override
  Future<void> copyFile(DriveFile file, String newParentId) {
    if (_isS3) {
      return _s3DataSource.copyFile(file.id, newParentId);
    }
    return _googleDataSource.copyFile(file.id, newParentId);
  }

  @override
  Future<void> shareFile(
    DriveFile file,
    String email, {
    String role = 'reader',
  }) {
    if (_isS3) {
      return Future.value(); // Not supported in S3
    }
    return _googleDataSource.shareFile(file.id, email, role: role);
  }
}
