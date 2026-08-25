import 'dart:typed_data';

import 'package:manydrive/features/drive/data/datasources/remote/google_photos_datasource.dart';

abstract class GooglePhotosRepository {
  Future<List<GooglePhotoItem>> getMediaItems();
  Future<List<GooglePhotoAlbum>> getAlbums();
  Future<List<GooglePhotoItem>> getAlbumMediaItems(String albumId);
  Future<Uint8List> getMediaItemBytes(GooglePhotoItem item);
  Future<void> uploadMediaItem(
    String filePath, {
    String? albumId,
    Function(int bytes)? onProgress,
  });
}
