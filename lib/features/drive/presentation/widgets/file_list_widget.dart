import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/presentation/state/drive_state.dart';

enum SortType { name, date, size }

class FileListWidget extends StatefulWidget {
  final DriveState driveState;
  final Function(DriveFile, List<DriveFile>) onFileOpen;
  final String tabKey;
  final bool isSharedWithMe;

  const FileListWidget({
    super.key,
    required this.driveState,
    required this.onFileOpen,
    required this.tabKey,
    required this.isSharedWithMe,
  });

  @override
  State<FileListWidget> createState() => FileListWidgetState();
}

class FileListWidgetState extends State<FileListWidget>
    with AutomaticKeepAliveClientMixin {
  SortType _sortType = SortType.name;
  bool _sortAscending = true;
  String? _selectedFileId;
  final ScrollController _scrollController = ScrollController();

  @override
  bool get wantKeepAlive => true;

  void showSortMenu() => _showSortMenu();

  void selectAndScrollToFile(DriveFile file) {
    setState(() {
      _selectedFileId = file.id;
    });
    // Scroll sẽ được thực hiện sau khi build xong
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollToFile(file.id);
    });
  }

  void _scrollToFile(String fileId) {
    // Đợi một chút để đảm bảo list đã build xong
    Future.delayed(const Duration(milliseconds: 100), () {
      if (!mounted || !_scrollController.hasClients) return;

      // Tìm index của file trong list hiện tại
      // Sẽ được tính trong build method
    });
  }

  @override
  void initState() {
    super.initState();
    _loadInitialData();
  }

  Future<void> _loadInitialData() async {
    await Future.delayed(Duration.zero);
    if (!mounted || !widget.driveState.isLoggedIn) return;

    if (widget.isSharedWithMe) {
      await widget.driveState.listFiles(
        sharedWithMe: true,
        tabKey: widget.tabKey,
      );
    } else {
      await widget.driveState.listFiles(tabKey: widget.tabKey);
    }
  }

  Widget _buildFolderTile(DriveFile file, List<DriveFile> allFiles) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: ListTile(
          leading: const Icon(Icons.folder),
          trailing: _buildFileMenu(file),
          title: Text(file.name),
          subtitle: Text(
            _formatDate(file.createdTime),
            style: TextStyle(
              color: Theme.of(
                context,
              ).colorScheme.onSurface.withValues(alpha: 0.5),
              fontSize: 12,
            ),
          ),
          tileColor: Theme.of(context).colorScheme.surfaceContainerHighest,
          onTap: () => widget.onFileOpen(file, allFiles),
        ),
      ),
    );
  }

  Widget _buildFileTile(DriveFile file, List<DriveFile> allFiles) {
    final fileIcon =
        file.isVideo
            ? Icons.video_file
            : file.isAudio
            ? Icons.audiotrack
            : file.isImage
            ? Icons.image
            : Icons.insert_drive_file;

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

    String subtitle = _formatDate(file.modifiedTime ?? file.createdTime);
    if (file.sizeInBytes > 0) {
      subtitle += ' • ${_formatFileSize(file.sizeInBytes)}';
    }

    final isSelected = _selectedFileId == file.id;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color:
            isSelected
                ? Theme.of(
                  context,
                ).colorScheme.primaryContainer.withValues(alpha: 0.3)
                : null,
        border:
            isSelected
                ? Border.all(
                  color: Theme.of(context).colorScheme.primary,
                  width: 2,
                )
                : null,
      ),
      child: ListTile(
        leading: leadingWidget,
        trailing: _buildFileMenu(file),
        title: Text(file.name),
        subtitle: Text(
          subtitle,
          style: TextStyle(
            color: Theme.of(
              context,
            ).colorScheme.onSurface.withValues(alpha: 0.5),
            fontSize: 12,
          ),
        ),
        onTap: () {
          setState(() => _selectedFileId = null);
          widget.onFileOpen(file, allFiles);
        },
      ),
    );
  }

  Widget _buildFileMenu(DriveFile file) {
    return PopupMenuButton<String>(
      itemBuilder:
          (context) => <PopupMenuEntry<String>>[
            PopupMenuItem<String>(
              value: 'download',
              child: const Row(
                children: [
                  Icon(Icons.download, size: 20),
                  SizedBox(width: 12),
                  Text('Download'),
                ],
              ),
            ),
            PopupMenuItem<String>(
              value: 'share',
              child: const Row(
                children: [
                  Icon(Icons.share, size: 20),
                  SizedBox(width: 12),
                  Text('Share'),
                ],
              ),
            ),
            PopupMenuItem<String>(
              value: 'info',
              child: const Row(
                children: [
                  Icon(Icons.info_outline, size: 20),
                  SizedBox(width: 12),
                  Text('Info'),
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
            _downloadFile(file);
            break;
          case 'share':
            _showShareDialog(file);
            break;
          case 'info':
            _showMetadata(file);
            break;
          case 'delete':
            _deleteFile(file);
            break;
        }
      },
    );
  }

  void _downloadFile(DriveFile file) async {
    await widget.driveState.downloadFile(file);
  }

  void _showMetadata(DriveFile file) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
            title: Row(
              children: [
                Icon(
                  file.isFolder ? Icons.folder : _getFileIcon(file),
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
                  _buildInfoTile(
                    icon: Icons.label_outline,
                    label: 'Name',
                    value: file.name,
                    colorScheme: colorScheme,
                  ),
                  _buildInfoTile(
                    icon: Icons.fingerprint,
                    label: 'ID',
                    value: file.id,
                    colorScheme: colorScheme,
                    isMonospace: true,
                  ),
                  _buildInfoTile(
                    icon: Icons.data_usage,
                    label: 'Size',
                    value:
                        file.sizeInBytes > 0
                            ? _formatFileSize(file.sizeInBytes)
                            : 'N/A',
                    colorScheme: colorScheme,
                  ),
                  _buildInfoTile(
                    icon: Icons.category_outlined,
                    label: 'Type',
                    value: _formatMimeType(file.mimeType),
                    colorScheme: colorScheme,
                  ),
                  _buildInfoTile(
                    icon: Icons.calendar_today_outlined,
                    label: 'Created',
                    value: _formatFullDate(file.createdTime),
                    colorScheme: colorScheme,
                  ),
                  _buildInfoTile(
                    icon: Icons.update,
                    label: 'Modified',
                    value: _formatFullDate(file.modifiedTime),
                    colorScheme: colorScheme,
                  ),
                  if (file.description != null && file.description!.isNotEmpty)
                    _buildInfoTile(
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
          ),
    );
  }

  Widget _buildInfoTile({
    required IconData icon,
    required String label,
    required String value,
    required ColorScheme colorScheme,
    bool isMonospace = false,
  }) {
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

  IconData _getFileIcon(DriveFile file) {
    if (file.isVideo) return Icons.video_file;
    if (file.isAudio) return Icons.audiotrack;
    if (file.isImage) return Icons.image;
    return Icons.insert_drive_file;
  }

  String _formatMimeType(String? mimeType) {
    if (mimeType == null) return 'Unknown';
    if (mimeType == 'application/vnd.google-apps.folder') return 'Folder';
    if (mimeType.startsWith('image/')) {
      return 'Image (${mimeType.split('/').last})';
    }
    if (mimeType.startsWith('video/')) {
      return 'Video (${mimeType.split('/').last})';
    }
    if (mimeType.startsWith('audio/')) {
      return 'Audio (${mimeType.split('/').last})';
    }
    if (mimeType.startsWith('text/')) {
      return 'Text (${mimeType.split('/').last})';
    }
    return mimeType.split('/').last;
  }

  String _formatFullDate(DateTime? date) {
    if (date == null) return 'Unknown';
    return '${date.day}/${date.month}/${date.year} ${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  }

  void _showShareDialog(DriveFile file) {
    final emailController = TextEditingController();
    String selectedRole = 'reader';
    final formKey = GlobalKey<FormState>();

    showDialog(
      context: context,
      builder:
          (context) => StatefulBuilder(
            builder:
                (context, setDialogState) => AlertDialog(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  title: Row(
                    children: [
                      const Icon(Icons.share, size: 24),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          'Share "${file.name}"',
                          style: const TextStyle(fontSize: 18),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  content: Form(
                    key: formKey,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        TextFormField(
                          controller: emailController,
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
                            final emailRegex = RegExp(
                              r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$',
                            );
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
                          selected: {selectedRole},
                          onSelectionChanged: (Set<String> newSelection) {
                            setDialogState(() {
                              selectedRole = newSelection.first;
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
                        if (!formKey.currentState!.validate()) return;
                        Navigator.pop(context);
                        await _shareFile(
                          file,
                          emailController.text.trim(),
                          selectedRole,
                        );
                      },
                      icon: const Icon(Icons.send, size: 18),
                      label: const Text('Share'),
                    ),
                  ],
                ),
          ),
    );
  }

  Future<void> _shareFile(DriveFile file, String email, String role) async {
    // Show loading
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(child: CircularProgressIndicator()),
    );

    try {
      await widget.driveState.shareFile(file, email, role: role);
      if (!mounted) return;
      Navigator.pop(context); // Close loading
      showSuccessSnackBar(
        context,
        'Shared "${file.name}" with $email as ${role == 'reader' ? 'viewer' : 'editor'}',
      );
    } catch (e) {
      if (!mounted) return;
      Navigator.pop(context); // Close loading
      showErrorSnackBar(context, 'Failed to share: $e');
    }
  }

  void _deleteFile(DriveFile file) async {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final itemType = file.isFolder ? 'folder' : 'file';

    final confirmed = await showDialog<bool>(
      context: context,
      builder:
          (context) => AlertDialog(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
            icon: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.red.shade50,
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.delete_forever,
                color: Colors.red.shade400,
                size: 32,
              ),
            ),
            title: Text('Delete $itemType?', textAlign: TextAlign.center),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Are you sure you want to delete',
                  style: TextStyle(
                    color: colorScheme.onSurface.withValues(alpha: 0.7),
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: colorScheme.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        file.isFolder ? Icons.folder : _getFileIcon(file),
                        size: 20,
                        color: colorScheme.primary,
                      ),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          file.name,
                          style: const TextStyle(fontWeight: FontWeight.w600),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  'This action cannot be undone.',
                  style: TextStyle(fontSize: 12, color: Colors.red.shade400),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
            actionsAlignment: MainAxisAlignment.center,
            actions: [
              OutlinedButton(
                onPressed: () => Navigator.pop(context, false),
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 12,
                  ),
                ),
                child: const Text('Cancel'),
              ),
              const SizedBox(width: 8),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                style: FilledButton.styleFrom(
                  backgroundColor: Colors.red.shade400,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 12,
                  ),
                ),
                child: const Text('Delete'),
              ),
            ],
          ),
    );

    if (confirmed != true || !mounted) return;

    // Show loading indicator
    showDialog(
      context: context,
      barrierDismissible: false,
      builder:
          (context) => Center(
            child: Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: colorScheme.surface,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const CircularProgressIndicator(),
                  const SizedBox(height: 16),
                  Text(
                    'Deleting...',
                    style: TextStyle(color: colorScheme.onSurface),
                  ),
                ],
              ),
            ),
          ),
    );

    try {
      await widget.driveState.deleteFile(file);
      if (!mounted) return;
      Navigator.pop(context); // Close loading
      showSuccessSnackBar(
        context,
        '${file.isFolder ? 'Folder' : 'File'} "${file.name}" deleted successfully',
      );
      widget.driveState.refresh(widget.tabKey);
    } catch (e) {
      if (!mounted) return;
      Navigator.pop(context); // Close loading
      showErrorSnackBar(
        context,
        'Failed to delete "${file.name}": ${e.toString().replaceAll('Exception: ', '')}',
      );
    }
  }

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
    } else if (difference.inDays < 30) {
      return '${(difference.inDays / 7).floor()}w ago';
    } else if (difference.inDays < 365) {
      return '${(difference.inDays / 30).floor()}mo ago';
    } else {
      return '${(difference.inDays / 365).floor()}y ago';
    }
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  List<DriveFile> _sortFiles(List<DriveFile> files) {
    final sorted = List<DriveFile>.from(files);

    final folders = sorted.where((f) => f.isFolder).toList();
    final regularFiles = sorted.where((f) => !f.isFolder).toList();

    _sortFileList(folders);
    _sortFileList(regularFiles);

    return [...folders, ...regularFiles];
  }

  void _sortFileList(List<DriveFile> files) {
    switch (_sortType) {
      case SortType.name:
        files.sort((a, b) {
          final comparison = a.name.toLowerCase().compareTo(
            b.name.toLowerCase(),
          );
          return _sortAscending ? comparison : -comparison;
        });
        break;
      case SortType.date:
        files.sort((a, b) {
          final dateA = a.modifiedTime ?? a.createdTime ?? DateTime(1970);
          final dateB = b.modifiedTime ?? b.createdTime ?? DateTime(1970);
          final comparison = dateA.compareTo(dateB);
          return _sortAscending ? comparison : -comparison;
        });
        break;
      case SortType.size:
        files.sort((a, b) {
          final comparison = a.sizeInBytes.compareTo(b.sizeInBytes);
          return _sortAscending ? comparison : -comparison;
        });
        break;
    }
  }

  void _showSortMenu() {
    showModalBottomSheet(
      context: context,
      builder:
          (context) => SafeArea(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildSortOption(
                  SortType.name,
                  Icons.sort_by_alpha,
                  'Sort by Name',
                ),
                _buildSortOption(
                  SortType.date,
                  Icons.access_time,
                  'Sort by Date',
                ),
                _buildSortOption(
                  SortType.size,
                  Icons.data_usage,
                  'Sort by Size',
                ),
              ],
            ),
          ),
    );
  }

  Widget _buildSortOption(SortType type, IconData icon, String title) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      trailing:
          _sortType == type
              ? Icon(_sortAscending ? Icons.arrow_upward : Icons.arrow_downward)
              : null,
      onTap: () {
        setState(() {
          if (_sortType == type) {
            _sortAscending = !_sortAscending;
          } else {
            _sortType = type;
            _sortAscending = type == SortType.name;
          }
        });
        Navigator.pop(context);
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return StreamBuilder<List<DriveFile>>(
      stream: widget.driveState.getFilesStream(widget.tabKey),
      initialData: const [],
      builder: (context, snapshot) {
        final files = _sortFiles(snapshot.data ?? []);

        if (files.isEmpty) {
          return Center(
            child: Text(
              'No files',
              style: TextStyle(
                color: Theme.of(
                  context,
                ).colorScheme.onSurface.withValues(alpha: 0.5),
              ),
            ),
          );
        }

        // Scroll đến file được select nếu có
        if (_selectedFileId != null) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            final index = files.indexWhere((f) => f.id == _selectedFileId);
            if (index != -1 && _scrollController.hasClients) {
              final position = index * 72.0; // Ước tính chiều cao mỗi item
              _scrollController.animateTo(
                position,
                duration: const Duration(milliseconds: 500),
                curve: Curves.easeInOut,
              );
              // Xóa highlight sau 2 giây
              Future.delayed(const Duration(seconds: 2), () {
                if (mounted) {
                  setState(() => _selectedFileId = null);
                }
              });
            }
          });
        }

        return ListView.builder(
          controller: _scrollController,
          itemCount: files.length,
          itemBuilder: (context, index) {
            final file = files[index];
            return file.isFolder
                ? _buildFolderTile(file, files)
                : _buildFileTile(file, files);
          },
        );
      },
    );
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }
}
