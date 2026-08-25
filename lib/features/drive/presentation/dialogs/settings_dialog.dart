import 'package:flutter/material.dart';
import 'package:manydrive/core/services/notification_service.dart';
import 'package:manydrive/core/services/settings_service.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/injection_container.dart';

class SettingsPage extends StatefulWidget {
  final ThemeMode themeMode;
  final bool superDarkMode;
  final bool dynamicColor;
  final Function(ThemeMode) onThemeModeChanged;
  final Function(bool) onSuperDarkModeChanged;
  final Function(bool) onDynamicColorChanged;

  const SettingsPage({
    super.key,
    required this.themeMode,
    required this.superDarkMode,
    required this.dynamicColor,
    required this.onThemeModeChanged,
    required this.onSuperDarkModeChanged,
    required this.onDynamicColorChanged,
  });

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late ThemeMode _themeMode;
  late bool _superDarkMode;
  late bool _dynamicColor;
  late bool _useS3PresignedUrl;
  late bool _enableFileCache;
  late String _uploadMode;
  late bool _backgroundPlayback;
  late bool _enableNotifications;

  final SettingsService _settingsService = injector.settingsService;

  @override
  void initState() {
    super.initState();
    _themeMode = widget.themeMode;
    _superDarkMode = widget.superDarkMode;
    _dynamicColor = widget.dynamicColor;
    _useS3PresignedUrl = _settingsService.useS3PresignedUrl;
    _enableFileCache = _settingsService.enableFileCache;
    _uploadMode = _settingsService.uploadMode;
    _backgroundPlayback = _settingsService.backgroundPlayback;
    _enableNotifications = _settingsService.enableNotifications;
  }

