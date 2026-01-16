import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:permission_handler/permission_handler.dart';

class NotificationService {
  static final NotificationService _instance = NotificationService._internal();
  factory NotificationService() => _instance;
  NotificationService._internal();

  final FlutterLocalNotificationsPlugin _notifications =
      FlutterLocalNotificationsPlugin();

  bool _initialized = false;

  /// Initialize notification service
  Future<void> initialize() async {
    if (_initialized) return;

    // Android settings
    const androidSettings = AndroidInitializationSettings(
      '@mipmap/launcher_icon',
    );

    // iOS settings
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    const initSettings = InitializationSettings(
      android: androidSettings,
      iOS: iosSettings,
    );

    await _notifications.initialize(
      initSettings,
      onDidReceiveNotificationResponse: _onNotificationTapped,
    );

    _initialized = true;
  }

  /// Handle notification tap
  void _onNotificationTapped(NotificationResponse response) {
    // Handle navigation or action here
    if (kDebugMode) {
      print('Notification tapped: ${response.payload}');
    }
  }

  /// Request notification permission
  Future<bool> requestPermission() async {
    if (await Permission.notification.isGranted) {
      return true;
    }

    final status = await Permission.notification.request();
    return status.isGranted;
  }

  /// Show simple notification
  Future<void> showNotification({
    required int id,
    required String title,
    required String body,
    String? payload,
  }) async {
    if (!_initialized) await initialize();

    const androidDetails = AndroidNotificationDetails(
      'default_channel',
      'General Notifications',
      channelDescription: 'General app notifications',
      importance: Importance.high,
      priority: Priority.high,
      showWhen: true,
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _notifications.show(id, title, body, details, payload: payload);
  }

  /// Download complete notification
  Future<void> showDownloadComplete({
    required String fileName,
    required String filePath,
  }) async {
    await showNotification(
      id: DateTime.now().millisecondsSinceEpoch ~/ 1000,
      title: '✅ Download Complete',
      body: 'Downloaded: $fileName',
      payload: filePath,
    );
  }

  /// Upload complete notification
  Future<void> showUploadComplete({required String fileName}) async {
    await showNotification(
      id: DateTime.now().millisecondsSinceEpoch ~/ 1000,
      title: '✅ Upload Complete',
      body: 'Uploaded: $fileName',
    );
  }

  /// Delete complete notification
  Future<void> showDeleteComplete({required String fileName}) async {
    await showNotification(
      id: DateTime.now().millisecondsSinceEpoch ~/ 1000,
      title: '🗑️ File Deleted',
      body: 'Deleted: $fileName',
    );
  }

  /// Error notification
  Future<void> showError({
    required String title,
    required String message,
  }) async {
    const androidDetails = AndroidNotificationDetails(
      'error_channel',
      'Error Notifications',
      channelDescription: 'Notifications for errors',
      importance: Importance.high,
      priority: Priority.high,
      showWhen: true,
      color: Color(0xFFF44336),
      playSound: true,
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _notifications.show(
      DateTime.now().millisecondsSinceEpoch ~/ 1000,
      title,
      message,
      details,
    );
  }

  /// Progress notification (for ongoing tasks - cannot be dismissed)
  Future<void> showProgress({
    required int id,
    required String title,
    required String body,
    required int progress,
    required int maxProgress,
  }) async {
    if (!_initialized) await initialize();

    final isComplete = progress >= maxProgress;

    final androidDetails = AndroidNotificationDetails(
      'progress_channel',
      'Task Progress',
      channelDescription: 'Shows progress of ongoing tasks',
      importance: Importance.low,
      priority: Priority.low,
      showProgress: !isComplete,
      maxProgress: maxProgress,
      progress: progress,
      ongoing: !isComplete, // Không thể quét đi khi chưa hoàn thành
      autoCancel: false,
      onlyAlertOnce: true, // Không phát âm thanh mỗi lần cập nhật
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: false,
      presentSound: false,
    );

    final details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _notifications.show(id, title, body, details);
  }

  /// Show upload/download complete notification (can be dismissed)
  Future<void> showTransferComplete({
    required int id,
    required String title,
    required String body,
  }) async {
    if (!_initialized) await initialize();

    const androidDetails = AndroidNotificationDetails(
      'progress_channel',
      'Task Progress',
      channelDescription: 'Shows progress of ongoing tasks',
      importance: Importance.high,
      priority: Priority.high,
      ongoing: false, // Có thể quét đi
      autoCancel: true,
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _notifications.show(id, title, body, details);
  }

  /// Cancel notification by ID
  Future<void> cancel(int id) async {
    await _notifications.cancel(id);
  }

  /// Cancel all notifications
  Future<void> cancelAll() async {
    await _notifications.cancelAll();
  }
}
