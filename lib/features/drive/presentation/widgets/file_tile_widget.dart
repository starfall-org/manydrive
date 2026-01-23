import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/formatters.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:manydrive/features/drive/presentation/widgets/file_menu_widget.dart';

class FileTile extends StatelessWidget {
  final DriveFile file;
  final List<DriveFile> allFiles;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback onDownload;
  final VoidCallback onShare;
  final VoidCallback onInfo;
  final VoidCallback onDelete;

  const FileTile({
    super.key,
    required this.file,
    required this.allFiles,
    required this.isSelected,
    required this.onTap,
    required this.onDownload,
    required this.onShare,
    required this.onInfo,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final fileIcon = getFileIcon(file);

    Widget leadingWidget;
    if (file.thumbnailLink != null && file.thumbnailLink!.isNotEmpty) {
      leadingWidget = ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: CachedNetworkImage(
          imageUrl: file.thumbnailLink!,
          width: 40,
          height: 40,
          fit: BoxFit.cover,
          placeholder:
              (context, url) => Container(
                width: 40,
                height: 40,
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                child: Icon(fileIcon, size: 20),
              ),
          errorWidget: (context, url, error) => Icon(fileIcon),
        ),
      );
    } else {
      leadingWidget = Icon(fileIcon);
    }

    String subtitle = formatDate(file.modifiedTime ?? file.createdTime);
    if (file.sizeInBytes > 0) {
      subtitle += ' • ${formatFileSize(file.sizeInBytes)}';
    }

    final isPlaying =
        MiniPlayerController().isShowing &&
        MiniPlayerController().currentFile?.id == file.id;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color:
            isPlaying
                ? Theme.of(
                  context,
                ).colorScheme.primaryContainer.withValues(alpha: 0.5)
                : isSelected
                ? Theme.of(
                  context,
                ).colorScheme.primaryContainer.withValues(alpha: 0.3)
                : null,
        border:
            isPlaying || isSelected
                ? Border.all(
                  color: Theme.of(context).colorScheme.primary,
                  width: 2,
                )
                : null,
      ),
      child: ListTile(
        leading: Stack(
          alignment: Alignment.center,
          children: [
            leadingWidget,
            if (isPlaying)
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: Colors.black26,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Icon(
                  Icons.play_circle_fill,
                  color: Colors.white,
                  size: 24,
                ),
              ),
          ],
        ),
        trailing: FileMenu(
          file: file,
          onDownload: onDownload,
          onShare: onShare,
          onInfo: onInfo,
          onDelete: onDelete,
        ),
        title: Text(
          file.name,
          style: TextStyle(
            fontWeight: isPlaying ? FontWeight.bold : FontWeight.normal,
            color: isPlaying ? Theme.of(context).colorScheme.primary : null,
          ),
        ),
        subtitle: Text(
          subtitle,
          style: TextStyle(
            color: Theme.of(
              context,
            ).colorScheme.onSurface.withValues(alpha: 0.5),
            fontSize: 12,
          ),
        ),
        onTap: onTap,
      ),
    );
  }
}
