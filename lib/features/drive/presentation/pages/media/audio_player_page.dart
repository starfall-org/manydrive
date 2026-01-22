import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';

class AudioPlayerPage extends StatefulWidget {
  final Uint8List audioData;
  final String title;

  const AudioPlayerPage({
    super.key,
    required this.audioData,
    required this.title,
  });

  @override
  State<AudioPlayerPage> createState() => _AudioPlayerPageState();
}

class _AudioPlayerPageState extends State<AudioPlayerPage> {
  late AudioPlayer _audioPlayer;

  @override
  void initState() {
    super.initState();
    _audioPlayer = AudioPlayer();
    _audioPlayer.setSourceBytes(widget.audioData);
    _audioPlayer.resume();
  }

  @override
  void dispose() {
    // Only dispose if not going to mini player
    if (!MiniPlayerController().isShowing) {
      _audioPlayer.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: true,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) {
          MiniPlayerController().showAudio(
            player: _audioPlayer,
            title: widget.title,
          );
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: Colors.white),
            onPressed: () => Navigator.of(context).pop(),
          ),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.music_note, size: 100, color: Colors.white),
              const SizedBox(height: 20),
              Text(
                widget.title,
                style: const TextStyle(color: Colors.white, fontSize: 18),
              ),
              const SizedBox(height: 40),
              StreamBuilder(
                stream: _audioPlayer.onPlayerStateChanged,
                builder: (context, snapshot) {
                  final isPlaying = snapshot.data == PlayerState.playing;
                  return IconButton(
                    icon: Icon(
                      isPlaying ? Icons.pause_circle_filled : Icons.play_circle_fill,
                      size: 64,
                      color: Colors.white,
                    ),
                    onPressed: () {
                      isPlaying ? _audioPlayer.pause() : _audioPlayer.resume();
                    },
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
