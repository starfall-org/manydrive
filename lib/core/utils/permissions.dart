import 'dart:io';
import 'package:permission_handler/permission_handler.dart';

Future<void> requestPermissions() async {
  if (Platform.isAndroid) {
    int androidVersion = int.tryParse(Platform.version.split('.')[0]) ?? 0;

    if (androidVersion >= 13 && await Permission.notification.isDenied) {
      await Permission.notification.request();
    }
    if (androidVersion >= 10) {
      if (await Permission.manageExternalStorage.isDenied) {
        await Permission.manageExternalStorage.request();
      }
    } else {
      if (await Permission.storage.isDenied) {
        await Permission.storage.request();
      }
    }
  }
}
