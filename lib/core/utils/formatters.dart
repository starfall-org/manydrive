import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';

IconData getFileIcon(DriveFile file) {
  if (file.isVideo) return Icons.video_file;
  if (file.isAudio) return Icons.audiotrack;
  if (file.isImage) return Icons.image;
  return Icons.insert_drive_file;
}

String formatMimeType(String? mimeType) {
  if (mimeType == null) return 'Unknown';
  if (mimeType == 'application/vnd.google-apps.folder') return 'Folder';
  if (mimeType.startsWith('image/')) {
    return 'Image (${mimeType.split('/').last})';
  }
  if (mimeType.startsWith('video/')) {
    return 'Video (${mimeType.split('/').last})';
  }
  if (mimeType.startsWith('audio/')) {
    return 'Audio (${mimeType.split('/').last})';
  }
  if (mimeType.startsWith('text/')) {
    return 'Text (${mimeType.split('/').last})';
  }
  return mimeType.split('/').last;
}

String formatFullDate(DateTime? date) {
  if (date == null) return 'Unknown';
  return '${date.day}/${date.month}/${date.year} ${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
}

String formatDate(DateTime? date) {
  if (date == null) return 'Unknown date';

  final now = DateTime.now();
  final difference = now.difference(date);

  if (difference.inDays == 0) {
    if (difference.inHours == 0) {
      if (difference.inMinutes == 0) return 'Just now';
      return '${difference.inMinutes}m ago';
    }
    return '${difference.inHours}h ago';
  } else if (difference.inDays < 7) {
    return '${difference.inDays}d ago';
  } else if (difference.inDays < 30) {
    return '${(difference.inDays / 7).floor()}w ago';
  } else if (difference.inDays < 365) {
    return '${(difference.inDays / 30).floor()}mo ago';
  } else {
    return '${(difference.inDays / 365).floor()}y ago';
  }
}

String formatFileSize(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  if (bytes < 1024 * 1024 * 1024) {
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
  return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
}
