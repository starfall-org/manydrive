import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:manydrive/core/utils/snackbar.dart';

void showAppErrorDialog(
  BuildContext context, {
  required String errorMessage,
  String title = 'Chi Tiết Lỗi',
}) {
  showDialog(
    context: context,
    builder: (dialogContext) => ErrorDialogWidget(
      title: title,
      errorMessage: errorMessage,
      onMinimize: () {
        showFloatingErrorButton(context, errorMessage: errorMessage, title: title);
      },
    ),
  );
}

void showFloatingErrorButton(
  BuildContext context, {
  required String errorMessage,
  required String title,
}) {
  OverlayEntry? overlayEntry;

  overlayEntry = OverlayEntry(
    builder: (context) {
      return _FloatingErrorButtonWidget(
        title: title,
        errorMessage: errorMessage,
        onDismiss: () {
          overlayEntry?.remove();
        },
        onRestore: () {
          overlayEntry?.remove();
          showAppErrorDialog(context, errorMessage: errorMessage, title: title);
        },
      );
    },
  );

  Overlay.of(context).insert(overlayEntry);
}

class _FloatingErrorButtonWidget extends StatefulWidget {
  final String title;
  final String errorMessage;
  final VoidCallback onDismiss;
  final VoidCallback onRestore;

  const _FloatingErrorButtonWidget({
    required this.title,
    required this.errorMessage,
    required this.onDismiss,
    required this.onRestore,
  });

  @override
  State<_FloatingErrorButtonWidget> createState() =>
      _FloatingErrorButtonWidgetState();
}

class _FloatingErrorButtonWidgetState
    extends State<_FloatingErrorButtonWidget> {
  Offset position = const Offset(20, 100);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Positioned(
      left: position.dx,
      top: position.dy,
      child: GestureDetector(
        onPanUpdate: (details) {
          setState(() {
            position += details.delta;
          });
        },
        child: Material(
          elevation: 8,
          borderRadius: BorderRadius.circular(30),
          color: colorScheme.error,
          child: InkWell(
            borderRadius: BorderRadius.circular(30),
            onTap: widget.onRestore,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.error_outline, color: Colors.white, size: 20),
                  const SizedBox(width: 8),
                  const Text(
                    'Lỗi (Bấm để xem)',
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                  const SizedBox(width: 4),
                  GestureDetector(
                    onTap: widget.onDismiss,
                    child: const Padding(
                      padding: EdgeInsets.all(2.0),
                      child: Icon(Icons.close, color: Colors.white70, size: 16),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class ErrorDialogWidget extends StatelessWidget {
  final String title;
  final String errorMessage;
  final VoidCallback? onMinimize;
  final VoidCallback? onRetry;

  const ErrorDialogWidget({
    super.key,
    this.title = 'Chi Tiết Lỗi',
    required this.errorMessage,
    this.onMinimize,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: Row(
        children: [
          Icon(Icons.error_outline, color: colorScheme.error),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              title,
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          if (onMinimize != null)
            IconButton(
              icon: const Icon(Icons.remove),
              tooltip: 'Thu nhỏ',
              onPressed: () {
                Navigator.of(context).pop();
                onMinimize!();
              },
            ),
        ],
      ),
      content: SingleChildScrollView(
        child: Container(
          constraints: const BoxConstraints(maxHeight: 250),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: colorScheme.outlineVariant),
          ),
          child: SingleChildScrollView(
            child: SelectableText(
              errorMessage,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
            ),
          ),
        ),
      ),
      actions: [
        TextButton.icon(
          icon: const Icon(Icons.copy, size: 18),
          label: const Text('Sao Chép Lỗi'),
          onPressed: () {
            Clipboard.setData(ClipboardData(text: errorMessage));
            showSuccessSnackBar(context, 'Đã sao chép chi tiết lỗi!');
          },
        ),
        if (onRetry != null)
          ElevatedButton(
            onPressed: onRetry,
            child: const Text('Thử lại'),
          ),
        ElevatedButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Đóng'),
        ),
      ],
    );
  }
}
