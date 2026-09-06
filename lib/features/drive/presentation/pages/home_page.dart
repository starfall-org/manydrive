import 'dart:convert';
import 'package:audioplayers/audioplayers.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:manydrive/core/services/settings_service.dart';
import 'package:manydrive/core/theme/app_theme.dart';
import 'package:manydrive/features/drive/domain/entities/credential.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:manydrive/features/drive/presentation/dialogs/login_dialog.dart';
import 'package:manydrive/features/drive/presentation/pages/file_viewer_page.dart';
import 'package:manydrive/features/drive/presentation/pages/media/audio_player_page.dart';
import 'package:manydrive/features/drive/presentation/pages/media/video_player_page.dart';
import 'package:manydrive/features/drive/presentation/pages/photos/google_photos_page.dart';
import 'package:manydrive/features/drive/presentation/pages/trash_page.dart';
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
  // Navigation
  int _selectedIndex = 0;
  late final PageController _pageController;

  // Theme settings
  late final SettingsService _settingsService;
  ThemeMode _themeMode = ThemeMode.system;
  bool _isSuperDarkMode = false;
  bool _isDynamicColor = true;

  // Drive state & account type
  late final DriveState _driveState;
  bool _isS3Account = false;
  bool _isServiceAccount = false;

  final GlobalKey<FileListWidgetState> _homeFileListKey = GlobalKey();
  final GlobalKey<FileListWidgetState> _sharedFileListKey = GlobalKey();
  final GlobalKey<GooglePhotosPageState> _photosPageKey = GlobalKey();

  String get _currentTabKey => _selectedIndex == 0 ? 'home' : 'shared';

  String get _currentTabTitle {
    switch (_selectedIndex) {
      case 0:
        return 'Home';
      case 1:
        return 'Shared with me';
      default:
        return 'Google Photos';
    }
  }

  // --- Lifecycle ---

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

  @override
  void dispose() {
    _pageController.dispose();
    _driveState.dispose();
    super.dispose();
  }

  // --- Settings ---

  void _loadSettings() {
    _themeMode = _settingsService.themeMode;
    _isSuperDarkMode = _settingsService.superDarkMode;
    _isDynamicColor = _settingsService.dynamicColor;
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

  // --- Authentication ---

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
      final credential =
          await widget.credentialRepository.getCredential(selectedEmail);
      _isS3Account = credential?.isS3 ?? false;
      await _login(selectedEmail);
    }
  }

  Future<void> _login(String clientEmail) async {
    final credential =
        await widget.credentialRepository.getCredential(clientEmail);

    if (credential != null && credential.isOAuth) {
      await _refreshGoogleTokens(credential, clientEmail);
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

  /// Silently refreshes OAuth tokens and profile info for the stored credential
  Future<void> _refreshGoogleTokens(
    Credential credential,
    String clientEmail,
  ) async {
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
      if (account == null || account.email != clientEmail) return;

      final auth = await account.authentication;
      final credData = Map<String, dynamic>.from(credential.rawData);
      if (auth.accessToken?.isNotEmpty == true) {
        credData['access_token'] = auth.accessToken;
      }
      if (auth.idToken?.isNotEmpty == true) {
        credData['id_token'] = auth.idToken;
      }
      if (account.photoUrl != null) {
        credData['photo_url'] = account.photoUrl;
      }
      if (account.displayName != null) {
        credData['display_name'] = account.displayName;
      }
      await widget.credentialRepository.saveCredential(jsonEncode(credData));
    } catch (_) {}
  }

  // --- Navigation & file actions ---

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
      _pageController.jumpToPage(index);
    });
  }

  Future<bool> _onWillPop() async {
    final currentHistory = _driveState.getPathHistory(_currentTabKey);

    if (currentHistory.isNotEmpty) {
      _driveState.goBack(_currentTabKey);
      return false;
    }

    SystemNavigator.pop();
    return false;
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

  void _onExpandMedia(
    DriveFile file,
    List<DriveFile>? allFiles,
    DriveRepository driveRepository, {
    AudioPlayer? audioPlayer,
    Uint8List? audioData,
    VideoPlayerController? videoController,
  }) {
    if (!mounted) return;

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
                initialController:
                    videoController ?? miniController.videoController,
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

  void _openTrashPage(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => TrashPage(driveRepository: widget.driveRepository),
      ),
    );
  }

  // --- UI ---

  @override
  Widget build(BuildContext context) {
    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        return MaterialApp(
          theme: lightTheme(_isDynamicColor ? lightDynamic : null),
          darkTheme: darkTheme(
            _isDynamicColor ? darkDynamic : null,
            superDark: _isSuperDarkMode,
          ),
          themeMode: _themeMode,
          builder: _buildOverlay,
          home: _buildDriveHome(),
        );
      },
    );
  }

  /// App-wide overlays: upload progress banner and mini player
  Widget _buildOverlay(BuildContext context, Widget? child) {
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
  }

  Widget _buildDriveHome() {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, Object? result) async {
        if (didPop) return;
        await _onWillPop();
      },
      // Rebuilds on file-list events so the back button stays in sync
      child: StreamBuilder<List<DriveFile>>(
        stream: _driveState.getFilesStream(_currentTabKey),
        builder: (context, snapshot) => _buildDriveScaffold(context),
      ),
    );
  }

  Widget _buildDriveScaffold(BuildContext context) {
    final canGoBack =
        _driveState.getPathHistory(_currentTabKey).isNotEmpty &&
        _selectedIndex < 2;

    return Scaffold(
      drawer: _buildSideMenu(context),
      appBar: _buildTopBar(canGoBack),
      body: _buildBody(),
      bottomNavigationBar: _isS3Account
          ? null
          : BottomBarWidget(
              selectedIndex: _selectedIndex,
              onItemTapped: _onItemTapped,
              showPhotosTab: !_isServiceAccount,
            ),
      floatingActionButton: _selectedIndex == 2
          ? null
          : FloatButtonsWidget(driveState: _driveState, tabKey: _currentTabKey),
    );
  }

  Widget _buildSideMenu(BuildContext context) {
    return SideMenuWidget(
      credentialRepository: widget.credentialRepository,
      onLogin: _login,
      themeMode: _themeMode,
      onThemeModeChanged: _onThemeModeChanged,
      isSuperDarkMode: _isSuperDarkMode,
      onSuperDarkModeChanged: _toggleSuperDarkMode,
      isDynamicColor: _isDynamicColor,
      onDynamicColorChanged: _toggleDynamicColor,
      onOpenTrash: () => _openTrashPage(context),
    );
  }

  TopBarWidget _buildTopBar(bool canGoBack) {
    return TopBarWidget(
      screen: _currentTabTitle,
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
      onBackPressed: canGoBack
          ? () => _driveState.goBack(_currentTabKey)
          : null,
    );
  }

  Widget _buildBody() {
    if (_isS3Account) {
      return _buildFileList(
        tabKey: 'home',
        listKey: _homeFileListKey,
        isSharedWithMe: false,
      );
    }

    return PageView(
      controller: _pageController,
      onPageChanged: (index) {
        setState(() => _selectedIndex = index);
      },
      children: [
        _buildFileList(
          tabKey: 'home',
          listKey: _homeFileListKey,
          isSharedWithMe: false,
        ),
        _buildFileList(
          tabKey: 'shared',
          listKey: _sharedFileListKey,
          isSharedWithMe: true,
        ),
        if (!_isServiceAccount) GooglePhotosPage(key: _photosPageKey),
      ],
    );
  }

  FileListWidget _buildFileList({
    required String tabKey,
    required GlobalKey<FileListWidgetState> listKey,
    required bool isSharedWithMe,
  }) {
    return FileListWidget(
      key: listKey,
      driveState: _driveState,
      onFileOpen: (file, allFiles) => _onFileOpen(file, tabKey, allFiles),
      tabKey: tabKey,
      isSharedWithMe: isSharedWithMe,
    );
  }
}
