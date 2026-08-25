import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/services/upload_manager.dart';
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

  Future<void> _uploadFiles(BuildContext context) async {
    _toggle();
    final result = await FilePicker.platform.pickFiles(allowMultiple: true);
    if (result == null || result.files.isEmpty) return;

    final validFiles = result.files.where((f) => f.path != null && f.path!.isNotEmpty).toList();
    if (validFiles.isEmpty) return;

    final paths = validFiles.map((f) => f.path!).toList();
    final names = validFiles.map((f) => f.name).toList();
    final sizes = validFiles.map((f) => f.size).toList();

    await UploadManager().startUploads(
      filePaths: paths,
      fileNames: names,
      fileSizes: sizes,
      driveState: widget.driveState,
      tabKey: widget.tabKey,
    );
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
                onPressed: () => _uploadFiles(context),
                tooltip: 'Upload Files',
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
