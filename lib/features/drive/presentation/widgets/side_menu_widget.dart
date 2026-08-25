import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/domain/entities/credential.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';
import 'package:manydrive/features/drive/presentation/dialogs/about_dialog.dart';
import 'package:manydrive/features/drive/presentation/dialogs/login_dialog.dart';
import 'package:manydrive/features/drive/presentation/dialogs/settings_dialog.dart';
import 'package:manydrive/injection_container.dart';

class SideMenuWidget extends StatefulWidget {
  final CredentialRepository credentialRepository;
  final Function(String) onLogin;
  final ThemeMode themeMode;
  final Function(ThemeMode) onThemeModeChanged;
  final bool isSuperDarkMode;
  final Function(bool) onSuperDarkModeChanged;
  final bool isDynamicColor;
  final Function(bool) onDynamicColorChanged;
  final VoidCallback? onOpenTrash;

  const SideMenuWidget({
    super.key,
    required this.credentialRepository,
    required this.onLogin,
    required this.themeMode,
    required this.onThemeModeChanged,
    required this.isSuperDarkMode,
    required this.onSuperDarkModeChanged,
    required this.isDynamicColor,
    required this.onDynamicColorChanged,
    this.onOpenTrash,
  });

  @override
  State<SideMenuWidget> createState() => _SideMenuWidgetState();
}

class _SideMenuWidgetState extends State<SideMenuWidget> {
  String? selectedClientEmail;
  List<Credential> credentials = [];
  String? _customHeaderImagePath;

  @override
  void initState() {
    super.initState();
    _loadCredentials();
    _loadHeaderImage();
  }

  Future<void> _loadHeaderImage() async {
    final imagePath = injector.settingsService.sidebarHeaderImage;
    setState(() {
      _customHeaderImagePath = imagePath;
    });
  }

  Future<void> _loadCredentials() async {
    selectedClientEmail = await widget.credentialRepository.getSelectedEmail();
    final credList = await widget.credentialRepository.listCredentials();
    setState(() {
      credentials = credList;
    });
  }

  void _addAccount(String identifier) async {
    await widget.credentialRepository.setSelectedEmail(identifier);
    await _loadCredentials();
    setState(() {
      selectedClientEmail = identifier;
    });
  }

