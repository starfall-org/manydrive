import 'package:googleapis/drive/v3.dart' as drive;

import 'package:manydrive/features/drive/domain/entities/drive_file.dart';

/// Data model for DriveFile with serialization capabilities
class DriveFileModel extends DriveFile {
  const DriveFileModel({
    required super.id,
    required super.name,
    super.mimeType,
    super.size,
    super.createdTime,
    super.modifiedTime,
    super.thumbnailLink,
    super.iconLink,
    super.webContentLink,
    super.webViewLink,
    super.description,
  });

  /// Create from Google Drive API File object
  factory DriveFileModel.fromDriveFile(drive.File file) {
    return DriveFileModel(
      id: file.id ?? '',
      name: file.name ?? 'Unnamed',
      mimeType: file.mimeType,
      size: file.size,
      createdTime: file.createdTime,
      modifiedTime: file.modifiedTime,
      thumbnailLink: file.thumbnailLink,
      iconLink: file.iconLink,
      webContentLink: file.webContentLink,
      webViewLink: file.webViewLink,
      description: file.description,
    );
  }

  /// Create from JSON (for caching)
  factory DriveFileModel.fromJson(Map<String, dynamic> json) {
    return DriveFileModel(
      id: json['id'] ?? '',
      name: json['name'] ?? 'Unnamed',
      mimeType: json['mimeType'],
      size: json['size'],
      createdTime:
          json['createdTime'] != null
              ? DateTime.parse(json['createdTime'])
              : null,
      modifiedTime:
          json['modifiedTime'] != null
              ? DateTime.parse(json['modifiedTime'])
              : null,
      thumbnailLink: json['thumbnailLink'],
      iconLink: json['iconLink'],
      webContentLink: json['webContentLink'],
      webViewLink: json['webViewLink'],
      description: json['description'],
    );
  }

  /// Create from domain entity
  factory DriveFileModel.fromEntity(DriveFile entity) {
    return DriveFileModel(
      id: entity.id,
      name: entity.name,
      mimeType: entity.mimeType,
      size: entity.size,
      createdTime: entity.createdTime,
      modifiedTime: entity.modifiedTime,
      thumbnailLink: entity.thumbnailLink,
      iconLink: entity.iconLink,
      webContentLink: entity.webContentLink,
      webViewLink: entity.webViewLink,
      description: entity.description,
    );
  }

  /// Convert to JSON (for caching)
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

  /// Convert to domain entity
  DriveFile toEntity() {
    return DriveFile(
      id: id,
      name: name,
      mimeType: mimeType,
      size: size,
      createdTime: createdTime,
      modifiedTime: modifiedTime,
      thumbnailLink: thumbnailLink,
      iconLink: iconLink,
      webContentLink: webContentLink,
      webViewLink: webViewLink,
      description: description,
    );
  }
}
