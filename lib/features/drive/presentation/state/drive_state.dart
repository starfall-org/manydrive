import 'dart:async';

import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';

/// State management for Drive feature
/// Manages file lists, navigation history, and authentication state
class DriveState {
  final DriveRepository _driveRepository;
  final CredentialRepository _credentialRepository;

  // Stream controllers for each tab
  final Map<String, StreamController<List<DriveFile>>> _filesControllers = {};

  // Path history for each tab (for back navigation)
  final Map<String, List<String>> _pathHistories = {};

  // In-memory cache
  final Map<String, List<DriveFile>> _cachedFiles = {};

  DriveState(this._driveRepository, this._credentialRepository);

  bool get isLoggedIn => _driveRepository.isLoggedIn;

  /// Get files stream for a specific tab
  Stream<List<DriveFile>> getFilesStream(String tabKey) {
    if (!_filesControllers.containsKey(tabKey)) {
      _filesControllers[tabKey] = StreamController<List<DriveFile>>.broadcast();
    }
    return _filesControllers[tabKey]!.stream;
  }

  /// Get path history for a tab
  List<String> getPathHistory(String tabKey) {
    return _pathHistories[tabKey] ?? [];
  }

  /// Get current folder ID for a tab
  String? getCurrentFolderId(String tabKey) {
    final history = _pathHistories[tabKey];
    if (history == null || history.isEmpty) return null;
    return history.last;
  }

  /// Login with a credential email
  Future<void> login(String clientEmail) async {
    final credential = await _credentialRepository.getCredential(clientEmail);
    if (credential == null) {
      throw Exception('Credential not found for $clientEmail');
    }
    await _driveRepository.login(credential.rawData);

    // Reset path histories when switching accounts to avoid invalid folder IDs
    for (final key in _pathHistories.keys) {
      _pathHistories[key] = [];
    }
  }

  /// List files for a tab
  Future<void> listFiles({
    String? folderId,
    bool sharedWithMe = false,
    required String tabKey,
    bool isRollback = false,
  }) async {
    if (!isLoggedIn) return;

    // Initialize path history for tab
    _pathHistories[tabKey] ??= [];

    // Initialize stream controller
    if (!_filesControllers.containsKey(tabKey)) {
      _filesControllers[tabKey] = StreamController<List<DriveFile>>.broadcast();
    }

    // Update path history
    if (folderId == null && !sharedWithMe) {
      _pathHistories[tabKey] = [];
    } else if (folderId != null && !isRollback) {
      if (_pathHistories[tabKey]!.isEmpty ||
          _pathHistories[tabKey]!.last != folderId) {
        _pathHistories[tabKey]!.add(folderId);
      }
    }

    final cacheKey = _getCacheKey(tabKey, folderId, sharedWithMe);

    // Emit cached data first
    if (_cachedFiles.containsKey(cacheKey)) {
      _filesControllers[tabKey]!.add(_cachedFiles[cacheKey]!);
    }

    try {
      final files = await _driveRepository.listFiles(
        folderId: folderId,
        sharedWithMe: sharedWithMe,
      );

      _cachedFiles[cacheKey] = files;
      _filesControllers[tabKey]!.add(files);
    } catch (e) {
      // If we have cached data, don't throw
      if (!_cachedFiles.containsKey(cacheKey)) {
        rethrow;
      }
    }
  }

  String _getCacheKey(String tabKey, String? folderId, bool sharedWithMe) {
    if (folderId != null) return '${tabKey}_$folderId';
    if (sharedWithMe) return '${tabKey}_shared';
    return '${tabKey}_root';
  }

  /// Go back to previous folder
  Future<void> goBack(String tabKey) async {
    final history = _pathHistories[tabKey];
    if (history == null || history.isEmpty) return;

    history.removeLast();

    if (history.isNotEmpty) {
      await listFiles(folderId: history.last, tabKey: tabKey, isRollback: true);
    } else {
      // Go to root or shared based on tab
      if (tabKey == 'shared') {
        await listFiles(sharedWithMe: true, tabKey: tabKey, isRollback: true);
      } else {
        await listFiles(tabKey: tabKey, isRollback: true);
      }
    }
  }

  /// Refresh current folder
  Future<void> refresh(String tabKey) async {
    final history = _pathHistories[tabKey] ?? [];

    if (history.isEmpty) {
      if (tabKey == 'shared') {
        await listFiles(sharedWithMe: true, tabKey: tabKey, isRollback: true);
      } else {
        await listFiles(tabKey: tabKey, isRollback: true);
      }
    } else {
      await listFiles(folderId: history.last, tabKey: tabKey, isRollback: true);
    }
  }

  /// Upload a file
  Future<void> uploadFile(
    String filePath,
    String tabKey, {
    Function(int)? onProgress,
  }) async {
    final parentFolderId = getCurrentFolderId(tabKey);
    await _driveRepository.uploadFile(
      filePath,
      parentFolderId: parentFolderId,
      onProgress: onProgress,
    );
  }

  /// Create a folder
  Future<void> createFolder(String name, String tabKey) async {
    final parentFolderId = getCurrentFolderId(tabKey);
    await _driveRepository.createFolder(name, parentFolderId: parentFolderId);
  }

  /// Delete a file
  Future<void> deleteFile(DriveFile file) {
    return _driveRepository.deleteFile(file);
  }

  /// Download a file
  Future<void> downloadFile(DriveFile file, {Function(int)? onProgress}) {
    return _driveRepository.downloadFile(file, onProgress: onProgress);
  }

  /// Get file bytes for preview
  Future<dynamic> getFileBytes(DriveFile file) {
    return _driveRepository.getFileBytes(file);
  }

  /// Share a file or folder with a specific email
  Future<void> shareFile(
    DriveFile file,
    String email, {
    String role = 'reader',
  }) {
    return _driveRepository.shareFile(file, email, role: role);
  }

  /// Dispose resources
  void dispose() {
    for (final controller in _filesControllers.values) {
      controller.close();
    }
    _filesControllers.clear();
  }
}
