import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/services/notification_service.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:manydrive/injection_container.dart';

class AudioPlayerPage extends StatefulWidget {
  final Uint8List? audioData;
  final DriveFile? file;
  final List<DriveFile>? allFiles;
  final DriveRepository? driveRepository;
  final String title;
  final AudioPlayer? initialAudioPlayer;

  const AudioPlayerPage({
    super.key,
    this.audioData,
    this.file,
    this.allFiles,
    this.driveRepository,
    required this.title,
    this.initialAudioPlayer,
  });

  @override
  State<AudioPlayerPage> createState() => _AudioPlayerPageState();
}

class _AudioPlayerPageState extends State<AudioPlayerPage> {
  late AudioPlayer _audioPlayer;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;
  bool _isPlaying = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    if (widget.initialAudioPlayer != null) {
      _audioPlayer = widget.initialAudioPlayer!;
      _isLoading = false;
    } else {
      _audioPlayer = AudioPlayer();
    }
    _initAudio();
  }

  Future<void> _initAudio() async {
    _audioPlayer.onDurationChanged.listen((d) {
      if (mounted) setState(() => _duration = d);
    });

    _audioPlayer.onPositionChanged.listen((p) {
      if (mounted) setState(() => _position = p);
    });

    _audioPlayer.onPlayerStateChanged.listen((state) {
      if (mounted) {
        final playing = state == PlayerState.playing;
        setState(() => _isPlaying = playing);
        NotificationService().showMediaNotification(
          id: 9992,
          title: widget.title,
          subtitle: 'Audio Playing',
          isPlaying: playing,
        );
      }
    });

    if (widget.initialAudioPlayer == null) {
      try {
        if (widget.file != null &&
            widget.driveRepository != null &&
            widget.driveRepository!.isS3 &&
            injector.settingsService.useS3PresignedUrl) {
          final presignedUrl = await widget.driveRepository!.getPresignedUrl(widget.file!);
          if (presignedUrl != null) {
            await _audioPlayer.setSource(UrlSource(presignedUrl));
          } else if (widget.audioData != null) {
            await _audioPlayer.setSourceBytes(widget.audioData!);
          }
        } else if (widget.audioData != null) {
          await _audioPlayer.setSourceBytes(widget.audioData!);
        }

        await _audioPlayer.resume();
        if (mounted) setState(() => _isLoading = false);
      } catch (e) {
        if (mounted) setState(() => _isLoading = false);
      }
    }
  }

  String _formatDuration(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  void dispose() {
    if (!MiniPlayerController().isShowing) {
      _audioPlayer.dispose();
      NotificationService().cancel(9992);
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
            file: widget.file,
            audioData: widget.audioData,
            driveRepository: widget.driveRepository,
            allFiles: widget.allFiles,
          );
        }
      },
      child: Scaffold(
        backgroundColor: const Color(0xFF121212),
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.keyboard_arrow_down, color: Colors.white, size: 30),
            onPressed: () => Navigator.of(context).pop(),
          ),
          title: const Text('Playing Audio', style: TextStyle(color: Colors.white, fontSize: 16)),
          centerTitle: true,
        ),
        body: SafeArea(
          child: _isLoading
              ? const Center(child: CircularProgressIndicator(color: Colors.white))
              : Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Spacer(),
                      Container(
                        width: 240,
                        height: 240,
                        decoration: BoxDecoration(
                          color: Colors.grey.shade900,
                          borderRadius: BorderRadius.circular(24),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.blue.withValues(alpha: 0.3),
                              blurRadius: 20,
                              spreadRadius: 5,
                            ),
                          ],
                        ),
                        child: const Icon(
                          Icons.music_note_rounded,
                          size: 120,
                          color: Colors.blueAccent,
                        ),
                      ),
                      const SizedBox(height: 40),
                      Text(
                        widget.title,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 30),
                      SliderTheme(
                        data: SliderThemeData(
                          activeTrackColor: Colors.blueAccent,
                          inactiveTrackColor: Colors.grey.shade800,
                          thumbColor: Colors.blueAccent,
                          trackHeight: 4,
                        ),
                        child: Slider(
                          value: _position.inSeconds
                              .toDouble()
                              .clamp(0.0, _duration.inSeconds.toDouble()),
                          max: _duration.inSeconds > 0
                              ? _duration.inSeconds.toDouble()
                              : 1.0,
                          onChanged: (val) {
                            _audioPlayer.seek(Duration(seconds: val.toInt()));
                          },
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16.0),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              _formatDuration(_position),
                              style: const TextStyle(color: Colors.grey, fontSize: 12),
                            ),
                            Text(
                              _formatDuration(_duration),
                              style: const TextStyle(color: Colors.grey, fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 20),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          IconButton(
                            iconSize: 36,
                            icon: const Icon(Icons.replay_10_rounded, color: Colors.white),
                            onPressed: () {
                              final newPos = _position - const Duration(seconds: 10);
                              _audioPlayer.seek(newPos < Duration.zero ? Duration.zero : newPos);
                            },
                          ),
                          const SizedBox(width: 20),
                          IconButton(
                            iconSize: 72,
                            icon: Icon(
                              _isPlaying
                                  ? Icons.pause_circle_filled_rounded
                                  : Icons.play_circle_fill_rounded,
                              color: Colors.blueAccent,
                            ),
                            onPressed: () {
                              _isPlaying ? _audioPlayer.pause() : _audioPlayer.resume();
                            },
                          ),
                          const SizedBox(width: 20),
                          IconButton(
                            iconSize: 36,
                            icon: const Icon(Icons.forward_10_rounded, color: Colors.white),
                            onPressed: () {
                              final newPos = _position + const Duration(seconds: 10);
                              _audioPlayer.seek(newPos > _duration ? _duration : newPos);
                            },
                          ),
                        ],
                      ),
                      const Spacer(),
                    ],
                  ),
                ),
        ),
      ),
    );
  }
}
