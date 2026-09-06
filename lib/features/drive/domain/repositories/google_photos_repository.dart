import 'dart:typed_data';

import 'package:manydrive/features/drive/data/datasources/remote/google_photos_datasource.dart';

abstract class GooglePhotosRepository {
  Future<PhotosPage<GooglePhotoItem>> getMediaItems({String? pageToken});
  Future<PhotosPage<GooglePhotoAlbum>> getAlbums({String? pageToken});
  Future<PhotosPage<GooglePhotoItem>> getAlbumMediaItems(
    String albumId, {
    String? pageToken,
  });
  Future<Uint8List> getMediaItemBytes(GooglePhotoItem item);
  Future<void> uploadMediaItem(
    String filePath, {
    String? albumId,
    Function(int bytes)? onProgress,
  });
}