  void _deleteAccount(String identifier) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('Xóa tài khoản'),
            content: Text(
              'Bạn có chắc chắn muốn xóa tài khoản "$identifier" khỏi ứng dụng?',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Hủy'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                style: TextButton.styleFrom(foregroundColor: Colors.red),
                child: const Text('Xóa'),
              ),
            ],
          ),
    );

    if (confirmed == true) {
      await widget.credentialRepository.deleteCredential(identifier);
      final newSelected = await widget.credentialRepository.getSelectedEmail();
      await _loadCredentials();
      if (newSelected != null) {
        widget.onLogin(newSelected);
      }
    }
  }

  Future<void> _pickHeaderImage() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.image,
      );

      if (result != null && result.files.single.path != null) {
        final path = result.files.single.path!;
        await injector.settingsService.setSidebarHeaderImage(path);
        setState(() {
          _customHeaderImagePath = path;
        });
      }
    } catch (e) {
      debugPrint('Error picking header image: $e');
    }
  }

  Future<void> _resetHeaderImage() async {
    await injector.settingsService.setSidebarHeaderImage(null);
    setState(() {
      _customHeaderImagePath = null;
    });
  }

  ImageProvider _getHeaderImage() {
    if (_customHeaderImagePath != null && _customHeaderImagePath!.isNotEmpty) {
      final file = File(_customHeaderImagePath!);
      if (file.existsSync()) {
        return FileImage(file);
      }
    }
    return const AssetImage('assets/background.png');
  }

  void _showAccountSelectionModal() {
    final uniqueIdentifiers =
        credentials
            .map((c) => c.clientEmail ?? c.s3Endpoint ?? 'unknown')
            .toSet()
            .toList();

    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                child: Row(
                  children: [
                    const Icon(Icons.manage_accounts_rounded),
                    const SizedBox(width: 10),
                    Text(
                      'Danh sách tài khoản',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                  ],
                ),
              ),
              const Divider(),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: uniqueIdentifiers.length,
                  itemBuilder: (context, index) {
                    final id = uniqueIdentifiers[index];
                    final cred = credentials.firstWhere(
                      (c) => (c.clientEmail ?? c.s3Endpoint) == id,
                    );
                    final isSelected = id == selectedClientEmail;

                    IconData accountIcon = Icons.account_circle;
                    if (cred.isS3) {
                      accountIcon = Icons.cloud_queue;
                    } else if (cred.isServiceAccount) {
                      accountIcon = Icons.key_outlined;
                    }

                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor: isSelected
                            ? Theme.of(context).colorScheme.primary
                            : Theme.of(context).colorScheme.surfaceContainerHighest,
                        foregroundColor: isSelected
                            ? Theme.of(context).colorScheme.onPrimary
                            : Theme.of(context).colorScheme.onSurfaceVariant,
                        child: Icon(accountIcon, size: 20),
                      ),
                      title: Text(
                        cred.username,
                        style: TextStyle(
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                        ),
                      ),
                      subtitle: Text(
                        id,
                        style: const TextStyle(fontSize: 12),
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (isSelected)
                            Icon(
                              Icons.check_circle_rounded,
                              color: Theme.of(context).colorScheme.primary,
                            ),
                          if (uniqueIdentifiers.length > 1)
                            IconButton(
                              icon: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 20),
                              onPressed: () {
                                Navigator.pop(context);
                                _deleteAccount(id);
                              },
                            ),
                        ],
                      ),
                      onTap: () {
                        Navigator.pop(context);
                        if (!isSelected) {
                          setState(() {
                            selectedClientEmail = id;
                          });
                          widget.credentialRepository.setSelectedEmail(id);
                          widget.onLogin(id);
                        }
                      },
                    );
                  },
                ),
              ),
              const Divider(),
              ListTile(
                leading: Icon(Icons.add_circle_outline, color: Theme.of(context).colorScheme.primary),
                title: Text(
                  'Thêm tài khoản mới',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                onTap: () {
                  Navigator.pop(context);
                  showLoginDialog(context, widget.credentialRepository, (identifier) {
                    _addAccount(identifier);
                    widget.onLogin(identifier);
                  });
                },
              ),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final uniqueIdentifiers =
        credentials
            .map((c) => c.clientEmail ?? c.s3Endpoint ?? 'unknown')
            .toSet()
            .toList();

    if (!uniqueIdentifiers.contains(selectedClientEmail) &&
        uniqueIdentifiers.isNotEmpty) {
      selectedClientEmail = uniqueIdentifiers.first;
    }

    final currentCred = credentials.firstWhere(
      (c) => (c.clientEmail ?? c.s3Endpoint) == selectedClientEmail,
      orElse: () => const Credential(rawData: {}),
    );

    return Drawer(
      child: Column(
        children: [
          Stack(
            children: [
              UserAccountsDrawerHeader(
                margin: EdgeInsets.zero,
                accountName: const Text(
                  "ManyDrive",
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                ),
                accountEmail: InkWell(
                  onTap: _showAccountSelectionModal,
                  borderRadius: BorderRadius.circular(8),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 2),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Flexible(
                          child: Text(
                            selectedClientEmail ?? "Chưa chọn tài khoản",
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                              fontWeight: FontWeight.w500,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 4),
                        const Icon(Icons.arrow_drop_down, color: Colors.white, size: 20),
                      ],
                    ),
                  ),
                ),
                currentAccountPicture: CircleAvatar(
                  backgroundColor: Colors.white.withValues(alpha: 0.9),
                  child: Icon(
                    currentCred.isS3
                        ? Icons.cloud_outlined
                        : currentCred.isServiceAccount
                        ? Icons.key
                        : Icons.account_circle,
                    size: 36,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ),
                decoration: BoxDecoration(
                  image: DecorationImage(
                    image: _getHeaderImage(),
                    fit: BoxFit.cover,
                  ),
                ),
              ),
              Positioned(
                top: 36,
                right: 8,
                child: PopupMenuButton<String>(
                  icon: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.4),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.photo_camera_outlined, color: Colors.white, size: 18),
                  ),
                  tooltip: 'Đổi ảnh bìa',
                  onSelected: (val) {
                    if (val == 'pick') {
                      _pickHeaderImage();
                    } else if (val == 'reset') {
                      _resetHeaderImage();
                    }
                  },
                  itemBuilder: (ctx) => [
                    const PopupMenuItem(
                      value: 'pick',
                      child: Row(
                        children: [
                          Icon(Icons.image, size: 18),
                          SizedBox(width: 8),
                          Text('Chọn ảnh bìa từ máy'),
                        ],
                      ),
                    ),
                    if (_customHeaderImagePath != null)
                      const PopupMenuItem(
                        value: 'reset',
                        child: Row(
                          children: [
                            Icon(Icons.restore, size: 18, color: Colors.redAccent),
                            SizedBox(width: 8),
                            Text('Khôi phục ảnh mặc định', style: TextStyle(color: Colors.redAccent)),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
          ListTile(
            leading: const Icon(Icons.delete_outline),
            title: const Text("Thùng rác (Trash)"),
            onTap: () {
              Navigator.pop(context);
              widget.onOpenTrash?.call();
            },
          ),
          ListTile(
            leading: const Icon(Icons.settings_outlined),
            title: const Text("Cài đặt"),
            onTap: () {
              Navigator.pop(context);
              showSettingsDialog(
                context,
                themeMode: widget.themeMode,
                superDarkMode: widget.isSuperDarkMode,
                dynamicColor: widget.isDynamicColor,
                onThemeModeChanged: widget.onThemeModeChanged,
                onSuperDarkModeChanged: widget.onSuperDarkModeChanged,
                onDynamicColorChanged: widget.onDynamicColorChanged,
              );
            },
          ),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text("Giới thiệu"),
            onTap: () {
              Navigator.pop(context);
              showAboutAppDialog(context);
            },
          ),
        ],
      ),
    );
  }
}
