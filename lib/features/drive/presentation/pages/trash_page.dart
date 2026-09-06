import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/formatters.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';

class TrashPage extends StatefulWidget {
  final DriveRepository driveRepository;

  const TrashPage({super.key, required this.driveRepository});

  @override
  State<TrashPage> createState() => _TrashPageState();
}

class _TrashPageState extends State<TrashPage> {
  List<DriveFile> _trashedFiles = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadTrashedFiles();
  }

  Future<void> _loadTrashedFiles() async {
    setState(() => _isLoading = true);
    try {
      final files = await widget.driveRepository.listFiles(trashed: true);
      if (!mounted) return;
      setState(() {
        _trashedFiles = files;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _isLoading = false);
    }
  }

  // Differs from core formatDate: falls back to absolute date after 7 days
  String _formatDate(DateTime? date) {
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
    }
    return '${date.day}/${date.month}/${date.year}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Trash'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadTrashedFiles,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _trashedFiles.isEmpty
              ? Center(
                  child: Text(
                    'Trash is empty',
                    style: TextStyle(
                      color: Theme.of(
                        context,
                      ).colorScheme.onSurface.withValues(alpha: 0.5),
                    ),
                  ),
                )
              : ListView.builder(
                  itemCount: _trashedFiles.length,
                  itemBuilder: (context, index) {
                    final file = _trashedFiles[index];
                    return ListTile(
                      leading: Icon(
                        file.isFolder ? Icons.folder : getFileIcon(file),
                      ),
                      title: Text(file.name),
                      subtitle: Text(
                        'Deleted ${_formatDate(file.modifiedTime)}',
                        style: TextStyle(
                          color: Theme.of(
                            context,
                          ).colorScheme.onSurface.withValues(alpha: 0.5),
                          fontSize: 12,
                        ),
                      ),
                    );
                  },
                ),
    );
  }
}
