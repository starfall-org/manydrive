import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/presentation/state/drive_state.dart';

class ShareFileDialog extends StatefulWidget {
  final DriveFile file;
  final DriveState driveState;

  const ShareFileDialog({
    super.key,
    required this.file,
    required this.driveState,
  });

  @override
  State<ShareFileDialog> createState() => _ShareFileDialogState();
}

class _ShareFileDialogState extends State<ShareFileDialog> {
  final _emailController = TextEditingController();
  String _selectedRole = 'reader';
  final _formKey = GlobalKey<FormState>();

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: Row(
        children: [
          const Icon(Icons.share, size: 24),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Share "${widget.file.name}"',
              style: const TextStyle(fontSize: 18),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextFormField(
              controller: _emailController,
              decoration: InputDecoration(
                labelText: 'Email address',
                hintText: 'example@gmail.com',
                prefixIcon: const Icon(Icons.email_outlined),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              keyboardType: TextInputType.emailAddress,
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return 'Please enter an email';
                }
                final emailRegex = RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$');
                if (!emailRegex.hasMatch(value)) {
                  return 'Please enter a valid email';
                }
                return null;
              },
            ),
            const SizedBox(height: 16),
            Text(
              'Permission',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: Theme.of(
                  context,
                ).colorScheme.onSurface.withValues(alpha: 0.7),
              ),
            ),
            const SizedBox(height: 8),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(
                  value: 'reader',
                  label: Text('Viewer'),
                  icon: Icon(Icons.visibility, size: 18),
                ),
                ButtonSegment(
                  value: 'writer',
                  label: Text('Editor'),
                  icon: Icon(Icons.edit, size: 18),
                ),
              ],
              selected: {_selectedRole},
              onSelectionChanged: (Set<String> newSelection) {
                setState(() {
                  _selectedRole = newSelection.first;
                });
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton.icon(
          onPressed: () async {
            if (!_formKey.currentState!.validate()) return;
            Navigator.pop(context);
            _shareFile(
              widget.file,
              _emailController.text.trim(),
              _selectedRole,
            );
          },
          icon: const Icon(Icons.send, size: 18),
          label: const Text('Share'),
        ),
      ],
    );
  }

  Future<void> _shareFile(DriveFile file, String email, String role) async {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(child: CircularProgressIndicator()),
    );

    try {
      await widget.driveState.shareFile(file, email, role: role);
      if (!mounted) return;
      
      // Close loading dialog
      Navigator.of(context, rootNavigator: true).pop();
      
      showSuccessSnackBar(
        context,
        'Shared "${file.name}" with $email as ${role == 'reader' ? 'viewer' : 'editor'}',
      );
    } catch (e) {
      if (!mounted) return;
      
      // Close loading dialog
      Navigator.of(context, rootNavigator: true).pop();
      
      showErrorSnackBar(context, 'Failed to share: $e');
    }
  }
}
