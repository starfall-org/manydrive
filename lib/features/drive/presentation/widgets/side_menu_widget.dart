import 'dart:io';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:manydrive/core/utils/constants.dart';
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
  List<Credential> credentials = [];
  String? selectedClientEmail;
  String? _customHeaderImagePath;

  @override
  void initState() {
    super.initState();
    _loadCredentials();
    _loadCustomHeaderImage();
  }

  Future<void> _loadCustomHeaderImage() async {
    final path = injector.settingsService.sidebarHeaderImage;
    if (mounted) {
      setState(() {
        _customHeaderImagePath = path;
      });
    }
  }

  Future<void> _loadCredentials() async {
    final list = await widget.credentialRepository.listCredentials();
    final selected = await widget.credentialRepository.getSelectedEmail();

    if (mounted) {
      setState(() {
        credentials = list;
        selectedClientEmail = selected;
      });
    }
  }

  void _switchAccount(String identifier) async {
    await widget.credentialRepository.setSelectedEmail(identifier);
    widget.onLogin(identifier);
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
      try {
        final googleSignIn = GoogleSignIn(scopes: kGoogleSignInScopes);
        await googleSignIn.signOut();
        await googleSignIn.disconnect();
      } catch (_) {}

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
      builder: (context) {
        final colorScheme = Theme.of(context).colorScheme;

        return Container(
          padding: const EdgeInsets.symmetric(vertical: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Chọn tài khoản',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.pop(context),
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
                    final identifier = uniqueIdentifiers[index];
                    final isSelected = identifier == selectedClientEmail;
                    final cred = credentials.firstWhere(
                      (c) => (c.clientEmail ?? c.s3Endpoint) == identifier,
                    );

                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor:
                            isSelected
                                ? colorScheme.primaryContainer
                                : colorScheme.surfaceContainerHighest,
                        backgroundImage:
                            cred.avatarUrl != null
                                ? CachedNetworkImageProvider(cred.avatarUrl!)
                                : null,
                        child:
                            cred.avatarUrl == null
                                ? Icon(
                                  cred.isS3
                                      ? Icons.cloud
                                      : cred.isServiceAccount
                                      ? Icons.key
                                      : Icons.person,
                                  color:
                                      isSelected
                                          ? colorScheme.onPrimaryContainer
                                          : colorScheme.onSurfaceVariant,
                                )
                                : null,
                      ),
                      title: Text(
                        cred.displayName ?? identifier,
                        style: TextStyle(
                          fontWeight:
                              isSelected ? FontWeight.bold : FontWeight.normal,
                        ),
                      ),
                      subtitle: cred.displayName != null ? Text(identifier) : null,
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (isSelected)
                            Icon(Icons.check_circle, color: colorScheme.primary),
                          IconButton(
                            icon: const Icon(Icons.delete_outline, color: Colors.red),
                            onPressed: () {
                              Navigator.pop(context);
                              _deleteAccount(identifier);
                            },
                          ),
                        ],
                      ),
                      onTap: () {
                        Navigator.pop(context);
                        if (!isSelected) {
                          _switchAccount(identifier);
                        }
                      },
                    );
                  },
                ),
              ),
              const Divider(),
              ListTile(
                leading: const Icon(Icons.add_circle_outline),
                title: const Text('Thêm tài khoản mới'),
                onTap: () {
                  Navigator.pop(context);
                  showLoginDialog(
                    context,
                    widget.credentialRepository,
                    widget.onLogin,
                  );
                },
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    final currentCred = credentials.cast<Credential?>().firstWhere(
      (c) => (c?.clientEmail ?? c?.s3Endpoint) == selectedClientEmail,
      orElse: () => null,
    );

    return Drawer(
      child: Column(
        children: [
          UserAccountsDrawerHeader(
            decoration: BoxDecoration(
              image: DecorationImage(
                image: _getHeaderImage(),
                fit: BoxFit.cover,
                colorFilter: ColorFilter.mode(
                  Colors.black.withValues(alpha: 0.4),
                  BlendMode.darken,
                ),
              ),
            ),
            currentAccountPicture: Stack(
              children: [
                CircleAvatar(
                  radius: 36,
                  backgroundColor: colorScheme.primaryContainer,
                  backgroundImage:
                      currentCred?.avatarUrl != null
                          ? CachedNetworkImageProvider(currentCred!.avatarUrl!)
                          : null,
                  child:
                      currentCred?.avatarUrl == null
                          ? Icon(
                            currentCred?.isS3 == true
                                ? Icons.cloud
                                : currentCred?.isServiceAccount == true
                                ? Icons.key
                                : Icons.person,
                            size: 36,
                            color: colorScheme.onPrimaryContainer,
                          )
                          : null,
                ),
                Positioned(
                  right: 0,
                  bottom: 0,
                  child: Container(
                    decoration: BoxDecoration(
                      color: colorScheme.surface,
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: const Icon(Icons.camera_alt, size: 16),
                      constraints: const BoxConstraints(),
                      padding: const EdgeInsets.all(4),
                      onPressed: _showCoverOptions,
                    ),
                  ),
                ),
              ],
            ),
            accountName: Text(
              currentCred?.displayName ??
                  (selectedClientEmail != null
                      ? selectedClientEmail!.split('@').first
                      : 'ManyDrive'),
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: Colors.white,
              ),
            ),
            accountEmail: Row(
              children: [
                Expanded(
                  child: Text(
                    selectedClientEmail ?? 'Chưa đăng nhập',
                    style: const TextStyle(color: Colors.white70, fontSize: 13),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const Icon(Icons.arrow_drop_down, color: Colors.white70),
              ],
            ),
            onDetailsPressed: _showAccountSelectionModal,
          ),
          ListTile(
            leading: const Icon(Icons.switch_account),
            title: const Text('Chuyển tài khoản'),
            subtitle: Text(
              'Đang dùng: ${selectedClientEmail ?? "Chưa chọn"}',
              style: const TextStyle(fontSize: 12),
            ),
            onTap: _showAccountSelectionModal,
          ),
          ListTile(
            leading: const Icon(Icons.add_circle_outline),
            title: const Text('Thêm tài khoản mới'),
            onTap: () {
              Navigator.pop(context);
              showLoginDialog(
                context,
                widget.credentialRepository,
                widget.onLogin,
              );
            },
          ),
          if (widget.onOpenTrash != null)
            ListTile(
              leading: const Icon(Icons.delete_outline),
              title: const Text('Thùng rác'),
              onTap: () {
                Navigator.pop(context);
                widget.onOpenTrash!();
              },
            ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.settings_outlined),
            title: const Text('Cài đặt'),
            onTap: () {
              Navigator.pop(context);
              showSettingsDialog(
                context,
                themeMode: widget.themeMode,
                onThemeModeChanged: widget.onThemeModeChanged,
                superDarkMode: widget.isSuperDarkMode,
                onSuperDarkModeChanged: widget.onSuperDarkModeChanged,
                dynamicColor: widget.isDynamicColor,
                onDynamicColorChanged: widget.onDynamicColorChanged,
              );
            },
          ),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text('Giới thiệu'),
            onTap: () {
              Navigator.pop(context);
              showAboutAppDialog(context);
            },
          ),
          const Spacer(),
          const Divider(),
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Text(
              'ManyDrive v1.0.0',
              style: theme.textTheme.bodySmall?.copyWith(
                color: colorScheme.outline,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showCoverOptions() {
    showModalBottomSheet(
      context: context,
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.photo_library),
                title: const Text('Chọn ảnh bìa từ thư viện'),
                onTap: () {
                  Navigator.pop(context);
                  _pickHeaderImage();
                },
              ),
              if (_customHeaderImagePath != null)
                ListTile(
                  leading: const Icon(Icons.restore, color: Colors.red),
                  title: const Text('Khôi phục ảnh mặc định', style: TextStyle(color: Colors.red)),
                  onTap: () {
                    Navigator.pop(context);
                    _resetHeaderImage();
                  },
                ),
            ],
          ),
        );
      },
    );
  }
}
