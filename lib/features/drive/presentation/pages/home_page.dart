import 'dart:convert';
import 'package:audioplayers/audioplayers.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:manydrive/core/services/settings_service.dart';
import 'package:manydrive/core/theme/app_theme.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:manydrive/features/drive/presentation/dialogs/login_dialog.dart';
import 'package:manydrive/features/drive/presentation/pages/file_viewer_page.dart';
import 'package:manydrive/features/drive/presentation/pages/media/audio_player_page.dart';
import 'package:manydrive/features/drive/presentation/pages/media/video_player_page.dart';
import 'package:manydrive/features/drive/presentation/pages/photos/google_photos_page.dart';
import 'package:manydrive/features/drive/presentation/state/drive_state.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:manydrive/features/drive/presentation/widgets/bottom_bar_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/file_list_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/float_buttons_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/mini_player_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/side_menu_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/top_bar_widget.dart';
import 'package:manydrive/features/drive/presentation/widgets/upload_progress_widget.dart';
import 'package:manydrive/injection_container.dart';
import 'package:video_player/video_player.dart';

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
  final GlobalKey<GooglePhotosPageState> _photosPageKey = GlobalKey();

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
    MiniPlayerController().setOnExpand(_onExpandMedia);
    _initialize();
  }

  void _onExpandMedia(
    DriveFile file,
    List<DriveFile>? allFiles,
    DriveRepository driveRepository, {
    AudioPlayer? audioPlayer,
    Uint8List? audioData,
    VideoPlayerController? videoController,
  }) {
    if (mounted) {
      final miniController = MiniPlayerController();
      if (file.isVideo || miniController.type == MiniPlayerType.video) {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder:
                (context) => VideoPlayerPage(
                  file: file,
                  allFiles: allFiles,
                  driveRepository: driveRepository,
                  initialController: videoController ?? miniController.videoController,
                ),
          ),
        );
      } else {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder:
                (context) => AudioPlayerPage(
                  title: file.name,
                  file: file,
                  allFiles: allFiles,
                  audioData: audioData ?? miniController.audioData,
                  driveRepository: driveRepository,
                  initialAudioPlayer: audioPlayer ?? miniController.audioPlayer,
                ),
          ),
        );
      }
    }
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

  bool _isS3Account = false;
  bool _isServiceAccount = false;

  Future<void> _login(String clientEmail) async {
    final credential = await widget.credentialRepository.getCredential(clientEmail);

    if (credential != null && credential.isOAuth) {
      try {
        final googleSignIn = GoogleSignIn(
          scopes: [
            'https://www.googleapis.com/auth/drive',
            'https://www.googleapis.com/auth/drive.file',
            'https://www.googleapis.com/auth/drive.readonly',
            'https://www.googleapis.com/auth/photoslibrary.readonly',
            'https://www.googleapis.com/auth/photoslibrary',
          ],
        );
        final account = await googleSignIn.signInSilently();
        if (account != null && account.email == clientEmail) {
          final auth = await account.authentication;
          final credData = Map<String, dynamic>.from(credential.rawData);
          if (auth.accessToken != null && auth.accessToken!.isNotEmpty) {
            credData['access_token'] = auth.accessToken;
          }
          if (auth.idToken != null && auth.idToken!.isNotEmpty) {
            credData['id_token'] = auth.idToken;
          }
          if (account.photoUrl != null) {
            credData['photo_url'] = account.photoUrl;
          }
          if (account.displayName != null) {
            credData['display_name'] = account.displayName;
          }
          await widget.credentialRepository.saveCredential(jsonEncode(credData));
        }
      } catch (_) {}
    }

    _isS3Account = credential?.isS3 ?? false;
    _isServiceAccount = credential?.isServiceAccount ?? false;

    await _driveState.login(clientEmail);

    if (_isS3Account && _selectedIndex != 0) {
      _onItemTapped(0);
    }

    if (mounted) {
      _driveState.listFiles(tabKey: 'home');
      if (!_isS3Account) {
        _driveState.listFiles(sharedWithMe: true, tabKey: 'shared');
        _photosPageKey.currentState?.clearAndReload();
      }
    }
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

    if (selectedEmail == null && credList.isNotEmpty) {
      final firstCred = credList.first;
      selectedEmail = firstCred.clientEmail ?? firstCred.s3Endpoint;
      if (selectedEmail != null) {
        await widget.credentialRepository.setSelectedEmail(selectedEmail);
      }
    }

    if (selectedEmail != null) {
      final credential = await widget.credentialRepository.getCredential(selectedEmail);
      _isS3Account = credential?.isS3 ?? false;
      await _login(selectedEmail);
    }
  }

  void _onFileOpen(
    DriveFile file,
    String tabKey,
    List<DriveFile> allFiles,
  ) async {
    MiniPlayerController().close();

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
          builder: (context, child) {
            return Stack(
              children: [
                if (child != null) child,
                const Positioned(
                  top: 70,
                  left: 8,
                  right: 8,
                  child: UploadProgressWidget(),
                ),
                MiniPlayerWidget(controller: MiniPlayerController()),
              ],
            );
          },
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
                    screen: _selectedIndex == 0
                        ? 'Home'
                        : _selectedIndex == 1
                            ? 'Shared with me'
                            : 'Google Photos',
                    onSortPressed: () {
                      if (_selectedIndex == 0) {
                        _homeFileListKey.currentState?.showSortMenu();
                      } else if (_selectedIndex == 1) {
                        _sharedFileListKey.currentState?.showSortMenu();
                      }
                    },
                    onReloadPressed: () {
                      if (_selectedIndex == 0) {
                        _driveState.refresh('home');
                      } else if (_selectedIndex == 1) {
                        _driveState.refresh('shared');
                      } else if (_selectedIndex == 2) {
                        _photosPageKey.currentState?.refresh();
                      }
                    },
                    onBackPressed:
                        hasHistory && _selectedIndex < 2
                            ? () => _driveState.goBack(currentTabKey)
                            : null,
                  ),
                  body: _isS3Account
                      ? FileListWidget(
                          key: _homeFileListKey,
                          driveState: _driveState,
                          onFileOpen:
                              (file, allFiles) => _onFileOpen(file, 'home', allFiles),
                          tabKey: 'home',
                          isSharedWithMe: false,
                        )
                      : PageView(
                          controller: _pageController,
                          onPageChanged: (index) {
                            setState(() => _selectedIndex = index);
                          },
                          children: [
                            FileListWidget(
                              key: _homeFileListKey,
                              driveState: _driveState,
                              onFileOpen:
                                  (file, allFiles) => _onFileOpen(file, 'home', allFiles),
                              tabKey: 'home',
                              isSharedWithMe: false,
                            ),
                            FileListWidget(
                              key: _sharedFileListKey,
                              driveState: _driveState,
                              onFileOpen: (file, allFiles) =>
                                  _onFileOpen(file, 'shared', allFiles),
                              tabKey: 'shared',
                              isSharedWithMe: true,
                            ),
                            if (!_isServiceAccount) GooglePhotosPage(key: _photosPageKey),
                          ],
                        ),
                  bottomNavigationBar: _isS3Account
                      ? null
                      : BottomBarWidget(
                          selectedIndex: _selectedIndex,
                          onItemTapped: _onItemTapped,
                          showPhotosTab: !_isServiceAccount,
                        ),
                  floatingActionButton: _selectedIndex == 2
                      ? null
                      : FloatButtonsWidget(
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
