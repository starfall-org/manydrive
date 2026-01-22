import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
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

  void showVideo({
    required VideoPlayerController controller,
    required String title,
  }) {
    _isShowing = true;
    _type = MiniPlayerType.video;
    _videoController = controller;
    _title = title;
    notifyListeners();
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
}
