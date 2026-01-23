import 'dart:io';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:path_provider/path_provider.dart';
import 'package:video_player/video_player.dart';

enum MiniPlayerType { video, audio }

class MiniPlayerController extends ChangeNotifier {
  static final MiniPlayerController _instance = MiniPlayerController._internal();
  factory MiniPlayerController() => _instance;
  MiniPlayerController._internal();

  bool _isShowing = false;
  bool get isShowing => _isShowing;

  MiniPlayerType? _type;
  MiniPlayerType? get type => _type;

  VideoPlayerController? _videoController;
  VideoPlayerController? get videoController => _videoController;

  AudioPlayer? _audioPlayer;
  AudioPlayer? get audioPlayer => _audioPlayer;

  String? _title;
  String? get title => _title;

  // Metadata for restoring full screen
  DriveFile? _currentFile;
  DriveFile? get currentFile => _currentFile;

  List<DriveFile>? _allFiles;
  List<DriveFile>? get allFiles => _allFiles;

  DriveRepository? _driveRepository;
  DriveRepository? get driveRepository => _driveRepository;

  Function(DriveFile, List<DriveFile>?, DriveRepository)? _onExpand;
  void setOnExpand(Function(DriveFile, List<DriveFile>?, DriveRepository) onExpand) {
    _onExpand = onExpand;
  }

  void showVideo({
    required VideoPlayerController controller,
    required String title,
    required DriveFile file,
    required DriveRepository driveRepository,
    List<DriveFile>? allFiles,
  }) {
    _isShowing = true;
    _type = MiniPlayerType.video;
    _videoController = controller;
    _title = title;
    _currentFile = file;
    _allFiles = allFiles;
    _driveRepository = driveRepository;
    notifyListeners();
  }

  void expand() {
    if (_onExpand != null && _currentFile != null && _driveRepository != null) {
      _onExpand!(_currentFile!, _allFiles, _driveRepository!);
      _isShowing = false;
      notifyListeners();
    }
  }

  void showAudio({
    required AudioPlayer player,
    required String title,
  }) {
    _isShowing = true;
    _type = MiniPlayerType.audio;
    _audioPlayer = player;
    _title = title;
    notifyListeners();
  }

  void hide() {
    _isShowing = false;
    // We don't dispose controllers here as they might be managed elsewhere
    // but we can pause them if needed.
    notifyListeners();
  }

  void close() {
    if (_type == MiniPlayerType.video) {
      _videoController?.pause();
    } else if (_type == MiniPlayerType.audio) {
      _audioPlayer?.pause();
    }
    _isShowing = false;
    _videoController = null;
    _audioPlayer = null;
    notifyListeners();
  }

  Future<void> playNext() async {
    if (_type != MiniPlayerType.video || _allFiles == null || _currentFile == null || _driveRepository == null) {
      return;
    }

    final currentIndex = _allFiles!.indexWhere((f) => f.id == _currentFile!.id);
    if (currentIndex == -1 || currentIndex >= _allFiles!.length - 1) {
      return;
    }

    final nextFile = _allFiles![currentIndex + 1];
    if (!nextFile.isVideo) return;

    try {
      // Tạm dừng và dispose video cũ nếu cần
      final oldController = _videoController;
      await oldController?.pause();

      _currentFile = nextFile;
      _title = nextFile.name;
      _videoController = null; // Để UI hiện loading
      notifyListeners();

      // Khởi tạo video mới
      final videoData = await _driveRepository!.getFileBytes(nextFile);
      
      final cacheKey = nextFile.id.replaceAll('/', '_');
      final cacheDir = await getTemporaryDirectory();
      final videoDir = Directory('${cacheDir.path}/video_cache');
      if (!await videoDir.exists()) {
        await videoDir.create(recursive: true);
      }
      final file = File('${videoDir.path}/$cacheKey.mp4');
      if (!await file.exists()) {
        await file.writeAsBytes(videoData);
      }

      final newController = VideoPlayerController.file(file);
      await newController.initialize();
      
      // Dispose old one after new one is ready to keep it smooth if possible
      // but here we already set _videoController = null
      await oldController?.dispose();

      _videoController = newController;
      _videoController!.play();
      notifyListeners();
      
    } catch (e) {
      debugPrint("Error playing next video in mini player: $e");
    }
  }
}
