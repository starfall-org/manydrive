import 'package:flutter/material.dart';
import 'package:manydrive/core/widgets/app_error_dialog.dart';

class AppErrorWidget extends StatelessWidget {
  final String errorMessage;
  final VoidCallback? onRetry;
  final String title;

  const AppErrorWidget({
    super.key,
    required this.errorMessage,
    this.onRetry,
    this.title = 'Chi Tiết Lỗi',
  });

  @override
  Widget build(BuildContext context) {
    return ErrorDialogWidget(
      title: title,
      errorMessage: errorMessage,
      onRetry: onRetry,
    );
  }
}
