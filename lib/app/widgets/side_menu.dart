import 'package:flutter/material.dart';
import 'package:manydrive/app/data/credentials.dart';
import 'package:manydrive/app/widgets/dialogs/about.dart';
import 'package:manydrive/app/widgets/dialogs/login.dart';

class SideMenu extends StatefulWidget {
  final Function(String) login;
  final ThemeMode themeMode;
  final Function(ThemeMode) onThemeModeChanged;
  final bool isSuperDarkMode;
  final Function(bool) onSuperDarkModeChanged;
  const SideMenu({
    super.key,
    required this.login,
    required this.themeMode,
    required this.onThemeModeChanged,
    required this.isSuperDarkMode,
    required this.onSuperDarkModeChanged,
  });

  @override
  SideMenuState createState() => SideMenuState();
}

class SideMenuState extends State<SideMenu> {
  String? selectedClientEmail;
  List<Map> accountsData = [];
  List<String> accounts = [];

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    selectedClientEmail = await Credentials.getSelected();
    List credList = await Credentials.list();
    setState(() {
      accountsData = credList.cast<Map>();
      accounts =
          credList.map((cred) => cred['client_email'] as String).toList();
    });
  }

  // Helper function để trích xuất username từ email
  String _extractUsername(String email) {
    return email.split('@').first;
  }

  // Helper function để lấy project_id từ credential data
  String _getProjectId(String clientEmail) {
    final cred = accountsData.firstWhere(
      (cred) => cred['client_email'] == clientEmail,
      orElse: () => {},
    );
    return cred['project_id']?.toString() ?? 'Unknown Project';
  }

  void _addAccount(String clientEmail) async {
    await Credentials.setSelected(clientEmail);
    await _init(); // Cập nhật danh sách tài khoản và trạng thái
    setState(() {
      selectedClientEmail = clientEmail;
    });
  }

  int get _themeModeIndex {
    switch (widget.themeMode) {
      case ThemeMode.light:
        return 1;
      case ThemeMode.dark:
        return 2;
      default:
        return 0;
    }
  }

  @override
  Widget build(BuildContext context) {
    // Ensure accounts list has unique values
    final uniqueAccounts = accounts.toSet().toList();

    // Ensure selectedClientEmail is valid
    if (!uniqueAccounts.contains(selectedClientEmail)) {
      selectedClientEmail =
          uniqueAccounts.isNotEmpty ? uniqueAccounts.first : null;
    }

    return Drawer(
      child: Column(
        children: [
          UserAccountsDrawerHeader(
            accountName: const Text("Service Account"),
            accountEmail: DropdownButton<String>(
              value: selectedClientEmail,
              items: [
                ...uniqueAccounts
                    .map(
                      (email) => DropdownMenuItem(
                        value: email,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              _extractUsername(email),
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                            Text(
                              _getProjectId(email),
                              style: const TextStyle(
                                fontSize: 12,
                                color: Colors.white70,
                              ),
                            ),
                          ],
                        ),
                      ),
                    )
                    ,
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
                  showLoginDialog(context, (clientEmail) {
                    _addAccount(clientEmail);
                  });
                } else if (value != null) {
                  setState(() {
                    Credentials.setSelected(value);
                    widget.login(value);
                  });
                }
              },
              dropdownColor: Colors.blue.shade800,
              style: const TextStyle(color: Colors.white),
              underline: Container(
                height: 2,
                color: Colors.white70,
              ),
            ),
            decoration: BoxDecoration(
              image: DecorationImage(
                image: AssetImage('assets/background.png'),
                fit: BoxFit.cover,
              ),
            ),
          ),

          // Theme Mode Segment Button
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  "Theme Mode",
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: SegmentedButton<int>(
                    segments: const [
                      ButtonSegment<int>(
                        value: 0,
                        label: Text('Auto'),
                        icon: Icon(Icons.auto_mode),
                      ),
                      ButtonSegment<int>(
                        value: 1,
                        label: Text('Light'),
                        icon: Icon(Icons.light_mode),
                      ),
                      ButtonSegment<int>(
                        value: 2,
                        label: Text('Dark'),
                        icon: Icon(Icons.dark_mode),
                      ),
                    ],
                    selected: {_themeModeIndex},
                    onSelectionChanged: (Set<int> newSelection) {
                      final index = newSelection.first;
                      ThemeMode mode;
                      switch (index) {
                        case 1:
                          mode = ThemeMode.light;
                          break;
                        case 2:
                          mode = ThemeMode.dark;
                          break;
                        default:
                          mode = ThemeMode.system;
                      }
                      widget.onThemeModeChanged(mode);
                    },
                  ),
                ),
              ],
            ),
          ),

          // Super Dark Mode Toggle
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Row(
              children: [
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        "Super Dark Mode",
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 14,
                        ),
                      ),
                      Text(
                        "Pure black background",
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: widget.isSuperDarkMode,
                  onChanged: widget.onSuperDarkModeChanged,
                ),
              ],
            ),
          ),

          const Divider(),

          ListTile(
            leading: const Icon(Icons.info),
            title: const Text("About"),
            onTap: () {
              showAbout(context);
            },
          ),
        ],
      ),
    );
  }
}
