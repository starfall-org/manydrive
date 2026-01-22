import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:video_player/video_player.dart';

class MiniPlayerWidget extends StatefulWidget {
  final MiniPlayerController controller;

  const MiniPlayerWidget({super.key, required this.controller});

  @override
  State<MiniPlayerWidget> createState() => _MiniPlayerWidgetState();
}

class _MiniPlayerWidgetState extends State<MiniPlayerWidget> {
  Offset _offset = const Offset(10, 100);
  final double _width = 200;
  final double _height = 120;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_handleUpdate);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleUpdate);
    super.dispose();
  }

  void _handleUpdate() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.controller.isShowing) return const SizedBox.shrink();

    return Positioned(
      left: _offset.dx,
      top: _offset.dy,
      child: GestureDetector(
        onPanUpdate: (details) {
          setState(() {
            _offset += details.delta;
            // Basic boundary check
            final size = MediaQuery.of(context).size;
            _offset = Offset(
              _offset.dx.clamp(0, size.width - _width),
              _offset.dy.clamp(0, size.height - _height),
            );
          });
        },
        child: Material(
          elevation: 8,
          borderRadius: BorderRadius.circular(12),
          clipBehavior: Clip.antiAlias,
          color: Colors.black,
          child: Container(
            width: _width,
            height: _height,
            decoration: BoxDecoration(
              border: Border.all(color: Colors.white24),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Stack(
              children: [
                _buildPlayer(),
                _buildTopBar(),
                _buildControls(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPlayer() {
    if (widget.controller.type == MiniPlayerType.video &&
        widget.controller.videoController != null) {
      return Center(
        child: AspectRatio(
          aspectRatio: widget.controller.videoController!.value.aspectRatio,
          child: VideoPlayer(widget.controller.videoController!),
        ),
      );
    }
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.music_note, color: Colors.white, size: 40),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8.0),
            child: Text(
              widget.controller.title ?? "Unknown",
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Colors.white, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTopBar() {
    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Colors.black54, Colors.transparent],
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            IconButton(
              icon: const Icon(Icons.close, color: Colors.white, size: 20),
              onPressed: () => widget.controller.close(),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 30, minHeight: 30),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildControls() {
    return Align(
      alignment: Alignment.bottomCenter,
      child: Container(
        height: 40,
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.bottomCenter,
            end: Alignment.topCenter,
            colors: [Colors.black54, Colors.transparent],
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (widget.controller.type == MiniPlayerType.video)
              ValueListenableBuilder(
                valueListenable: widget.controller.videoController!,
                builder: (context, VideoPlayerValue value, child) {
                  return IconButton(
                    icon: Icon(
                      value.isPlaying ? Icons.pause : Icons.play_arrow,
                      color: Colors.white,
                      size: 24,
                    ),
                    onPressed: () {
                      value.isPlaying
                          ? widget.controller.videoController!.pause()
                          : widget.controller.videoController!.play();
                    },
                  );
                },
              )
            else if (widget.controller.type == MiniPlayerType.audio)
              StreamBuilder(
                stream: widget.controller.audioPlayer!.onPlayerStateChanged,
                builder: (context, snapshot) {
                  final isPlaying = snapshot.data == PlayerState.playing;
                  return IconButton(
                    icon: Icon(
                      isPlaying ? Icons.pause : Icons.play_arrow,
                      color: Colors.white,
                      size: 24,
                    ),
                    onPressed: () {
                      isPlaying
                          ? widget.controller.audioPlayer!.pause()
                          : widget.controller.audioPlayer!.resume();
                    },
                  );
                },
              ),
          ],
        ),
      ),
    );
  }
}