  int get _themeModeIndex {
    switch (_themeMode) {
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
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Cài đặt hệ thống'),
        centerTitle: true,
      ),
      body: SafeArea(
        child: ListView(
          padding: EdgeInsets.only(
            left: 16,
            right: 16,
            top: 12,
            bottom: 24 + MediaQuery.paddingOf(context).bottom,
          ),
        children: [
          _buildSectionHeader('Giao diện & Chủ đề'),
          const SizedBox(height: 8),
          Card(
            elevation: 0,
            color: colorScheme.surfaceContainerLow,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Chế độ giao diện',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: SegmentedButton<int>(
                      segments: const [
                        ButtonSegment(
                          value: 0,
                          label: Text('Tự động'),
                          icon: Icon(Icons.auto_mode, size: 18),
                        ),
                        ButtonSegment(
                          value: 1,
                          label: Text('Sáng'),
                          icon: Icon(Icons.light_mode, size: 18),
                        ),
                        ButtonSegment(
                          value: 2,
                          label: Text('Tối'),
                          icon: Icon(Icons.dark_mode, size: 18),
                        ),
                      ],
                      selected: {_themeModeIndex},
                      onSelectionChanged: (selection) {
                        final index = selection.first;
                        final mode =
                            index == 1
                                ? ThemeMode.light
                                : index == 2
                                ? ThemeMode.dark
                                : ThemeMode.system;
                        setState(() => _themeMode = mode);
                        widget.onThemeModeChanged(mode);
                      },
                    ),
                  ),
                  const Divider(height: 24),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text(
                      'Chế độ Super Dark',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: const Text(
                      'Nền đen tuyệt đối tối ưu cho màn hình OLED',
                      style: TextStyle(fontSize: 12),
                    ),
                    value: _superDarkMode,
                    onChanged: (value) {
                      setState(() => _superDarkMode = value);
                      widget.onSuperDarkModeChanged(value);
                    },
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text(
                      'Màu sắc động (Dynamic Color)',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: const Text(
                      'Tự động đồng bộ màu theo hình nền thiết bị (Material You)',
                      style: TextStyle(fontSize: 12),
                    ),
                    value: _dynamicColor,
                    onChanged: (value) {
                      setState(() => _dynamicColor = value);
                      widget.onDynamicColorChanged(value);
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          _buildSectionHeader('Lưu trữ S3 & Phát trực tuyến'),
          const SizedBox(height: 8),
          Card(
            elevation: 0,
            color: colorScheme.surfaceContainerLow,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text(
                      'Dùng S3 Presigned URL',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: const Text(
                      'Phát media trực tiếp qua URL thay vì tải toàn bộ file về máy',
                      style: TextStyle(fontSize: 12),
                    ),
                    value: _useS3PresignedUrl,
                    onChanged: (value) {
                      setState(() => _useS3PresignedUrl = value);
                      _settingsService.setUseS3PresignedUrl(value);
                    },
                  ),
                  const Divider(height: 16),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text(
                      'Bộ nhớ đệm (File Cache)',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: const Text(
                      'Lưu bản sao cục bộ để mở file & phát media nhanh hơn',
                      style: TextStyle(fontSize: 12),
                    ),
                    value: _enableFileCache,
                    onChanged: (value) {
                      setState(() => _enableFileCache = value);
                      _settingsService.setEnableFileCache(value);
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          _buildSectionHeader('Tải lên & Trình phát'),
          const SizedBox(height: 8),
          Card(
            elevation: 0,
            color: colorScheme.surfaceContainerLow,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Chế độ tải lên nhiều file',
                              style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                            ),
                            SizedBox(height: 2),
                            Text(
                              'Tải tuần tự hoặc tải song song cùng lúc',
                              style: TextStyle(fontSize: 12, color: Colors.grey),
                            ),
                          ],
                        ),
                      ),
                      DropdownButton<String>(
                        value: _uploadMode,
                        items: const [
                          DropdownMenuItem(
                            value: 'sequential',
                            child: Text('Tuần tự', style: TextStyle(fontSize: 13)),
                          ),
                          DropdownMenuItem(
                            value: 'parallel',
                            child: Text('Song song', style: TextStyle(fontSize: 13)),
                          ),
                        ],
                        onChanged: (val) {
                          if (val != null) {
                            setState(() => _uploadMode = val);
                            _settingsService.setUploadMode(val);
                          }
                        },
                      ),
                    ],
                  ),
                  const Divider(height: 16),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text(
                      'Phát trong nền (Background Playback)',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: const Text(
                      'Tiếp tục phát audio/video khi ẩn ứng dụng hoặc chuyển ứng dụng khác',
                      style: TextStyle(fontSize: 12),
                    ),
                    value: _backgroundPlayback,
                    onChanged: (value) {
                      setState(() => _backgroundPlayback = value);
                      _settingsService.setBackgroundPlayback(value);
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          _buildSectionHeader('Thông báo hệ thống'),
          const SizedBox(height: 8),
          Card(
            elevation: 0,
            color: colorScheme.surfaceContainerLow,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'Bật thông báo ứng dụng',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                subtitle: const Text(
                  'Yêu cầu cấp quyền và hiển thị thông báo tiến trình & trình phát',
                  style: TextStyle(fontSize: 12),
                ),
                value: _enableNotifications,
                onChanged: (value) async {
                  if (value) {
                    final granted = await NotificationService().requestPermission();
                    if (granted) {
                      setState(() => _enableNotifications = true);
                      _settingsService.setEnableNotifications(true);
                      if (mounted) showSuccessSnackBar(context, 'Đã bật thông báo hệ thống');
                    } else {
                      if (mounted) showErrorSnackBar(context, 'Quyền thông báo bị từ chối');
                    }
                  } else {
                    setState(() => _enableNotifications = false);
                    _settingsService.setEnableNotifications(false);
                  }
                },
              ),
            ),
          ),
        ],
      ),
    ),
  );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 4),
      child: Text(
        title,
        style: TextStyle(
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.bold,
          fontSize: 13,
        ),
      ),
    );
  }
}

void showSettingsDialog(
  BuildContext context, {
  required ThemeMode themeMode,
  required bool superDarkMode,
  required bool dynamicColor,
  required Function(ThemeMode) onThemeModeChanged,
  required Function(bool) onSuperDarkModeChanged,
  required Function(bool) onDynamicColorChanged,
}) {
  Navigator.push(
    context,
    MaterialPageRoute(
      builder: (context) => SettingsPage(
        themeMode: themeMode,
        superDarkMode: superDarkMode,
        dynamicColor: dynamicColor,
        onThemeModeChanged: onThemeModeChanged,
        onSuperDarkModeChanged: onSuperDarkModeChanged,
        onDynamicColorChanged: onDynamicColorChanged,
      ),
    ),
  );
}
