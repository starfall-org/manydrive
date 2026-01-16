import 'dart:async';
import 'dart:io' as io;
import 'dart:typed_data';

import 'package:googleapis/drive/v3.dart' as drive;
import 'package:googleapis_auth/auth_io.dart';
import 'package:manydrive/features/drive/data/models/drive_file_model.dart';
import 'package:path_provider/path_provider.dart';

/// Remote data source for Google Drive API operations
class GoogleDriveDataSource {
  drive.DriveApi? _driveApi;

  bool get isLoggedIn => _driveApi != null;

  Future<void> login(Map<String, dynamic> credentials) async {
    final scopes = ['https://www.googleapis.com/auth/drive'];
    final serviceAccountCredentials = ServiceAccountCredentials.fromJson(
      credentials,
    );
    final authClient = await clientViaServiceAccount(
      serviceAccountCredentials,
      scopes,
    );
    _driveApi = drive.DriveApi(authClient);
  }

  Future<List<DriveFileModel>> listFiles({
    String? folderId,
    bool sharedWithMe = false,
    bool trashed = false,
  }) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }

    final conditions = <String>[];

    if (folderId == null && !sharedWithMe && !trashed) {
      conditions.add("'root' in parents");
    } else if (folderId != null) {
      conditions.add("'$folderId' in parents");
    } else if (sharedWithMe) {
      conditions.add("sharedWithMe = true");
    } else {
      conditions.add("trashed = true");
    }

    final query = conditions.join(" and ");

    final response = await _driveApi!.files.list(
      q: query.isNotEmpty ? query : null,
      $fields:
          'files(id,name,mimeType,size,createdTime,modifiedTime,thumbnailLink,iconLink,webContentLink,webViewLink,description)',
    );

    return (response.files ?? [])
        .map((file) => DriveFileModel.fromDriveFile(file))
        .toList();
  }

  Future<io.File?> downloadFile(
    DriveFileModel file, {
    Function(int progress)? onProgress,
  }) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }

    final media = await _driveApi!.files.get(
      file.id,
      downloadOptions: drive.DownloadOptions.fullMedia,
    );

    if (media is drive.Media) {
      final buffer = <int>[];
      int totalBytes = 0;
      final fileSize = file.sizeInBytes;

      await for (final chunk in media.stream) {
        buffer.addAll(chunk);
        totalBytes += chunk.length;

        if (onProgress != null && fileSize > 0) {
          final progress = ((totalBytes / fileSize) * 100).round();
          onProgress(progress);
        }
      }

      final bytes = Uint8List.fromList(buffer);
      final directory =
          await getDownloadsDirectory() ??
          await getApplicationDocumentsDirectory();
      final savePath = '${directory.path}/${file.name}';
      final fileIo = io.File(savePath);
      await fileIo.writeAsBytes(bytes);
      return fileIo;
    }

    return null;
  }

  Future<Uint8List> getFileBytes(DriveFileModel file) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }

    final directory = await getApplicationCacheDirectory();
    final cachePath = '${directory.path}/${file.id}_cache';
    final cacheFile = io.File(cachePath);

    if (await cacheFile.exists()) {
      return await cacheFile.readAsBytes();
    }

    final media = await _driveApi!.files.get(
      file.id,
      downloadOptions: drive.DownloadOptions.fullMedia,
    );

    if (media is drive.Media) {
      final buffer = <int>[];
      await for (final chunk in media.stream) {
        buffer.addAll(chunk);
      }

      final bytes = Uint8List.fromList(buffer);
      await cacheFile.writeAsBytes(bytes);
      return bytes;
    }

    throw Exception('Failed to load file: Media not found');
  }

  Future<void> uploadFile(
    String filePath, {
    String? parentFolderId,
    Function(int bytes)? onProgress,
  }) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }

    final file = io.File(filePath);
    final fileSize = await file.length();

    Stream<List<int>> progressStream = file.openRead().transform(
      StreamTransformer.fromHandlers(
        handleData: (data, sink) {
          sink.add(data);
          onProgress?.call(data.length);
        },
      ),
    );

    final media = drive.Media(progressStream, fileSize);
    final driveFile = drive.File()..name = file.uri.pathSegments.last;

    if (parentFolderId != null) {
      driveFile.parents = [parentFolderId];
    }

    await _driveApi!.files.create(driveFile, uploadMedia: media);
  }

  Future<void> createFolder(String name, {String? parentFolderId}) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }

    final driveFile =
        drive.File()
          ..name = name
          ..mimeType = 'application/vnd.google-apps.folder';

    if (parentFolderId != null) {
      driveFile.parents = [parentFolderId];
    }

    await _driveApi!.files.create(driveFile);
  }

  Future<void> deleteFile(String fileId) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }
    await _driveApi!.files.delete(fileId);
  }

  Future<void> moveFile(String fileId, String newParentId) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }
    final driveFile =
        drive.File()
          ..id = fileId
          ..parents = [newParentId];
    await _driveApi!.files.update(driveFile, fileId);
  }

  Future<void> copyFile(String fileId, String newParentId) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }
    final driveFile =
        drive.File()
          ..id = fileId
          ..parents = [newParentId];
    await _driveApi!.files.copy(driveFile, fileId);
  }

  Future<void> shareFile(
    String fileId,
    String email, {
    String role = 'reader',
  }) async {
    if (_driveApi == null) {
      throw Exception('Not logged in');
    }
    final permission =
        drive.Permission()
          ..type = 'user'
          ..role = role
          ..emailAddress = email;
    await _driveApi!.permissions.create(
      permission,
      fileId,
      sendNotificationEmail: true,
    );
  }
}
