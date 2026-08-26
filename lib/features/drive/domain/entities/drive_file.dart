/// Domain entity representing a file in Google Drive
/// This is independent of any external library
class DriveFile {
  final String id;
  final String name;
  final String? mimeType;
  final String? size;
  final DateTime? createdTime;
  final DateTime? modifiedTime;
  final String? thumbnailLink;
  final String? iconLink;
  final String? webContentLink;
  final String? webViewLink;
  final String? description;

  const DriveFile({
    required this.id,
    required this.name,
    this.mimeType,
    this.size,
    this.createdTime,
    this.modifiedTime,
    this.thumbnailLink,
    this.iconLink,
    this.webContentLink,
    this.webViewLink,
    this.description,
  });

  bool get isFolder => mimeType == 'application/vnd.google-apps.folder';
  bool get isVideo => mimeType?.startsWith('video/') == true;
  bool get isAudio => mimeType?.startsWith('audio/') == true;
  bool get isImage => mimeType?.startsWith('image/') == true;
  bool get isText =>
      mimeType?.startsWith('text/') == true || mimeType == 'application/json';

  int get sizeInBytes => int.tryParse(size ?? '0') ?? 0;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'mimeType': mimeType,
      'size': size,
      'createdTime': createdTime?.toIso8601String(),
      'modifiedTime': modifiedTime?.toIso8601String(),
      'thumbnailLink': thumbnailLink,
      'iconLink': iconLink,
      'webContentLink': webContentLink,
      'webViewLink': webViewLink,
      'description': description,
    };
  }

  factory DriveFile.fromJson(Map<String, dynamic> json) {
    return DriveFile(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      mimeType: json['mimeType'] as String?,
      size: json['size'] as String?,
      createdTime: json['createdTime'] != null ? DateTime.tryParse(json['createdTime']) : null,
      modifiedTime: json['modifiedTime'] != null ? DateTime.tryParse(json['modifiedTime']) : null,
      thumbnailLink: json['thumbnailLink'] as String?,
      iconLink: json['iconLink'] as String?,
      webContentLink: json['webContentLink'] as String?,
      webViewLink: json['webViewLink'] as String?,
      description: json['description'] as String?,
    );
  }

  DriveFile copyWith({
    String? id,
    String? name,
    String? mimeType,
    String? size,
    DateTime? createdTime,
    DateTime? modifiedTime,
    String? thumbnailLink,
    String? iconLink,
    String? webContentLink,
    String? webViewLink,
    String? description,
  }) {
    return DriveFile(
      id: id ?? this.id,
      name: name ?? this.name,
      mimeType: mimeType ?? this.mimeType,
      size: size ?? this.size,
      createdTime: createdTime ?? this.createdTime,
      modifiedTime: modifiedTime ?? this.modifiedTime,
      thumbnailLink: thumbnailLink ?? this.thumbnailLink,
      iconLink: iconLink ?? this.iconLink,
      webContentLink: webContentLink ?? this.webContentLink,
      webViewLink: webViewLink ?? this.webViewLink,
      description: description ?? this.description,
    );
  }
}
