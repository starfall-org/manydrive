import 'package:flutter/material.dart';

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

  @override
  void initState() {
    super.initState();
    _themeMode = widget.themeMode;
    _superDarkMode = widget.superDarkMode;
    _dynamicColor = widget.dynamicColor;
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
      content: SingleChildScrollView(
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
            const SizedBox(height: 16),
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
          ],
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
