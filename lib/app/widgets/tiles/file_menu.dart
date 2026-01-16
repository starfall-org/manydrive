import 'package:flutter/material.dart';
import 'package:manydrive/app/common/snackbar.dart';
import 'package:manydrive/app/models/file_model.dart';
import 'package:manydrive/app/services/gdrive.dart';
import 'package:manydrive/app/services/notification_service.dart';
import 'package:manydrive/app/widgets/dialogs/show_metadata.dart';

class FileMenuWidget extends StatefulWidget {
  final FileModel fileModel;
  final GDrive gds;
  final String tabKey;

  const FileMenuWidget({
    super.key,
    required this.fileModel,
    required this.gds,
    required this.tabKey,
  });

  @override
  State<FileMenuWidget> createState() => _FileMenuState();
}

class _FileMenuState extends State<FileMenuWidget> {
  @override
  Widget build(BuildContext context) {
    return PopupMenuButton(
      itemBuilder:
          (context) => [
            PopupMenuItem(child: Text('Download'), onTap: () => _download()),
            PopupMenuItem(child: Text('Info'), onTap: () => _metadata()),
            PopupMenuItem(child: Text('Delete'), onTap: () => _delete()),
          ],
    );
  }

  void _metadata() {
    try {
      showMetadataDialog(context, widget.fileModel);
    } catch (e) {
      showErrorSnackBar(
        context,
        'Error getting file metadata: $e',
        duration: const Duration(seconds: 5),
      );
    }
  }

  void _delete() {
    try {
      widget.fileModel.delete();
      showSuccessSnackBar(context, 'File deleted successfully');
      widget.gds.refresh(widget.tabKey);
    } catch (e) {
      showErrorSnackBar(
        context,
        'Error deleting file: $e',
        duration: const Duration(seconds: 5),
      );
    }
  }

  void _download() async {
    final notificationService = NotificationService();
    final notificationId = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    final fileName = widget.fileModel.file.name ?? 'file';

    try {
      // Initialize notification service
      await notificationService.initialize();

      // Show starting notification (cannot be dismissed)
      await notificationService.showProgress(
        id: notificationId,
        title: 'Downloading',
        body: fileName,
        progress: 0,
        maxProgress: 100,
      );

      // Perform download
      final _ = await widget.fileModel.download(
        onProgress: (progress) async {
          await notificationService.showProgress(
            id: notificationId,
            title: 'Downloading',
            body: fileName,
            progress: progress,
            maxProgress: 100,
          );
        },
      );

      // Show completion notification (can be dismissed)
      await notificationService.showTransferComplete(
        id: notificationId,
        title: '✅ Download Complete',
        body: 'Downloaded: $fileName',
      );
    } catch (e) {
      // Cancel progress notification
      await notificationService.cancel(notificationId);

      // Show error notification
      await notificationService.showError(
        title: 'Download Failed',
        message: 'Could not download $fileName',
      );
    }
  }
}
