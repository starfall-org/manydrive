import 'dart:async';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';

class MiniPlayerWidget extends StatefulWidget {
  final MiniPlayerController controller;

  const MiniPlayerWidget({super.key, required this.controller});

  @override
  State<MiniPlayerWidget> createState() => _MiniPlayerWidgetState();
}

class _MiniPlayerWidgetState extends State<MiniPlayerWidget> {
  Offset _offset = Offset.zero;
  final double _width = 200;
  final double _height = 120;
  final double _margin = 16.0;
  bool _isInitialized = false;
  bool _showControls = true;
  Timer? _hideTimer;
  bool _autoPlayNext = true;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_handleUpdate);
    _loadAutoPlaySetting();
    _startHideTimer();
  }

  Future<void> _loadAutoPlaySetting() async {
    final prefs = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() {
        _autoPlayNext = prefs.getBool('video_autoplay_next') ?? true;
      });
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_isInitialized) {
      final size = MediaQuery.of(context).size;
      final padding = MediaQuery.of(context).padding;
      // Mặc định ở góc dưới bên phải
      _offset = Offset(
        size.width - _width - _margin,
        size.height - _height - _margin - padding.bottom - 60, // Tránh bottom bar
      );
      _isInitialized = true;
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleUpdate);
    _hideTimer?.cancel();
    super.dispose();
  }

  void _handleUpdate() {
    if (mounted) {
      setState(() {});
      if (widget.controller.isShowing) {
        if (!_showControls) {
          _toggleControls();
        }
        // Kiểm tra cài đặt autoplay mỗi khi mini player hiện lên
        _loadAutoPlaySetting();
      }
    }
  }

  void _startHideTimer() {
    _hideTimer?.cancel();
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (mounted && _showControls) {
        setState(() {
          _showControls = false;
        });
      }
    });
  }

  void _toggleControls() {
    setState(() {
      _showControls = !_showControls;
    });
    if (_showControls) {
      _startHideTimer();
    }
  }

  void _snapToClosestCorner() {
    final size = MediaQuery.of(context).size;
    final padding = MediaQuery.of(context).padding;
    
    // Các vị trí hít vào (có tính đến margin và safe area)
    final double left = _margin;
    final double right = size.width - _width - _margin;
    final double top = _margin + padding.top;
    final double bottom = size.height - _height - _margin - padding.bottom - 60;

    final double centerX = _offset.dx + _width / 2;
    final double centerY = _offset.dy + _height / 2;

    double targetX = centerX < size.width / 2 ? left : right;
    double targetY = centerY < size.height / 2 ? top : bottom;

    setState(() {
      _offset = Offset(targetX, targetY);
    });
    _startHideTimer();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.controller.isShowing) return const SizedBox.shrink();

    return AnimatedPositioned(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOutBack,
      left: _offset.dx,
      top: _offset.dy,
      child: GestureDetector(
        onTap: _toggleControls,
        onPanUpdate: (details) {
          setState(() {
            _offset += details.delta;
            if (!_showControls) _showControls = true;
          });
          _hideTimer?.cancel();
        },
        onPanEnd: (details) {
          _snapToClosestCorner();
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
                AnimatedOpacity(
                  opacity: _showControls ? 1.0 : 0.0,
                  duration: const Duration(milliseconds: 200),
                  child: IgnorePointer(
                    ignoring: !_showControls,
                    child: Stack(
                      children: [
                        _buildTopBar(),
                        _buildControls(),
                        // Nút expand ở giữa
                        Center(
                          child: IconButton(
                            icon: const Icon(Icons.fullscreen, color: Colors.white, size: 30),
                            onPressed: () => widget.controller.expand(),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPlayer() {
    if (widget.controller.type == MiniPlayerType.video) {
      if (widget.controller.videoController != null) {
        return Center(
          child: AspectRatio(
            aspectRatio: widget.controller.videoController!.value.aspectRatio,
            child: VideoPlayer(widget.controller.videoController!),
          ),
        );
      } else {
        return const Center(
          child: CircularProgressIndicator(color: Colors.white),
        );
      }
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
        decoration: const BoxDecoration(
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

  void _checkVideoEnd() {
    if (widget.controller.type != MiniPlayerType.video) return;
    final controller = widget.controller.videoController;
    if (controller == null || !controller.value.isInitialized) return;

    final isEnded = controller.value.position >= controller.value.duration;
    final isNotPlaying = !controller.value.isPlaying;

    if (isEnded && isNotPlaying && _autoPlayNext) {
      final allFiles = widget.controller.allFiles;
      final currentFile = widget.controller.currentFile;
      if (allFiles != null && currentFile != null) {
        final currentIndex = allFiles.indexWhere((f) => f.id == currentFile.id);
        if (currentIndex != -1 && currentIndex < allFiles.length - 1) {
          // Play next video
          widget.controller.playNext();
        }
      }
    }
  }

  Widget _buildControls() {
    return Align(
      alignment: Alignment.bottomCenter,
      child: Container(
        height: 40,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.bottomCenter,
            end: Alignment.topCenter,
            colors: [Colors.black54, Colors.transparent],
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (widget.controller.type == MiniPlayerType.video && widget.controller.videoController != null)
              ValueListenableBuilder(
                valueListenable: widget.controller.videoController!,
                builder: (context, VideoPlayerValue value, child) {
                  // Kiểm tra kết thúc video để autoplay
                  _checkVideoEnd();
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
                      _startHideTimer();
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
                      _startHideTimer();
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
