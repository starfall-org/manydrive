import 'package:flutter/material.dart';

class TopBarWidget extends StatelessWidget implements PreferredSizeWidget {
  final String screen;
  final VoidCallback? onSortPressed;
  final VoidCallback? onBackPressed;
  final VoidCallback? onReloadPressed;

  const TopBarWidget({
    super.key,
    required this.screen,
    this.onSortPressed,
    this.onBackPressed,
    this.onReloadPressed,
  });

  @override
  Widget build(BuildContext context) {
    return AppBar(
      backgroundColor: Theme.of(context).colorScheme.surface,
      foregroundColor: Theme.of(context).colorScheme.onSurfaceVariant,
      leading:
          onBackPressed != null
              ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: onBackPressed,
                tooltip: 'Back',
              )
              : null,
      title: Text(screen),
      actions: [
        if (onReloadPressed != null)
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: onReloadPressed,
            tooltip: 'Reload',
          ),
        if (onSortPressed != null)
          IconButton(
            icon: const Icon(Icons.sort),
            onPressed: onSortPressed,
            tooltip: 'Sort',
          ),
      ],
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(24)),
      ),
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
