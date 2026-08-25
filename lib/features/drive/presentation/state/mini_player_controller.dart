import 'dart:io';
import 'dart:typed_data';

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

  Uint8List? _audioData;
  Uint8List? get audioData => _audioData;

  String? _title;
  String? get title => _title;

  DriveFile? _currentFile;
  DriveFile? get currentFile => _currentFile;

  List<DriveFile>? _allFiles;
  List<DriveFile>? get allFiles => _allFiles;

  void addToQueue(DriveFile file) {
    _allFiles ??= [];
    if (!_allFiles!.any((f) => f.id == file.id)) {
      _allFiles!.add(file);
      notifyListeners();
    }
  }

  DriveRepository? _driveRepository;
  DriveRepository? get driveRepository => _driveRepository;

  Function(DriveFile, List<DriveFile>?, DriveRepository, {AudioPlayer? audioPlayer, Uint8List? audioData, VideoPlayerController? videoController})? _onExpand;

  void setOnExpand(Function(DriveFile, List<DriveFile>?, DriveRepository, {AudioPlayer? audioPlayer, Uint8List? audioData, VideoPlayerController? videoController}) onExpand) {
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

  void showAudio({
    required AudioPlayer player,
    required String title,
    DriveFile? file,
    Uint8List? audioData,
    DriveRepository? driveRepository,
    List<DriveFile>? allFiles,
  }) {
    _isShowing = true;
    _type = MiniPlayerType.audio;
    _audioPlayer = player;
    _title = title;
    _currentFile = file;
    _audioData = audioData;
    _driveRepository = driveRepository;
    _allFiles = allFiles;
    notifyListeners();
  }

  void expand() {
    if (_onExpand != null && _currentFile != null && _driveRepository != null) {
      final currentF = _currentFile!;
      final repo = _driveRepository!;
      final files = _allFiles;
      final aPlayer = _audioPlayer;
      final aData = _audioData;
      final vCtrl = _videoController;

      _isShowing = false;
      notifyListeners();

      _onExpand!(
        currentF,
        files,
        repo,
        audioPlayer: _type == MiniPlayerType.audio ? aPlayer : null,
        audioData: _type == MiniPlayerType.audio ? aData : null,
        videoController: _type == MiniPlayerType.video ? vCtrl : null,
      );
    }
  }

  void hide() {
    _isShowing = false;
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
    _audioData = null;
    _currentFile = null;
    notifyListeners();
  }

  Future<void> playPrevious() async {
    if (_allFiles == null || _currentFile == null || _driveRepository == null) return;

    bool filterCondition(DriveFile f) => _type == MiniPlayerType.video ? f.isVideo : f.isAudio;
    final matchingFiles = _allFiles!.where(filterCondition).toList();
    if (matchingFiles.isEmpty) return;

    final currentIndex = matchingFiles.indexWhere((f) => f.id == _currentFile!.id);
    if (currentIndex <= 0) return;

    final prevFile = matchingFiles[currentIndex - 1];
    await _switchToFile(prevFile);
  }

  Future<void> playNext() async {
    if (_allFiles == null || _currentFile == null || _driveRepository == null) return;

    bool filterCondition(DriveFile f) => _type == MiniPlayerType.video ? f.isVideo : f.isAudio;
    final matchingFiles = _allFiles!.where(filterCondition).toList();
    if (matchingFiles.isEmpty) return;

    final currentIndex = matchingFiles.indexWhere((f) => f.id == _currentFile!.id);
    if (currentIndex == -1 || currentIndex >= matchingFiles.length - 1) return;

    final nextFile = matchingFiles[currentIndex + 1];
    await _switchToFile(nextFile);
  }

  Future<void> _switchToFile(DriveFile targetFile) async {
    if (_type == MiniPlayerType.video) {
      try {
        final oldController = _videoController;
        await oldController?.pause();

        _currentFile = targetFile;
        _title = targetFile.name;
        _videoController = null;
        notifyListeners();

        final videoData = await _driveRepository!.getFileBytes(targetFile);
        final cacheKey = targetFile.id.replaceAll('/', '_');
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
        await oldController?.dispose();

        _videoController = newController;
        _videoController!.play();
        notifyListeners();
      } catch (e) {
        debugPrint("Error switching video in mini player: $e");
      }
    } else if (_type == MiniPlayerType.audio) {
      try {
        await _audioPlayer?.stop();

        _currentFile = targetFile;
        _title = targetFile.name;
        notifyListeners();

        final bytes = await _driveRepository!.getFileBytes(targetFile);
        _audioData = bytes;
        await _audioPlayer?.setSourceBytes(bytes);
        await _audioPlayer?.resume();
        notifyListeners();
      } catch (e) {
        debugPrint("Error switching audio in mini player: $e");
      }
    }
  }
}
