import 'dart:convert';
import 'dart:io' as io;
import 'dart:typed_data';

import 'package:http/http.dart' as http;

class GooglePhotoItem {
  final String id;
  final String filename;
  final String mimeType;
  final String baseUrl;
  final String? productUrl;
  final DateTime? creationTime;
  final bool isVideo;
  final String? width;
  final String? height;

  GooglePhotoItem({
    required this.id,
    required this.filename,
    required this.mimeType,
    required this.baseUrl,
    this.productUrl,
    this.creationTime,
    required this.isVideo,
    this.width,
    this.height,
  });

  factory GooglePhotoItem.fromJson(Map<String, dynamic> json) {
    final mime = json['mimeType'] as String? ?? 'image/jpeg';
    final metadata = json['mediaMetadata'] as Map<String, dynamic>? ?? {};
    final isVid = mime.startsWith('video/') || metadata.containsKey('video');

    return GooglePhotoItem(
      id: json['id'] as String? ?? '',
      filename: json['filename'] as String? ?? 'photo.jpg',
      mimeType: mime,
      baseUrl: json['baseUrl'] as String? ?? '',
      productUrl: json['productUrl'] as String?,
      creationTime: metadata['creationTime'] != null
          ? DateTime.tryParse(metadata['creationTime'])
          : null,
      isVideo: isVid,
      width: metadata['width']?.toString(),
      height: metadata['height']?.toString(),
    );
  }

  /// Full size download URL (baseUrl + '=d' or '=m8' for video)
  String get downloadUrl => isVideo ? '$baseUrl=dv' : '$baseUrl=d';

  /// Thumbnail URL
  String get thumbnailUrl => '$baseUrl=w400-h400';
}

class GooglePhotoAlbum {
  final String id;
  final String title;
  final String mediaItemsCount;
  final String? coverPhotoBaseUrl;

  GooglePhotoAlbum({
    required this.id,
    required this.title,
    required this.mediaItemsCount,
    this.coverPhotoBaseUrl,
  });

  factory GooglePhotoAlbum.fromJson(Map<String, dynamic> json) {
    return GooglePhotoAlbum(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? 'Untitled Album',
      mediaItemsCount: json['mediaItemsCount'] as String? ?? '0',
      coverPhotoBaseUrl: json['coverPhotoBaseUrl'] as String?,
    );
  }
}

class GooglePhotosDataSource {
  static const _baseUrl = 'https://photoslibrary.googleapis.com/v1';

  Future<List<GooglePhotoItem>> listMediaItems(http.Client client, {String? pageToken}) async {
    final url = Uri.parse('$_baseUrl/mediaItems?pageSize=50${pageToken != null ? '&pageToken=$pageToken' : ''}');
    final response = await client.get(url);

    if (response.statusCode != 200) {
      throw Exception('Failed to list media items: ${response.statusCode} ${response.body}');
    }

    final data = jsonDecode(response.body) as Map<String, dynamic>;
    final itemsJson = data['mediaItems'] as List<dynamic>? ?? [];

    return itemsJson
        .map((item) => GooglePhotoItem.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<List<GooglePhotoAlbum>> listAlbums(http.Client client) async {
    final url = Uri.parse('$_baseUrl/albums?pageSize=50');
    final response = await client.get(url);

    if (response.statusCode != 200) {
      throw Exception('Failed to list albums: ${response.statusCode} ${response.body}');
    }

    final data = jsonDecode(response.body) as Map<String, dynamic>;
    final albumsJson = data['albums'] as List<dynamic>? ?? [];

    return albumsJson
        .map((album) => GooglePhotoAlbum.fromJson(album as Map<String, dynamic>))
        .toList();
  }

  Future<List<GooglePhotoItem>> listAlbumMediaItems(http.Client client, String albumId) async {
    final url = Uri.parse('$_baseUrl/mediaItems:search');
    final response = await client.post(
      url,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'albumId': albumId, 'pageSize': 50}),
    );

    if (response.statusCode != 200) {
      throw Exception('Failed to list album items: ${response.statusCode} ${response.body}');
    }

    final data = jsonDecode(response.body) as Map<String, dynamic>;
    final itemsJson = data['mediaItems'] as List<dynamic>? ?? [];

    return itemsJson
        .map((item) => GooglePhotoItem.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<Uint8List> getMediaItemBytes(http.Client client, GooglePhotoItem item) async {
    final downloadUri = Uri.parse(item.downloadUrl);
    final response = await client.get(downloadUri);
    if (response.statusCode == 200) {
      return response.bodyBytes;
    }
    throw Exception('Failed to download photo bytes: ${response.statusCode}');
  }

  Future<void> uploadMediaItem(
    http.Client client,
    String filePath, {
    String? albumId,
    Function(int bytes)? onProgress,
  }) async {
    final file = io.File(filePath);
    final bytes = await file.readAsBytes();
    final fileName = file.uri.pathSegments.last;

    // 1. Upload bytes to get uploadToken
    final uploadUrl = Uri.parse('$_baseUrl/uploads');
    final uploadResponse = await client.post(
      uploadUrl,
      headers: {
        'Content-Type': 'application/octet-stream',
        'X-Goog-Upload-Content-Type': _guessMimeType(fileName),
        'X-Goog-Upload-Protocol': 'raw',
      },
      body: bytes,
    );

    if (uploadResponse.statusCode != 200) {
      throw Exception('Failed to upload photo bytes: ${uploadResponse.statusCode}');
    }

    final uploadToken = uploadResponse.body;

    // 2. Batch create media item
    final createUrl = Uri.parse('$_baseUrl/mediaItems:batchCreate');
    final createBody = <String, dynamic>{
      'newMediaItems': [
        {
          'description': fileName,
          'simpleMediaItem': {'uploadToken': uploadToken},
        }
      ],
    };

    if (albumId != null && albumId.isNotEmpty) {
      createBody['albumId'] = albumId;
    }

    final createResponse = await client.post(
      createUrl,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(createBody),
    );

    if (createResponse.statusCode != 200) {
      throw Exception('Failed to create photo item: ${createResponse.statusCode}');
    }
  }

  String _guessMimeType(String name) {
    final ext = name.split('.').last.toLowerCase();
    switch (ext) {
      case 'png':
        return 'image/png';
      case 'gif':
        return 'image/gif';
      case 'mp4':
        return 'video/mp4';
      case 'mov':
        return 'video/quicktime';
      default:
        return 'image/jpeg';
    }
  }
}
