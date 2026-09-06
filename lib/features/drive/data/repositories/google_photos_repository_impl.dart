import 'dart:typed_data';

import 'package:manydrive/features/drive/data/datasources/remote/google_drive_datasource.dart';
import 'package:manydrive/features/drive/data/datasources/remote/google_photos_datasource.dart';
import 'package:manydrive/features/drive/domain/repositories/google_photos_repository.dart';

class GooglePhotosRepositoryImpl implements GooglePhotosRepository {
  final GoogleDriveDataSource _googleDriveDataSource;
  final GooglePhotosDataSource _photosDataSource;

  GooglePhotosRepositoryImpl(
    this._googleDriveDataSource,
    this._photosDataSource,
  );

  @override
  Future<PhotosPage<GooglePhotoItem>> getMediaItems({String? pageToken}) async {
    final client = _googleDriveDataSource.authClient;
    if (client == null) throw Exception('Not logged in to Google');
    return await _photosDataSource.listMediaItems(client, pageToken: pageToken);
  }

  @override
  Future<PhotosPage<GooglePhotoAlbum>> getAlbums({String? pageToken}) async {
    final client = _googleDriveDataSource.authClient;
    if (client == null) throw Exception('Not logged in to Google');
    return await _photosDataSource.listAlbums(client, pageToken: pageToken);
  }

  @override
  Future<PhotosPage<GooglePhotoItem>> getAlbumMediaItems(
    String albumId, {
    String? pageToken,
  }) async {
    final client = _googleDriveDataSource.authClient;
    if (client == null) throw Exception('Not logged in to Google');
    return await _photosDataSource.listAlbumMediaItems(
      client,
      albumId,
      pageToken: pageToken,
    );
  }

  @override
  Future<Uint8List> getMediaItemBytes(GooglePhotoItem item) async {
    final client = _googleDriveDataSource.authClient;
    if (client == null) throw Exception('Not logged in to Google');
    return await _photosDataSource.getMediaItemBytes(client, item);
  }

  @override
  Future<void> uploadMediaItem(
    String filePath, {
    String? albumId,
    Function(int bytes)? onProgress,
  }) async {
    final client = _googleDriveDataSource.authClient;
    if (client == null) throw Exception('Not logged in to Google');
    return await _photosDataSource.uploadMediaItem(
      client,
      filePath,
      albumId: albumId,
      onProgress: onProgress,
    );
  }
}
