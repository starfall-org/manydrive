import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manydrive/core/utils/snackbar.dart';

class AppErrorWidget extends StatelessWidget {
  final String errorMessage;
  final VoidCallback? onRetry;
  final String title;

  const AppErrorWidget({
    super.key,
    required this.errorMessage,
    this.onRetry,
    this.title = 'Đã xảy ra lỗi',
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Center(
      child: Container(
        constraints: const BoxConstraints(maxWidth: 500),
        margin: const EdgeInsets.all(16),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: colorScheme.errorContainer.withValues(alpha: 0.4),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: colorScheme.error.withValues(alpha: 0.3),
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(
                  Icons.error_outline_rounded,
                  color: colorScheme.error,
                  size: 28,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: colorScheme.onErrorContainer,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              constraints: const BoxConstraints(maxHeight: 200),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: colorScheme.surface,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(
                  color: colorScheme.outlineVariant,
                ),
              ),
              child: SingleChildScrollView(
                child: SelectableText(
                  errorMessage,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontFamily: 'monospace',
                    color: colorScheme.onSurface,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                OutlinedButton.icon(
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: errorMessage));
                    showSuccessSnackBar(context, 'Đã sao chép nội dung lỗi!');
                  },
                  icon: const Icon(Icons.copy_rounded, size: 18),
                  label: const Text('Sao chép lỗi'),
                ),
                if (onRetry != null) ...[
                  const SizedBox(width: 8),
                  FilledButton.icon(
                    onPressed: onRetry,
                    icon: const Icon(Icons.refresh_rounded, size: 18),
                    label: const Text('Thử lại'),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}
