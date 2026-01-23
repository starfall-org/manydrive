import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/formatters.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/presentation/state/drive_state.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:manydrive/features/drive/presentation/widgets/file_info_dialog.dart';
import 'package:manydrive/features/drive/presentation/widgets/file_tile_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/folder_tile_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/share_file_dialog.dart';
import 'package:manydrive/features/drive/presentation/widgets/sort_bottom_sheet.dart';

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
    MiniPlayerController().addListener(_onMiniPlayerUpdate);
  }

  void _onMiniPlayerUpdate() {
    if (mounted) {
      setState(() {});
    }
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

  void _downloadFile(DriveFile file) async {
    await widget.driveState.downloadFile(file);
  }

  void _showMetadata(DriveFile file) {
    showDialog(
      context: context,
      builder: (context) => FileInfoDialog(file: file),
    );
  }

  void _showShareDialog(DriveFile file) {
    showDialog(
      context: context,
      builder: (context) => ShareFileDialog(
        file: file,
        driveState: widget.driveState,
      ),
    );
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
                        file.isFolder
                            ? Icons.folder
                            : getFileIcon(file),
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
      
      // Close loading dialog safely
      if (Navigator.of(context, rootNavigator: true).canPop()) {
        Navigator.of(context, rootNavigator: true).pop();
      }
      
      showSuccessSnackBar(
        context,
        '${file.isFolder ? 'Folder' : 'File'} "${file.name}" deleted successfully',
      );
      widget.driveState.refresh(widget.tabKey);
    } catch (e) {
      if (!mounted) return;
      
      // Close loading dialog safely
      if (Navigator.of(context, rootNavigator: true).canPop()) {
        Navigator.of(context, rootNavigator: true).pop();
      }
      
      showErrorSnackBar(
        context,
        'Failed to delete "${file.name}": ${e.toString().replaceAll('Exception: ', '')}',
      );
    }
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
          (context) => SortBottomSheet(
            currentSortType: _sortType,
            isAscending: _sortAscending,
            onSortSelected: (type, ascending) {
              setState(() {
                _sortType = type;
                _sortAscending = ascending;
              });
            },
          ),
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
            if (file.isFolder) {
              return FolderTile(
                file: file,
                allFiles: files,
                onTap: () => widget.onFileOpen(file, files),
                onDownload: () => _downloadFile(file),
                onShare: () => _showShareDialog(file),
                onInfo: () => _showMetadata(file),
                onDelete: () => _deleteFile(file),
              );
            } else {
              return FileTile(
                file: file,
                allFiles: files,
                isSelected: _selectedFileId == file.id,
                onTap: () {
                  setState(() => _selectedFileId = null);
                  widget.onFileOpen(file, files);
                },
                onDownload: () => _downloadFile(file),
                onShare: () => _showShareDialog(file),
                onInfo: () => _showMetadata(file),
                onDelete: () => _deleteFile(file),
              );
            }
          },
        );
      },
    );
  }

  @override
  void dispose() {
    MiniPlayerController().removeListener(_onMiniPlayerUpdate);
    _scrollController.dispose();
    super.dispose();
  }
}
