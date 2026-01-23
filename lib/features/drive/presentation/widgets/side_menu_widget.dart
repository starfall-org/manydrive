import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/domain/entities/credential.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';
import 'package:manydrive/features/drive/presentation/dialogs/about_dialog.dart';
import 'package:manydrive/features/drive/presentation/dialogs/login_dialog.dart';
import 'package:manydrive/features/drive/presentation/dialogs/settings_dialog.dart';

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

  @override
  void initState() {
    super.initState();
    _loadCredentials();
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
            title: const Text('Delete Account'),
            content: Text(
              'Are you sure you want to remove the account "$identifier"?',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                style: TextButton.styleFrom(foregroundColor: Colors.red),
                child: const Text('Delete'),
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

    return Drawer(
      child: Column(
        children: [
          UserAccountsDrawerHeader(
            accountName: const Text("ManyDrive"),
            accountEmail: Theme(
              data: Theme.of(context).copyWith(
                canvasColor: Theme.of(context).colorScheme.surface,
              ),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  value: selectedClientEmail,
                  isExpanded: true,
                  icon: const Icon(Icons.arrow_drop_down, color: Colors.white),
                  items: [
                    ...uniqueIdentifiers.map(
                      (id) {
                        final cred = credentials.firstWhere(
                          (c) => (c.clientEmail ?? c.s3Endpoint) == id,
                        );
                        return DropdownMenuItem(
                          value: id,
                          child: Container(
                            constraints: const BoxConstraints(maxWidth: 200),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Text(
                                        cred.username,
                                        style: TextStyle(
                                          fontWeight: FontWeight.bold,
                                          color:
                                              Theme.of(
                                                context,
                                              ).colorScheme.onSurface,
                                        ),
                                      ),
                                      Text(
                                        cred.isS3
                                            ? (cred.s3Endpoint ?? '')
                                            : (cred.projectId ?? ''),
                                        style: TextStyle(
                                          fontSize: 11,
                                          color:
                                              Theme.of(
                                                context,
                                              ).colorScheme.onSurfaceVariant,
                                        ),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ),
                                ),
                                if (uniqueIdentifiers.length > 1)
                                  IconButton(
                                    icon: const Icon(
                                      Icons.delete_outline,
                                      size: 18,
                                      color: Colors.red,
                                    ),
                                    onPressed: () => _deleteAccount(id),
                                  ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                    DropdownMenuItem<String>(
                      value: '__add_account__',
                      child: Row(
                        children: [
                          Icon(Icons.add, color: Theme.of(context).colorScheme.primary, size: 18),
                          const SizedBox(width: 8),
                          Text(
                            'Add Account',
                            style: TextStyle(color: Theme.of(context).colorScheme.primary),
                          ),
                        ],
                      ),
                    ),
                  ],
                  onChanged: (value) {
                    if (value == '__add_account__') {
                      showLoginDialog(context, widget.credentialRepository, (
                        identifier,
                      ) {
                        _addAccount(identifier);
                        widget.onLogin(identifier);
                      });
                    } else if (value != null) {
                      setState(() {
                        selectedClientEmail = value;
                      });
                      widget.credentialRepository.setSelectedEmail(value);
                      widget.onLogin(value);
                    }
                  },
                  selectedItemBuilder: (BuildContext context) {
                    return [
                      ...uniqueIdentifiers.map((id) {
                        return Container(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            id,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                              fontWeight: FontWeight.w500,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        );
                      }),
                      const SizedBox.shrink(),
                    ];
                  },
                  dropdownColor: Theme.of(context).colorScheme.surface,
                ),
              ),
            ),
            decoration: const BoxDecoration(
              image: DecorationImage(
                image: AssetImage('assets/background.png'),
                fit: BoxFit.cover,
              ),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.delete_outline),
            title: const Text("Trash"),
            onTap: () {
              Navigator.pop(context);
              widget.onOpenTrash?.call();
            },
          ),
          ListTile(
            leading: const Icon(Icons.settings),
            title: const Text("Settings"),
            onTap: () {
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
            leading: const Icon(Icons.info),
            title: const Text("About"),
            onTap: () => showAboutAppDialog(context),
          ),
        ],
      ),
    );
  }
}
