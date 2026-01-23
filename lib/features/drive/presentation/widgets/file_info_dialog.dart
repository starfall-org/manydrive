import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/formatters.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';

class FileInfoDialog extends StatelessWidget {
  final DriveFile file;

  const FileInfoDialog({super.key, required this.file});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: Row(
        children: [
          Icon(
            file.isFolder ? Icons.folder : getFileIcon(file),
            color: colorScheme.primary,
            size: 28,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'File Info',
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            InfoTile(
              icon: Icons.label_outline,
              label: 'Name',
              value: file.name,
              colorScheme: colorScheme,
            ),
            InfoTile(
              icon: Icons.fingerprint,
              label: 'ID',
              value: file.id,
              colorScheme: colorScheme,
              isMonospace: true,
            ),
            InfoTile(
              icon: Icons.data_usage,
              label: 'Size',
              value:
                  file.sizeInBytes > 0 ? formatFileSize(file.sizeInBytes) : 'N/A',
              colorScheme: colorScheme,
            ),
            InfoTile(
              icon: Icons.category_outlined,
              label: 'Type',
              value: formatMimeType(file.mimeType),
              colorScheme: colorScheme,
            ),
            InfoTile(
              icon: Icons.calendar_today_outlined,
              label: 'Created',
              value: formatFullDate(file.createdTime),
              colorScheme: colorScheme,
            ),
            InfoTile(
              icon: Icons.update,
              label: 'Modified',
              value: formatFullDate(file.modifiedTime),
              colorScheme: colorScheme,
            ),
            if (file.description != null && file.description!.isNotEmpty)
              InfoTile(
                icon: Icons.description_outlined,
                label: 'Description',
                value: file.description!,
                colorScheme: colorScheme,
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close'),
        ),
      ],
    );
  }
}

class InfoTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final ColorScheme colorScheme;
  final bool isMonospace;

  const InfoTile({
    super.key,
    required this.icon,
    required this.label,
    required this.value,
    required this.colorScheme,
    this.isMonospace = false,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            icon,
            size: 20,
            color: colorScheme.onSurface.withValues(alpha: 0.6),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 12,
                    color: colorScheme.onSurface.withValues(alpha: 0.6),
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 2),
                SelectableText(
                  value,
                  style: TextStyle(
                    fontSize: 14,
                    fontFamily: isMonospace ? 'monospace' : null,
                    color: colorScheme.onSurface,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
