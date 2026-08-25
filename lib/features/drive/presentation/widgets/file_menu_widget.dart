import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';

class FileMenu extends StatelessWidget {
  final DriveFile file;
  final VoidCallback onDownload;
  final VoidCallback onShare;
  final VoidCallback onInfo;
  final VoidCallback onDelete;
  final VoidCallback? onAddToQueue;

  const FileMenu({
    super.key,
    required this.file,
    required this.onDownload,
    required this.onShare,
    required this.onInfo,
    required this.onDelete,
    this.onAddToQueue,
  });

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      itemBuilder:
          (context) => <PopupMenuEntry<String>>[
            const PopupMenuItem<String>(
              value: 'download',
              child: Row(
                children: [
                  Icon(Icons.download, size: 20),
                  SizedBox(width: 12),
                  Text('Download'),
                ],
              ),
            ),
            const PopupMenuItem<String>(
              value: 'share',
              child: Row(
                children: [
                  Icon(Icons.share, size: 20),
                  SizedBox(width: 12),
                  Text('Share'),
                ],
              ),
            ),
            const PopupMenuItem<String>(
              value: 'info',
              child: Row(
                children: [
                  Icon(Icons.info_outline, size: 20),
                  SizedBox(width: 12),
                  Text('Info'),
                ],
              ),
            ),
            if (file.isAudio || file.isVideo)
              const PopupMenuItem<String>(
                value: 'addToQueue',
                child: Row(
                  children: [
                    Icon(Icons.queue_music_rounded, size: 20),
                    SizedBox(width: 12),
                    Text('Thêm vào danh sách phát'),
                  ],
                ),
              ),
            const PopupMenuDivider(),
            PopupMenuItem<String>(
              value: 'delete',
              child: Row(
                children: [
                  Icon(
                    Icons.delete_outline,
                    size: 20,
                    color: Colors.red.shade400,
                  ),
                  const SizedBox(width: 12),
                  Text('Delete', style: TextStyle(color: Colors.red.shade400)),
                ],
              ),
            ),
          ],
      onSelected: (value) {
        switch (value) {
          case 'download':
            onDownload();
            break;
          case 'share':
            onShare();
            break;
          case 'info':
            onInfo();
            break;
          case 'addToQueue':
            if (onAddToQueue != null) {
              onAddToQueue!();
            } else {
              MiniPlayerController().addToQueue(file);
              showSuccessSnackBar(context, 'Đã thêm "${file.name}" vào danh sách phát');
            }
            break;
          case 'delete':
            onDelete();
            break;
        }
      },
    );
  }
}
