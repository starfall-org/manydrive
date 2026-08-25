import 'package:flutter/material.dart';

class BottomBarWidget extends StatelessWidget {
  final int selectedIndex;
  final Function(int) onItemTapped;
  final bool showPhotosTab;

  const BottomBarWidget({
    super.key,
    required this.selectedIndex,
    required this.onItemTapped,
    this.showPhotosTab = true,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    final destinations = <NavigationDestination>[
      const NavigationDestination(
        icon: Icon(Icons.folder_outlined),
        selectedIcon: Icon(Icons.folder_rounded),
        label: "Files",
      ),
      const NavigationDestination(
        icon: Icon(Icons.share_outlined),
        selectedIcon: Icon(Icons.share_rounded),
        label: "Shared",
      ),
      if (showPhotosTab)
        const NavigationDestination(
          icon: Icon(Icons.photo_library_outlined),
          selectedIcon: Icon(Icons.photo_library_rounded),
          label: "Photos",
        ),
    ];

    final currentIndex = selectedIndex.clamp(0, destinations.length - 1);

    return Container(
      decoration: BoxDecoration(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        child: NavigationBar(
          selectedIndex: currentIndex,
          onDestinationSelected: onItemTapped,
          elevation: 3,
          indicatorColor: colorScheme.primaryContainer,
          destinations: destinations,
        ),
      ),
    );
  }
}
