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
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.controller.isShowing) return const SizedBox.shrink();

    final mediaQuery = MediaQuery.of(context);
    final bottomPadding = mediaQuery.padding.bottom + 56.0; // Dock above bottom nav bar

    return Positioned(
      left: 8,
      right: 8,
      bottom: bottomPadding,
      child: GestureDetector(
        onTap: () => widget.controller.expand(),
        child: Material(
          elevation: 10,
          borderRadius: BorderRadius.circular(12),
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          child: Container(
            height: 64,
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.5),
              ),
            ),
            child: Row(
              children: [
                _buildThumbnail(),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.controller.title ?? "Now Playing",
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        widget.controller.type == MiniPlayerType.video
                            ? "Video"
                            : "Audio",
                        style: TextStyle(
                          fontSize: 11,
                          color: Theme.of(context)
                              .colorScheme
                              .onSurfaceVariant
                              .withValues(alpha: 0.8),
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.skip_previous_rounded, size: 24),
                  onPressed: () => widget.controller.playPrevious(),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                  visualDensity: VisualDensity.compact,
                  tooltip: 'Previous',
                ),
                _buildControls(),
                IconButton(
                  icon: const Icon(Icons.skip_next_rounded, size: 24),
                  onPressed: () => widget.controller.playNext(),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                  visualDensity: VisualDensity.compact,
                  tooltip: 'Next',
                ),
                const SizedBox(width: 4),
                IconButton(
                  icon: const Icon(Icons.close, size: 20),
                  onPressed: () => widget.controller.close(),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                  tooltip: 'Close',
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildThumbnail() {
    if (widget.controller.type == MiniPlayerType.video) {
      final vCtrl = widget.controller.videoController;
      if (vCtrl != null && vCtrl.value.isInitialized) {
        return ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Container(
            width: 52,
            height: 52,
            color: Colors.black,
            child: FittedBox(
              fit: BoxFit.cover,
              clipBehavior: Clip.hardEdge,
              child: SizedBox(
                width: vCtrl.value.size.width > 0 ? vCtrl.value.size.width : 16,
                height: vCtrl.value.size.height > 0 ? vCtrl.value.size.height : 9,
                child: VideoPlayer(vCtrl),
              ),
            ),
          ),
        );
      } else {
        return Container(
          width: 52,
          height: 52,
          decoration: BoxDecoration(
            color: Colors.black,
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Center(
            child: SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
            ),
          ),
        );
      }
    } else {
      return Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.primaryContainer,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(
          Icons.music_note,
          color: Theme.of(context).colorScheme.onPrimaryContainer,
          size: 26,
        ),
      );
    }
  }

  Widget _buildControls() {
    if (widget.controller.type == MiniPlayerType.video &&
        widget.controller.videoController != null) {
      return ValueListenableBuilder(
        valueListenable: widget.controller.videoController!,
        builder: (context, VideoPlayerValue value, child) {
          return IconButton(
            icon: Icon(
              value.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
              size: 28,
            ),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
            onPressed: () {
              value.isPlaying
                  ? widget.controller.videoController!.pause()
                  : widget.controller.videoController!.play();
            },
          );
        },
      );
    } else if (widget.controller.type == MiniPlayerType.audio &&
        widget.controller.audioPlayer != null) {
      return StreamBuilder<PlayerState>(
        stream: widget.controller.audioPlayer!.onPlayerStateChanged,
        builder: (context, snapshot) {
          final isPlaying = snapshot.data == PlayerState.playing;
          return IconButton(
            icon: Icon(
              isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
              size: 28,
            ),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
            onPressed: () {
              isPlaying
                  ? widget.controller.audioPlayer!.pause()
                  : widget.controller.audioPlayer!.resume();
            },
          );
        },
      );
    }
    return const SizedBox.shrink();
  }
}
