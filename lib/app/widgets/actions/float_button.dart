import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import 'package:manydrive/app/services/gdrive.dart';
import 'package:manydrive/app/services/notification_service.dart';

class FloatButtons extends StatefulWidget {
  final GDrive gds;
  final String tabKey;

  const FloatButtons({super.key, required this.gds, required this.tabKey});

  @override
  State<FloatButtons> createState() => _FloatButtonsState();
}

class _FloatButtonsState extends State<FloatButtons> {
  Future<void> _uploadFile() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles();
    if (result != null) {
      String filePath = result.files.single.path ?? '';
      if (filePath.isNotEmpty) {
        final notificationService = NotificationService();
        final notificationId = DateTime.now().millisecondsSinceEpoch ~/ 1000;
        final fileName = result.files.single.name;
        final fileSize = result.files.single.size;

        try {
          // Initialize notification service
          await notificationService.initialize();

          // Show starting notification (cannot be dismissed)
          await notificationService.showProgress(
            id: notificationId,
            title: 'Uploading',
            body: fileName,
            progress: 0,
            maxProgress: 100,
          );

          int uploadedBytes = 0;

          // Perform upload with progress
          await widget.gds.upload(
            filePath,
            widget.tabKey,
            onProgress: (bytes) async {
              uploadedBytes += bytes;
              final progress = ((uploadedBytes / fileSize) * 100).round();
              await notificationService.showProgress(
                id: notificationId,
                title: 'Uploading',
                body: fileName,
                progress: progress,
                maxProgress: 100,
              );
            },
          );

          // Show completion notification (can be dismissed)
          await notificationService.showTransferComplete(
            id: notificationId,
            title: '✅ Upload Complete',
            body: 'Uploaded: $fileName',
          );

          // Refresh file list
          widget.gds.refresh(widget.tabKey);
        } catch (e) {
          // Cancel progress notification
          await notificationService.cancel(notificationId);

          // Show error notification
          await notificationService.showError(
            title: 'Upload Failed',
            message: 'Could not upload $fileName',
          );
        }
      }
    }
  }

  Future<void> _createFolder() async {
    TextEditingController folderController = TextEditingController();
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text("Create Folder"),
          content: TextField(
            controller: folderController,
            decoration: const InputDecoration(hintText: "Enter folder name"),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text("Cancel"),
            ),
            TextButton(
              onPressed: () async {
                if (folderController.text.isNotEmpty) {
                  await widget.gds.mkdir(folderController.text, widget.tabKey);
                  widget.gds.refresh(widget.tabKey);
                  Navigator.pop(context);
                }
              },
              child: const Text("Create"),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        FloatingActionButton(
          onPressed: _createFolder,
          tooltip: 'Create Folder',
          child: const Icon(Icons.create_new_folder),
        ),
        const SizedBox(height: 10),
        FloatingActionButton(
          onPressed: _uploadFile,
          tooltip: 'Upload File',
          child: const Icon(Icons.upload_file),
        ),
      ],
    );
  }
}
