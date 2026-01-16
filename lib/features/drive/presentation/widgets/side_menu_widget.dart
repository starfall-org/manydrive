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

  void _addAccount(String clientEmail) async {
    await widget.credentialRepository.setSelectedEmail(clientEmail);
    await _loadCredentials();
    setState(() {
      selectedClientEmail = clientEmail;
    });
  }

  @override
  Widget build(BuildContext context) {
    final uniqueEmails = credentials.map((c) => c.clientEmail).toSet().toList();

    if (!uniqueEmails.contains(selectedClientEmail) &&
        uniqueEmails.isNotEmpty) {
      selectedClientEmail = uniqueEmails.first;
    }

    return Drawer(
      child: Column(
        children: [
          UserAccountsDrawerHeader(
            accountName: const Text("Service Account"),
            accountEmail: DropdownButton<String>(
              value: selectedClientEmail,
              items: [
                ...uniqueEmails.map(
                  (email) => DropdownMenuItem(
                    value: email,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          email.split('@').first,
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        Text(
                          credentials
                              .firstWhere((c) => c.clientEmail == email)
                              .projectId,
                          style: const TextStyle(
                            fontSize: 12,
                            color: Colors.white70,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const DropdownMenuItem<String>(
                  value: '__add_account__',
                  child: Row(
                    children: [
                      Icon(Icons.add, color: Colors.white70, size: 18),
                      SizedBox(width: 8),
                      Text(
                        'Add Account',
                        style: TextStyle(color: Colors.white70),
                      ),
                    ],
                  ),
                ),
              ],
              onChanged: (value) {
                if (value == '__add_account__') {
                  showLoginDialog(context, widget.credentialRepository, (
                    email,
                  ) {
                    _addAccount(email);
                    widget.onLogin(email);
                  });
                } else if (value != null) {
                  widget.credentialRepository.setSelectedEmail(value);
                  widget.onLogin(value);
                }
              },
              dropdownColor: Colors.blue.shade800,
              style: const TextStyle(color: Colors.white),
              underline: Container(height: 2, color: Colors.white70),
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
