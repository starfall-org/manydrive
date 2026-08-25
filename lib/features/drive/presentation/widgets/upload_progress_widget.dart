import 'package:flutter/material.dart';
import 'package:manydrive/core/services/upload_manager.dart';

class UploadProgressWidget extends StatefulWidget {
  const UploadProgressWidget({super.key});

  @override
  State<UploadProgressWidget> createState() => _UploadProgressWidgetState();
}

class _UploadProgressWidgetState extends State<UploadProgressWidget> {
  bool _isExpanded = false;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<List<UploadTask>>(
      valueListenable: UploadManager(),
      builder: (context, tasks, child) {
        if (tasks.isEmpty) return const SizedBox.shrink();

        final overallProg = UploadManager().overallProgress;
        final completedCount = tasks.where((t) => t.status == UploadStatus.completed).length;

        return Card(
          margin: const EdgeInsets.all(8),
          elevation: 6,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    const Icon(Icons.cloud_upload, color: Colors.blue),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Uploading ${tasks.length} file(s) ($completedCount/${tasks.length})',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                          ),
                          const SizedBox(height: 4),
                          LinearProgressIndicator(
                            value: overallProg,
                            backgroundColor: Colors.grey.shade300,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '${(overallProg * 100).round()}%',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                    ),
                    IconButton(
                      icon: Icon(_isExpanded ? Icons.expand_less : Icons.expand_more),
                      visualDensity: VisualDensity.compact,
                      onPressed: () => setState(() => _isExpanded = !_isExpanded),
                    ),
                    if (completedCount > 0 && !UploadManager().isUploading)
                      IconButton(
                        icon: const Icon(Icons.close, size: 18),
                        visualDensity: VisualDensity.compact,
                        onPressed: () => UploadManager().clearCompleted(),
                      ),
                  ],
                ),
                if (_isExpanded) ...[
                  const Divider(),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxHeight: 200),
                    child: ListView.builder(
                      shrinkWrap: true,
                      itemCount: tasks.length,
                      itemBuilder: (context, index) {
                        final task = tasks[index];
                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Row(
                            children: [
                              _buildStatusIcon(task.status),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      task.fileName,
                                      style: const TextStyle(fontSize: 12),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                    if (task.status == UploadStatus.uploading)
                                      LinearProgressIndicator(
                                        value: task.progress,
                                        minHeight: 3,
                                      ),
                                    if (task.status == UploadStatus.failed && task.error != null)
                                      Text(
                                        task.error!,
                                        style: const TextStyle(fontSize: 10, color: Colors.red),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                '${(task.progress * 100).round()}%',
                                style: const TextStyle(fontSize: 11),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildStatusIcon(UploadStatus status) {
    switch (status) {
      case UploadStatus.completed:
        return const Icon(Icons.check_circle, color: Colors.green, size: 18);
      case UploadStatus.failed:
        return const Icon(Icons.error, color: Colors.red, size: 18);
      case UploadStatus.uploading:
        return const SizedBox(
          width: 14,
          height: 14,
          child: CircularProgressIndicator(strokeWidth: 2),
        );
      case UploadStatus.pending:
        return const Icon(Icons.hourglass_empty, color: Colors.grey, size: 18);
    }
  }
}
