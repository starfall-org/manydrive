import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:manydrive/core/services/notification_service.dart';
import 'package:manydrive/core/services/settings_service.dart';
import 'package:manydrive/features/drive/presentation/state/drive_state.dart';
import 'package:manydrive/injection_container.dart';

enum UploadStatus { pending, uploading, completed, failed }

class UploadTask {
  final String id;
  final String fileName;
  final String filePath;
  final int fileSize;
  int uploadedBytes;
  UploadStatus status;
  String? error;

  UploadTask({
    required this.id,
    required this.fileName,
    required this.filePath,
    required this.fileSize,
    this.uploadedBytes = 0,
    this.status = UploadStatus.pending,
    this.error,
  });

  double get progress => fileSize > 0 ? (uploadedBytes / fileSize).clamp(0.0, 1.0) : 0.0;
}

class UploadManager extends ValueNotifier<List<UploadTask>> {
  static final UploadManager _instance = UploadManager._internal();
  factory UploadManager() => _instance;
  UploadManager._internal() : super([]);

  final NotificationService _notificationService = NotificationService();
  final SettingsService _settingsService = injector.settingsService;

  bool _isUploading = false;
  bool get isUploading => _isUploading;

  double get overallProgress {
    if (value.isEmpty) return 0.0;
    final totalSize = value.fold<int>(0, (sum, item) => sum + item.fileSize);
    if (totalSize == 0) return 0.0;
    final uploadedSize = value.fold<int>(0, (sum, item) => sum + item.uploadedBytes);
    return (uploadedSize / totalSize).clamp(0.0, 1.0);
  }

  Future<void> startUploads({
    required List<String> filePaths,
    required List<String> fileNames,
    required List<int> fileSizes,
    required DriveState driveState,
    required String tabKey,
  }) async {
    final tasks = <UploadTask>[];
    for (int i = 0; i < filePaths.length; i++) {
      tasks.add(UploadTask(
        id: '${DateTime.now().millisecondsSinceEpoch}_$i',
        fileName: fileNames[i],
        filePath: filePaths[i],
        fileSize: fileSizes[i],
      ));
    }

    value = [...value, ...tasks];
    notifyListeners();

    _isUploading = true;
    notifyListeners();

    await _notificationService.initialize();

    final mode = _settingsService.uploadMode; // 'sequential' or 'parallel'

    if (mode == 'parallel') {
      await Future.wait(tasks.map((task) => _processTask(task, driveState, tabKey)));
    } else {
      for (final task in tasks) {
        await _processTask(task, driveState, tabKey);
      }
    }

    _isUploading = false;
    notifyListeners();

    driveState.refresh(tabKey);
  }

  Future<void> _processTask(UploadTask task, DriveState driveState, String tabKey) async {
    task.status = UploadStatus.uploading;
    notifyListeners();

    final notificationId = task.id.hashCode.abs();

    try {
      await _notificationService.showProgress(
        id: notificationId,
        title: 'Uploading',
        body: task.fileName,
        progress: 0,
        maxProgress: 100,
      );

      await driveState.uploadFile(
        task.filePath,
        tabKey,
        onProgress: (bytes) async {
          task.uploadedBytes += bytes;
          if (task.uploadedBytes > task.fileSize) {
            task.uploadedBytes = task.fileSize;
          }
          notifyListeners();

          final progInt = (task.progress * 100).round();
          await _notificationService.showProgress(
            id: notificationId,
            title: 'Uploading (${(overallProgress * 100).round()}%)',
            body: task.fileName,
            progress: progInt,
            maxProgress: 100,
          );
        },
      );

      task.uploadedBytes = task.fileSize;
      task.status = UploadStatus.completed;
      notifyListeners();

      await _notificationService.showTransferComplete(
        id: notificationId,
        title: '✅ Upload Complete',
        body: 'Uploaded: ${task.fileName}',
      );
    } catch (e) {
      task.status = UploadStatus.failed;
      task.error = e.toString();
      notifyListeners();

      await _notificationService.cancel(notificationId);
      await _notificationService.showError(
        title: 'Upload Failed',
        message: 'Could not upload ${task.fileName}',
      );
    }
  }

  void clearCompleted() {
    value = value.where((t) => t.status != UploadStatus.completed).toList();
    notifyListeners();
  }
}
