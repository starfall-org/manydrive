import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/data/datasources/remote/google_photos_datasource.dart';
import 'package:manydrive/features/drive/domain/repositories/google_photos_repository.dart';
import 'package:path_provider/path_provider.dart';

class PhotoViewerPage extends StatefulWidget {
  final GooglePhotoItem item;
  final GooglePhotosRepository photosRepository;

  const PhotoViewerPage({
    super.key,
    required this.item,
    required this.photosRepository,
  });

  @override
  State<PhotoViewerPage> createState() => _PhotoViewerPageState();
}

class _PhotoViewerPageState extends State<PhotoViewerPage> {
  bool _isDownloading = false;

  Future<void> _downloadPhoto() async {
    setState(() => _isDownloading = true);
    try {
      final bytes = await widget.photosRepository.getMediaItemBytes(widget.item);
      final directory =
          await getDownloadsDirectory() ?? await getApplicationDocumentsDirectory();
      final filePath = '${directory.path}/${widget.item.filename}';
      final file = File(filePath);
      await file.writeAsBytes(bytes);

      if (mounted) {
        showSuccessSnackBar(context, 'Saved to $filePath');
      }
    } catch (e) {
      if (mounted) {
        showErrorSnackBar(context, 'Failed to download photo: $e');
      }
    } finally {
      if (mounted) setState(() => _isDownloading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(widget.item.filename, style: const TextStyle(fontSize: 16)),
        actions: [
          if (_isDownloading)
            const Padding(
              padding: EdgeInsets.all(12.0),
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
              ),
            )
          else
            IconButton(
              icon: const Icon(Icons.download),
              onPressed: _downloadPhoto,
              tooltip: 'Download',
            ),
        ],
      ),
      body: Center(
        child: InteractiveViewer(
          minScale: 0.5,
          maxScale: 4.0,
          child: CachedNetworkImage(
            imageUrl: widget.item.baseUrl,
            fit: BoxFit.contain,
            placeholder: (context, url) => const Center(
              child: CircularProgressIndicator(color: Colors.white),
            ),
            errorWidget: (context, url, error) => const Center(
              child: Icon(Icons.broken_image, color: Colors.white, size: 64),
            ),
          ),
        ),
      ),
    );
  }
}
