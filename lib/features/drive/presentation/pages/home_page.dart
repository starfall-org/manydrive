import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manydrive/core/services/settings_service.dart';
import 'package:manydrive/core/theme/app_theme.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:manydrive/features/drive/presentation/dialogs/login_dialog.dart';
import 'package:manydrive/features/drive/presentation/pages/file_viewer_page.dart';
import 'package:manydrive/features/drive/presentation/state/drive_state.dart';
import 'package:manydrive/features/drive/presentation/widgets/bottom_bar_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/file_list_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/float_buttons_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/side_menu_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/top_bar_widget.dart';
import 'package:manydrive/injection_container.dart';

class HomePage extends StatefulWidget {
  final DriveRepository driveRepository;
  final CredentialRepository credentialRepository;

  const HomePage({
    super.key,
    required this.driveRepository,
    required this.credentialRepository,
  });

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _selectedIndex = 0;
  ThemeMode _themeMode = ThemeMode.system;
  bool _isSuperDarkMode = false;
  bool _isDynamicColor = true;
  late final DriveState _driveState;
  late final PageController _pageController;
  late final SettingsService _settingsService;
  final GlobalKey<FileListWidgetState> _homeFileListKey = GlobalKey();
  final GlobalKey<FileListWidgetState> _sharedFileListKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _settingsService = injector.settingsService;
    _loadSettings();
    _driveState = DriveState(
      widget.driveRepository,
      widget.credentialRepository,
    );
    _pageController = PageController(initialPage: 0);
    _initialize();
  }

  void _loadSettings() {
    _themeMode = _settingsService.themeMode;
    _isSuperDarkMode = _settingsService.superDarkMode;
    _isDynamicColor = _settingsService.dynamicColor;
  }

  @override
  void dispose() {
    _pageController.dispose();
    _driveState.dispose();
    super.dispose();
  }

  Future<void> _login(String clientEmail) async {
    await _driveState.login(clientEmail);
  }

  Future<void> _initialize() async {
    final credList = await widget.credentialRepository.listCredentials();
    String? selectedEmail =
        await widget.credentialRepository.getSelectedEmail();

    if (credList.isEmpty) {
      if (mounted) {
        showLoginDialog(context, widget.credentialRepository, _login);
      }
      return;
    }

    if (selectedEmail == null) {
      selectedEmail = credList.first.clientEmail;
      await widget.credentialRepository.setSelectedEmail(selectedEmail);
    }

    await _login(selectedEmail);

    await Future.delayed(const Duration(milliseconds: 100));
    if (mounted) {
      _driveState.listFiles(tabKey: 'home');
      _driveState.listFiles(sharedWithMe: true, tabKey: 'shared');
    }
  }

  void _onFileOpen(
    DriveFile file,
    String tabKey,
    List<DriveFile> allFiles,
  ) async {
    if (file.isFolder) {
      _driveState.listFiles(folderId: file.id, tabKey: tabKey);
    } else {
      final lastViewedFile =
          await FileViewerPage(
            context: context,
            file: file,
            driveRepository: widget.driveRepository,
            allFiles: allFiles,
          ).open();

      // Nếu có file được trả về (từ video player), select và scroll đến file đó
      if (lastViewedFile != null && mounted) {
        final fileListKey =
            tabKey == 'home' ? _homeFileListKey : _sharedFileListKey;
        fileListKey.currentState?.selectAndScrollToFile(lastViewedFile);
      }
    }
  }

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
      _pageController.jumpToPage(index);
    });
  }

  void _onThemeModeChanged(ThemeMode mode) {
    setState(() => _themeMode = mode);
    _settingsService.setThemeMode(mode);
  }

  void _toggleSuperDarkMode(bool value) {
    setState(() => _isSuperDarkMode = value);
    _settingsService.setSuperDarkMode(value);
  }

  void _toggleDynamicColor(bool value) {
    setState(() => _isDynamicColor = value);
    _settingsService.setDynamicColor(value);
  }

  Future<bool> _onWillPop() async {
    final currentTabKey = _selectedIndex == 0 ? 'home' : 'shared';
    final currentHistory = _driveState.getPathHistory(currentTabKey);

    if (currentHistory.isNotEmpty) {
      _driveState.goBack(currentTabKey);
      return false;
    }

    SystemNavigator.pop();
    return false;
  }

  void _openTrashPage(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => _TrashPage(
              driveRepository: widget.driveRepository,
              driveState: _driveState,
            ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        final effectiveLightDynamic = _isDynamicColor ? lightDynamic : null;
        final effectiveDarkDynamic = _isDynamicColor ? darkDynamic : null;

        return MaterialApp(
          theme: lightTheme(effectiveLightDynamic),
          darkTheme: darkTheme(
            effectiveDarkDynamic,
            superDark: _isSuperDarkMode,
          ),
          themeMode: _themeMode,
          home: PopScope(
            canPop: false,
            onPopInvokedWithResult: (bool didPop, Object? result) async {
              if (didPop) return;
              await _onWillPop();
            },
            child: StreamBuilder<List<DriveFile>>(
              stream: _driveState.getFilesStream(
                _selectedIndex == 0 ? 'home' : 'shared',
              ),
              builder: (context, snapshot) {
                final currentTabKey = _selectedIndex == 0 ? 'home' : 'shared';
                final pathHistory = _driveState.getPathHistory(currentTabKey);
                final hasHistory = pathHistory.isNotEmpty;

                return Scaffold(
                  drawer: SideMenuWidget(
                    credentialRepository: widget.credentialRepository,
                    onLogin: _login,
                    themeMode: _themeMode,
                    onThemeModeChanged: _onThemeModeChanged,
                    isSuperDarkMode: _isSuperDarkMode,
                    onSuperDarkModeChanged: _toggleSuperDarkMode,
                    isDynamicColor: _isDynamicColor,
                    onDynamicColorChanged: _toggleDynamicColor,
                    onOpenTrash: () => _openTrashPage(context),
                  ),
                  appBar: TopBarWidget(
                    screen: _selectedIndex == 0 ? 'Home' : 'Shared with me',
                    onSortPressed: () {
                      if (_selectedIndex == 0) {
                        _homeFileListKey.currentState?.showSortMenu();
                      } else {
                        _sharedFileListKey.currentState?.showSortMenu();
                      }
                    },
                    onReloadPressed: () {
                      _driveState.refresh(currentTabKey);
                    },
                    onBackPressed:
                        hasHistory
                            ? () => _driveState.goBack(currentTabKey)
                            : null,
                  ),
                  body: PageView(
                    controller: _pageController,
                    onPageChanged: (index) {
                      setState(() => _selectedIndex = index);
                    },
                    children: [
                      FileListWidget(
                        key: _homeFileListKey,
                        driveState: _driveState,
                        onFileOpen:
                            (file, allFiles) =>
                                _onFileOpen(file, 'home', allFiles),
                        tabKey: 'home',
                        isSharedWithMe: false,
                      ),
                      FileListWidget(
                        key: _sharedFileListKey,
                        driveState: _driveState,
                        onFileOpen:
                            (file, allFiles) =>
                                _onFileOpen(file, 'shared', allFiles),
                        tabKey: 'shared',
                        isSharedWithMe: true,
                      ),
                    ],
                  ),
                  bottomNavigationBar: BottomBarWidget(
                    selectedIndex: _selectedIndex,
                    onItemTapped: _onItemTapped,
                  ),
                  floatingActionButton: FloatButtonsWidget(
                    driveState: _driveState,
                    tabKey: _selectedIndex == 0 ? 'home' : 'shared',
                  ),
                );
              },
            ),
          ),
        );
      },
    );
  }
}

class _TrashPage extends StatefulWidget {
  final DriveRepository driveRepository;
  final DriveState driveState;

  const _TrashPage({required this.driveRepository, required this.driveState});

  @override
  State<_TrashPage> createState() => _TrashPageState();
}

class _TrashPageState extends State<_TrashPage> {
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
      setState(() {
        _trashedFiles = files;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
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
      body:
          _isLoading
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
                      file.isFolder
                          ? Icons.folder
                          : file.isImage
                          ? Icons.image
                          : file.isVideo
                          ? Icons.video_file
                          : file.isAudio
                          ? Icons.audiotrack
                          : Icons.insert_drive_file,
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
