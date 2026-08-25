import 'package:flutter/material.dart';
import 'package:manydrive/core/services/settings_service.dart';
import 'package:manydrive/injection_container.dart';

class SettingsDialog extends StatefulWidget {
  final ThemeMode themeMode;
  final bool superDarkMode;
  final bool dynamicColor;
  final Function(ThemeMode) onThemeModeChanged;
  final Function(bool) onSuperDarkModeChanged;
  final Function(bool) onDynamicColorChanged;

  const SettingsDialog({
    super.key,
    required this.themeMode,
    required this.superDarkMode,
    required this.dynamicColor,
    required this.onThemeModeChanged,
    required this.onSuperDarkModeChanged,
    required this.onDynamicColorChanged,
  });

  @override
  State<SettingsDialog> createState() => _SettingsDialogState();
}

class _SettingsDialogState extends State<SettingsDialog> {
  late ThemeMode _themeMode;
  late bool _superDarkMode;
  late bool _dynamicColor;
  late bool _useS3PresignedUrl;
  late bool _enableFileCache;
  late String _uploadMode;
  late bool _backgroundPlayback;

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
    return AlertDialog(
      title: const Row(
        children: [Icon(Icons.settings), SizedBox(width: 8), Text('Settings')],
      ),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Theme Mode',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: SegmentedButton<int>(
                  segments: const [
                    ButtonSegment(
                      value: 0,
                      label: Text('Auto'),
                      icon: Icon(Icons.auto_mode),
                    ),
                    ButtonSegment(
                      value: 1,
                      label: Text('Light'),
                      icon: Icon(Icons.light_mode),
                    ),
                    ButtonSegment(
                      value: 2,
                      label: Text('Dark'),
                      icon: Icon(Icons.dark_mode),
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
              const SizedBox(height: 12),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'Super Dark Mode',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                subtitle: const Text(
                  'Pure black background for OLED screens',
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
                  'Dynamic Color',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                subtitle: const Text(
                  'Use system wallpaper colors (Material You)',
                  style: TextStyle(fontSize: 12),
                ),
                value: _dynamicColor,
                onChanged: (value) {
                  setState(() => _dynamicColor = value);
                  widget.onDynamicColorChanged(value);
                },
              ),
              const Divider(height: 24),
              const Text(
                'S3 Storage & Streaming',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'S3 Presigned URL',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                ),
                subtitle: const Text(
                  'Stream media directly via Presigned URL instead of downloading full bytes first',
                  style: TextStyle(fontSize: 11),
                ),
                value: _useS3PresignedUrl,
                onChanged: (value) {
                  setState(() => _useS3PresignedUrl = value);
                  _settingsService.setUseS3PresignedUrl(value);
                },
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'File Caching',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                ),
                subtitle: const Text(
                  'Cache media & files locally for faster playback and offline access',
                  style: TextStyle(fontSize: 11),
                ),
                value: _enableFileCache,
                onChanged: (value) {
                  setState(() => _enableFileCache = value);
                  _settingsService.setEnableFileCache(value);
                },
              ),
              const Divider(height: 24),
              const Text(
                'Upload & Playback',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
              ),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Multi-file Upload Mode',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                  ),
                  DropdownButton<String>(
                    value: _uploadMode,
                    items: const [
                      DropdownMenuItem(
                        value: 'sequential',
                        child: Text('Sequential', style: TextStyle(fontSize: 13)),
                      ),
                      DropdownMenuItem(
                        value: 'parallel',
                        child: Text('Parallel', style: TextStyle(fontSize: 13)),
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
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'Background Playback',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                ),
                subtitle: const Text(
                  'Keep audio/video playing when app is in background or minimized',
                  style: TextStyle(fontSize: 11),
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
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Close'),
        ),
      ],
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
  showDialog(
    context: context,
    builder:
        (context) => SettingsDialog(
          themeMode: themeMode,
          superDarkMode: superDarkMode,
          dynamicColor: dynamicColor,
          onThemeModeChanged: onThemeModeChanged,
          onSuperDarkModeChanged: onSuperDarkModeChanged,
          onDynamicColorChanged: onDynamicColorChanged,
        ),
  );
}
