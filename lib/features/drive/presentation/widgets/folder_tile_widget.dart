import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/formatters.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/presentation/widgets/file_menu_widget.dart';

class FolderTile extends StatelessWidget {
  final DriveFile file;
  final List<DriveFile> allFiles;
  final VoidCallback onTap;
  final VoidCallback onDownload;
  final VoidCallback onShare;
  final VoidCallback onInfo;
  final VoidCallback onDelete;

  const FolderTile({
    super.key,
    required this.file,
    required this.allFiles,
    required this.onTap,
    required this.onDownload,
    required this.onShare,
    required this.onInfo,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: ListTile(
          leading: const Icon(Icons.folder),
          trailing: FileMenu(
            file: file,
            onDownload: onDownload,
            onShare: onShare,
            onInfo: onInfo,
            onDelete: onDelete,
          ),
          title: Text(file.name),
          subtitle: Text(
            formatDate(file.createdTime),
            style: TextStyle(
              color: Theme.of(
                context,
              ).colorScheme.onSurface.withValues(alpha: 0.5),
              fontSize: 12,
            ),
          ),
          tileColor: Theme.of(context).colorScheme.surfaceContainerHighest,
          onTap: onTap,
        ),
      ),
    );
  }
}
