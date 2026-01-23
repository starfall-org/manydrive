import 'dart:async';
import 'dart:io' as io;
import 'dart:typed_data';

import 'package:manydrive/features/drive/data/models/drive_file_model.dart';
import 'package:minio/minio.dart';
import 'package:path_provider/path_provider.dart';

/// Remote data source for S3 Compatible API operations
class S3DriveDataSource {
  Minio? _minio;
  String? _bucket;

  bool get isLoggedIn => _minio != null && _bucket != null;

  Future<void> login(Map<String, dynamic> credentials) async {
    final endpoint = credentials['s3_endpoint'] as String;
    final accessKey = credentials['s3_access_key'] as String;
    final secretKey = credentials['s3_secret_key'] as String;
    _bucket = credentials['s3_bucket'] as String;
    final region = credentials['s3_region'] as String?;

    Uri uri = Uri.parse(endpoint);

    _minio = Minio(
      endPoint: uri.host,
      port: uri.port == 0 ? (uri.scheme == 'https' ? 443 : 80) : uri.port,
      useSSL: uri.scheme == 'https',
      accessKey: accessKey,
      secretKey: secretKey,
      region: region,
    );
  }

  Future<List<DriveFileModel>> listFiles({
    String? folderId,
    bool sharedWithMe = false,
    bool trashed = false,
  }) async {
    if (_minio == null || _bucket == null) {
      throw Exception('Not logged in to S3');
    }

    if (trashed || sharedWithMe) {
      return []; // S3 doesn't have a native "trash" or "shared with me" concept like GDrive
    }

    final prefix = folderId == null ? '' : (folderId.endsWith('/') ? folderId : '$folderId/');
    
    final results = <DriveFileModel>[];
    
    // listObjectsV2 returns a Stream<ListObjectsV2Result>
    final objectsStream = _minio!.listObjectsV2(_bucket!, prefix: prefix, recursive: false);
    
    await for (final result in objectsStream) {
      for (final obj in result.objects) {
        if (obj.key == prefix) continue; // Skip the directory itself
        
        final name = obj.key!.replaceFirst(prefix, '').replaceFirst('/', '');
        if (name.isEmpty) continue;

        results.add(DriveFileModel(
          id: obj.key!,
          name: name,
          mimeType: _guessMimeType(obj.key!),
          size: obj.size.toString(),
          createdTime: obj.lastModified,
          modifiedTime: obj.lastModified,
        ));
      }
      
      for (final prefixObj in result.prefixes) {
        final name = prefixObj.replaceFirst(prefix, '').replaceFirst('/', '');
        results.add(DriveFileModel(
          id: prefixObj,
          name: name,
          mimeType: 'application/vnd.google-apps.folder',
          size: '0',
          createdTime: DateTime.now(),
          modifiedTime: DateTime.now(),
        ));
      }
    }

    return results;
  }

  String _guessMimeType(String key) {
    if (key.endsWith('/')) return 'application/vnd.google-apps.folder';
    final ext = key.split('.').last.toLowerCase();
    switch (ext) {
      case 'mp4': return 'video/mp4';
      case 'mkv': return 'video/x-matroska';
      case 'mov': return 'video/quicktime';
      case 'mp3': return 'audio/mpeg';
      case 'wav': return 'audio/wav';
      case 'txt': return 'text/plain';
      case 'json': return 'application/json';
      case 'jpg':
      case 'jpeg': return 'image/jpeg';
      case 'png': return 'image/png';
      case 'pdf': return 'application/pdf';
      default: return 'application/octet-stream';
    }
  }

  Future<io.File?> downloadFile(
    DriveFileModel file, {
    Function(int progress)? onProgress,
  }) async {
    if (_minio == null || _bucket == null) throw Exception('Not logged in');

    final stream = await _minio!.getObject(_bucket!, file.id);
    final buffer = <int>[];
    int totalBytes = 0;
    final fileSize = int.tryParse(file.size ?? '0') ?? 0;

    await for (final chunk in stream) {
      buffer.addAll(chunk);
      totalBytes += chunk.length;
      if (onProgress != null && fileSize > 0) {
        onProgress(((totalBytes / fileSize) * 100).toInt());
      }
    }

    final directory = await getDownloadsDirectory() ?? await getApplicationDocumentsDirectory();
    final saveFile = io.File('${directory.path}/${file.name}');
    await saveFile.writeAsBytes(buffer);
    return saveFile;
  }

  Future<Uint8List> getFileBytes(DriveFileModel file) async {
    if (_minio == null || _bucket == null) throw Exception('Not logged in');

    final directory = await getApplicationCacheDirectory();
    final cacheFile = io.File('${directory.path}/${file.id.replaceAll('/', '_')}_cache');

    if (await cacheFile.exists()) {
      return await cacheFile.readAsBytes();
    }

    final stream = await _minio!.getObject(_bucket!, file.id);
    final buffer = <int>[];
    await for (final chunk in stream) {
      buffer.addAll(chunk);
    }
    final bytes = Uint8List.fromList(buffer);
    await cacheFile.writeAsBytes(bytes);
    return bytes;
  }

  Future<void> uploadFile(
    String filePath, {
    String? parentFolderId,
    Function(int bytes)? onProgress,
  }) async {
    if (_minio == null || _bucket == null) throw Exception('Not logged in');

    final file = io.File(filePath);
    final name = file.uri.pathSegments.last;
    final key = parentFolderId == null ? name : '$parentFolderId$name';
    
    final size = await file.length();
    final stream = file.openRead();

    // putObject expects Stream<Uint8List>
    final byteStream = stream.map((event) => Uint8List.fromList(event));
    await _minio!.putObject(_bucket!, key, byteStream, size: size);
  }

  Future<void> createFolder(String name, {String? parentFolderId}) async {
    if (_minio == null || _bucket == null) throw Exception('Not logged in');
    final key = parentFolderId == null ? '$name/' : '$parentFolderId$name/';
    // S3 folders are just objects ending with /
    await _minio!.putObject(_bucket!, key, Stream.value(Uint8List(0)), size: 0);
  }

  Future<void> deleteFile(String fileId) async {
    if (_minio == null || _bucket == null) throw Exception('Not logged in');
    
    if (fileId.endsWith('/')) {
      // It's a folder, we need to delete all objects with this prefix
      final objectsStream = _minio!.listObjectsV2(_bucket!, prefix: fileId, recursive: true);
      final objectsToDelete = <String>[];
      
      await for (final result in objectsStream) {
        for (final obj in result.objects) {
          if (obj.key != null) {
            objectsToDelete.add(obj.key!);
          }
        }
      }
      
      if (objectsToDelete.isNotEmpty) {
        await _minio!.removeObjects(_bucket!, objectsToDelete);
      }
    } else {
      // It's a single file
      await _minio!.removeObject(_bucket!, fileId);
    }
  }

  Future<void> moveFile(String fileId, String newParentId) async {
    // S3 move is copy + delete
    await copyFile(fileId, newParentId);
    await deleteFile(fileId);
  }

  Future<void> copyFile(String fileId, String newParentId) async {
    if (_minio == null || _bucket == null) throw Exception('Not logged in');
    final fileName = fileId.endsWith('/') 
        ? fileId.split('/').reversed.elementAt(1) 
        : fileId.split('/').last;
    final destKey = '$newParentId$fileName${fileId.endsWith('/') ? '/' : ''}';
    
    await _minio!.copyObject(_bucket!, destKey, '/$_bucket/$fileId');
  }
}
