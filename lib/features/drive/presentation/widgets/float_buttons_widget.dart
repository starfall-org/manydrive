import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import 'package:manydrive/core/services/notification_service.dart';
import 'package:manydrive/features/drive/presentation/state/drive_state.dart';

class FloatButtonsWidget extends StatefulWidget {
  final DriveState driveState;
  final String tabKey;

  const FloatButtonsWidget({
    super.key,
    required this.driveState,
    required this.tabKey,
  });

  @override
  State<FloatButtonsWidget> createState() => _FloatButtonsWidgetState();
}

class _FloatButtonsWidgetState extends State<FloatButtonsWidget>
    with SingleTickerProviderStateMixin {
  bool _isExpanded = false;
  late AnimationController _animationController;
  late Animation<double> _expandAnimation;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
    );
    _expandAnimation = CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeInOut,
    );
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  void _toggle() {
    setState(() {
      _isExpanded = !_isExpanded;
      if (_isExpanded) {
        _animationController.forward();
      } else {
        _animationController.reverse();
      }
    });
  }

  Future<void> _uploadFile(BuildContext context) async {
    _toggle();
    final result = await FilePicker.platform.pickFiles();
    if (result == null) return;

    final filePath = result.files.single.path ?? '';
    if (filePath.isEmpty) return;

    final notificationService = NotificationService();
    final notificationId = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    final fileName = result.files.single.name;
    final fileSize = result.files.single.size;

    try {
      await notificationService.initialize();
      await notificationService.showProgress(
        id: notificationId,
        title: 'Uploading',
        body: fileName,
        progress: 0,
        maxProgress: 100,
      );

      int uploadedBytes = 0;

      await widget.driveState.uploadFile(
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

      await notificationService.showTransferComplete(
        id: notificationId,
        title: '✅ Upload Complete',
        body: 'Uploaded: $fileName',
      );

      widget.driveState.refresh(widget.tabKey);
    } catch (e) {
      await notificationService.cancel(notificationId);
      await notificationService.showError(
        title: 'Upload Failed',
        message: 'Could not upload $fileName',
      );
    }
  }

  Future<void> _createFolder(BuildContext context) async {
    _toggle();
    final controller = TextEditingController();

    await showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text("Create Folder"),
            content: TextField(
              controller: controller,
              decoration: const InputDecoration(hintText: "Enter folder name"),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text("Cancel"),
              ),
              TextButton(
                onPressed: () async {
                  if (controller.text.isNotEmpty) {
                    await widget.driveState.createFolder(
                      controller.text,
                      widget.tabKey,
                    );
                    widget.driveState.refresh(widget.tabKey);
                    Navigator.pop(context);
                  }
                },
                child: const Text("Create"),
              ),
            ],
          ),
    );

    controller.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        ScaleTransition(
          scale: _expandAnimation,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              FloatingActionButton.small(
                heroTag: 'create_folder',
                onPressed: () => _createFolder(context),
                tooltip: 'Create Folder',
                child: const Icon(Icons.create_new_folder),
              ),
              const SizedBox(height: 10),
              FloatingActionButton.small(
                heroTag: 'upload_file',
                onPressed: () => _uploadFile(context),
                tooltip: 'Upload File',
                child: const Icon(Icons.upload_file),
              ),
              const SizedBox(height: 10),
            ],
          ),
        ),
        FloatingActionButton(
          heroTag: 'menu_fab',
          onPressed: _toggle,
          child: AnimatedRotation(
            turns: _isExpanded ? 0.125 : 0,
            duration: const Duration(milliseconds: 200),
            child: const Icon(Icons.add),
          ),
        ),
      ],
    );
  }
}
