import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:manydrive/features/drive/presentation/pages/media/audio_player_page.dart';
import 'package:manydrive/features/drive/presentation/pages/media/video_player_page.dart';

/// Handles opening different file types
class FileViewerPage {
  final BuildContext context;
  final DriveFile file;
  final DriveRepository driveRepository;
  final List<DriveFile>? allFiles;

  FileViewerPage({
    required this.context,
    required this.file,
    required this.driveRepository,
    this.allFiles,
  });

  void open() {
    if (file.isImage) {
      _viewImage();
    } else if (file.isVideo) {
      _playVideo();
    } else if (file.isAudio) {
      _playAudio();
    } else if (file.isText) {
      _viewText();
    }
  }

  void _viewImage() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => Scaffold(
              backgroundColor: Colors.black,
              body: FutureBuilder<Uint8List>(
                future: driveRepository.getFileBytes(file),
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(
                      child: CircularProgressIndicator(color: Colors.white),
                    );
                  } else if (snapshot.hasError) {
                    return Center(
                      child: Text(
                        'Error loading image: ${snapshot.error}',
                        style: const TextStyle(color: Colors.white),
                      ),
                    );
                  } else if (snapshot.hasData) {
                    return Center(child: Image.memory(snapshot.data!));
                  } else {
                    return const Center(
                      child: Text(
                        'Failed to load image',
                        style: TextStyle(color: Colors.white),
                      ),
                    );
                  }
                },
              ),
            ),
      ),
    );
  }

  void _playVideo() {
    Navigator.push(
      context,
      PageRouteBuilder(
        pageBuilder:
            (context, animation, secondaryAnimation) => VideoPlayerPage(
              file: file,
              driveRepository: driveRepository,
              allFiles: allFiles,
            ),
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          return FadeTransition(opacity: animation, child: child);
        },
        transitionDuration: const Duration(milliseconds: 200),
        reverseTransitionDuration: const Duration(milliseconds: 200),
      ),
    );
  }

  void _playAudio() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => Scaffold(
              backgroundColor: Colors.black,
              body: FutureBuilder<Uint8List>(
                future: driveRepository.getFileBytes(file),
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(
                      child: CircularProgressIndicator(color: Colors.white),
                    );
                  } else if (snapshot.hasError) {
                    return Center(
                      child: Text(
                        'Error loading audio: ${snapshot.error}',
                        style: const TextStyle(color: Colors.white),
                      ),
                    );
                  } else if (snapshot.hasData) {
                    return AudioPlayerPage(audioData: snapshot.data!);
                  } else {
                    return const Center(
                      child: Text(
                        'Failed to load audio',
                        style: TextStyle(color: Colors.white),
                      ),
                    );
                  }
                },
              ),
            ),
      ),
    );
  }

  void _viewText() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder:
            (context) => Scaffold(
              body: FutureBuilder<Uint8List>(
                future: driveRepository.getFileBytes(file),
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(child: CircularProgressIndicator());
                  } else if (snapshot.hasError) {
                    return Center(
                      child: Text('Error loading file: ${snapshot.error}'),
                    );
                  } else {
                    return SingleChildScrollView(
                      padding: const EdgeInsets.all(16.0),
                      child: SelectableText(utf8.decode(snapshot.data!)),
                    );
                  }
                },
              ),
            ),
      ),
    );
  }
}
