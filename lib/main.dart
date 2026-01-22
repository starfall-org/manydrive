import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:manydrive/core/utils/permissions.dart';
import 'package:manydrive/features/drive/presentation/pages/home_page.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:manydrive/features/drive/presentation/widgets/mini_player_widget.dart';
import 'package:manydrive/injection_container.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize dependency injection
  await injector.init();

  // Make status bar and navigation bar transparent
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      statusBarBrightness: Brightness.light,
      systemNavigationBarColor: Colors.transparent,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );

  // Allow UI to extend into status bar and navigation bar areas
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);

  // Request permissions
  requestPermissions();

  runApp(const ManyDriveApp());
}

class ManyDriveApp extends StatelessWidget {
  const ManyDriveApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ManyDrive',
      builder: (context, child) {
        return Stack(
          children: [
            if (child != null) child,
            MiniPlayerWidget(controller: MiniPlayerController()),
          ],
        );
      },
      home: HomePage(
        driveRepository: injector.driveRepository,
        credentialRepository: injector.credentialRepository,
      ),
    );
  }
}
