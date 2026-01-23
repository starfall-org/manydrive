import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/presentation/widgets/file_list_widget.dart';

class SortBottomSheet extends StatelessWidget {
  final SortType currentSortType;
  final bool isAscending;
  final Function(SortType type, bool ascending) onSortSelected;

  const SortBottomSheet({
    super.key,
    required this.currentSortType,
    required this.isAscending,
    required this.onSortSelected,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _SortOption(
            type: SortType.name,
            icon: Icons.sort_by_alpha,
            title: 'Sort by Name',
            isSelected: currentSortType == SortType.name,
            isAscending: isAscending,
            onTap: () => _handleTap(context, SortType.name),
          ),
          _SortOption(
            type: SortType.date,
            icon: Icons.access_time,
            title: 'Sort by Date',
            isSelected: currentSortType == SortType.date,
            isAscending: isAscending,
            onTap: () => _handleTap(context, SortType.date),
          ),
          _SortOption(
            type: SortType.size,
            icon: Icons.data_usage,
            title: 'Sort by Size',
            isSelected: currentSortType == SortType.size,
            isAscending: isAscending,
            onTap: () => _handleTap(context, SortType.size),
          ),
        ],
      ),
    );
  }

  void _handleTap(BuildContext context, SortType type) {
    if (currentSortType == type) {
      onSortSelected(type, !isAscending);
    } else {
      onSortSelected(type, type == SortType.name);
    }
    Navigator.pop(context);
  }
}

class _SortOption extends StatelessWidget {
  final SortType type;
  final IconData icon;
  final String title;
  final bool isSelected;
  final bool isAscending;
  final VoidCallback onTap;

  const _SortOption({
    required this.type,
    required this.icon,
    required this.title,
    required this.isSelected,
    required this.isAscending,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      trailing:
          isSelected
              ? Icon(isAscending ? Icons.arrow_upward : Icons.arrow_downward)
              : null,
      onTap: onTap,
    );
  }
}
