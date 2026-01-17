import 'dart:io';
import 'dart:typed_data';

import 'package:chewie/chewie.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

class VideoPlayerPage extends StatefulWidget {
  final DriveFile file;
  final List<DriveFile>? allFiles;
  final DriveRepository driveRepository;

  const VideoPlayerPage({
    super.key,
    required this.file,
    required this.driveRepository,
    this.allFiles,
  });

  @override
  State<VideoPlayerPage> createState() => _VideoPlayerPageState();
}

class _VideoPlayerPageState extends State<VideoPlayerPage> {
  late PageController _pageController;
  List<DriveFile> _videoFiles = [];
  int _currentIndex = 0;
  bool _isLoading = true;
  bool _autoPlayNext = true;

  final Map<int, VideoPlayerController> _videoControllers = {};
  final Map<int, ChewieController> _chewieControllers = {};

  @override
  void initState() {
    super.initState();
    _enableWakelock();
    _loadAutoPlaySetting();
    _loadVideoList();
  }

  Future<void> _enableWakelock() async {
    await WakelockPlus.enable();
  }

  Future<void> _loadAutoPlaySetting() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _autoPlayNext = prefs.getBool('video_autoplay_next') ?? true;
    });
  }

  Future<void> _saveAutoPlaySetting(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('video_autoplay_next', value);
  }

  Future<void> _loadVideoList() async {
    try {
      if (widget.allFiles != null && widget.allFiles!.isNotEmpty) {
        _videoFiles = widget.allFiles!.where((f) => f.isVideo).toList();
      } else {
        _videoFiles = [widget.file];
      }

      _currentIndex = _videoFiles.indexWhere((f) => f.id == widget.file.id);
      if (_currentIndex == -1) {
        _currentIndex = 0;
        _videoFiles = [widget.file];
      }

      _pageController = PageController(initialPage: _currentIndex);

      setState(() => _isLoading = false);

      await _initializePlayer(_currentIndex);

      if (_currentIndex < _videoFiles.length - 1) {
        _preloadPlayer(_currentIndex + 1);
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _videoFiles = [widget.file];
        _currentIndex = 0;
      });
      _pageController = PageController(initialPage: 0);
      await _initializePlayer(0);
    }
  }

  Future<void> _initializePlayer(int index) async {
    if (_videoControllers.containsKey(index) &&
        _videoControllers[index]!.value.isInitialized) {
      return;
    }

    try {
      final videoData = await widget.driveRepository.getFileBytes(
        _videoFiles[index],
      );
      final cacheKey = _videoFiles[index].id;

      final cachedFile = await _getCachedVideo(cacheKey);

      VideoPlayerController videoController;
      if (cachedFile != null && await cachedFile.exists()) {
        videoController = VideoPlayerController.file(cachedFile);
      } else {
        final savedFile = await _saveVideoToCache(cacheKey, videoData);
        videoController = VideoPlayerController.file(savedFile);
      }

      await videoController.initialize();

      if (mounted) {
        videoController.addListener(() => _checkVideoEnd(index));
      }

      if (!mounted) return;

      final chewieController = ChewieController(
        videoPlayerController: videoController,
        aspectRatio:
            videoController.value.aspectRatio > 0
                ? videoController.value.aspectRatio
                : 9 / 16,
        autoPlay: index == _currentIndex,
        looping: false,
        autoInitialize: true,
        showControlsOnInitialize: false,
        errorBuilder: (context, errorMessage) {
          return const SizedBox.shrink();
        },
        additionalOptions: (context) {
          return [
            OptionItem(
              onTap: (ctx) {
                Navigator.of(ctx).pop();
                setState(() => _autoPlayNext = !_autoPlayNext);
                _saveAutoPlaySetting(_autoPlayNext);
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      _autoPlayNext
                          ? 'Tự động chuyển tiếp: BẬT'
                          : 'Tự động chuyển tiếp: TẮT',
                    ),
                    duration: const Duration(seconds: 1),
                    behavior: SnackBarBehavior.floating,
                  ),
                );
              },
              iconData:
                  _autoPlayNext ? Icons.playlist_play : Icons.playlist_remove,
              title:
                  _autoPlayNext
                      ? 'Tắt tự động chuyển tiếp'
                      : 'Bật tự động chuyển tiếp',
            ),
          ];
        },
      );

      setState(() {
        _videoControllers[index] = videoController;
        _chewieControllers[index] = chewieController;
      });
    } catch (e) {
      if (mounted) {
        showErrorSnackBar(context, "Failed to initialize video player: $e");
      }
    }
  }

  Future<void> _preloadPlayer(int index) async {
    if (index < 0 || index >= _videoFiles.length) return;
    if (_videoControllers.containsKey(index)) return;
    await _initializePlayer(index);
  }

  Future<File?> _getCachedVideo(String cacheKey) async {
    try {
      final cacheDir = await getTemporaryDirectory();
      final videoDir = Directory('${cacheDir.path}/video_cache');
      if (!await videoDir.exists()) {
        await videoDir.create(recursive: true);
      }
      final cachedFile = File('${videoDir.path}/$cacheKey.mp4');
      if (await cachedFile.exists()) return cachedFile;
      return null;
    } catch (_) {
      return null;
    }
  }

  Future<File> _saveVideoToCache(String cacheKey, Uint8List data) async {
    final cacheDir = await getTemporaryDirectory();
    final videoDir = Directory('${cacheDir.path}/video_cache');
    if (!await videoDir.exists()) {
      await videoDir.create(recursive: true);
    }
    final file = File('${videoDir.path}/$cacheKey.mp4');
    await file.writeAsBytes(data);
    return file;
  }

  void _checkVideoEnd(int index) {
    if (!_videoControllers.containsKey(index)) return;

    final controller = _videoControllers[index]!;
    if (!controller.value.isInitialized) return;

    final isEnded = controller.value.position >= controller.value.duration;
    final isNotPlaying = !controller.value.isPlaying;

    if (isEnded && isNotPlaying && index == _currentIndex) {
      if (_autoPlayNext && _currentIndex < _videoFiles.length - 1) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            _pageController.nextPage(
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
            );
          }
        });
      }
    }
  }

  void _onPageChanged(int index) {
    setState(() => _currentIndex = index);

    for (var i = 0; i < _videoFiles.length; i++) {
      if (i != index && _videoControllers.containsKey(i)) {
        _videoControllers[i]!.pause();
      }
    }

    if (_videoControllers.containsKey(index)) {
      _videoControllers[index]!.play();
    }

    if (index < _videoFiles.length - 1) _preloadPlayer(index + 1);
    if (index > 0) _preloadPlayer(index - 1);
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(backgroundColor: Colors.black);
    }

    return PopScope(
      canPop: true,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) {
          // Pause all videos before popping
          for (var controller in _videoControllers.values) {
            try {
              if (controller.value.isInitialized) {
                await controller.pause();
              }
            } catch (_) {}
          }
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: SafeArea(
          child: Stack(
            children: [
              PageView.builder(
                controller: _pageController,
                onPageChanged: _onPageChanged,
                itemCount: _videoFiles.length,
                itemBuilder:
                    (context, index) => Container(
                      color: Colors.black,
                      child:
                          _chewieControllers.containsKey(index)
                              ? Chewie(controller: _chewieControllers[index]!)
                              : const Center(
                                child: CircularProgressIndicator(
                                  color: Colors.white,
                                ),
                              ),
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    WakelockPlus.disable();

    // Dispose chewie controllers first
    for (var controller in _chewieControllers.values) {
      try {
        controller.dispose();
      } catch (_) {}
    }
    _chewieControllers.clear();

    // Then dispose video controllers
    for (var controller in _videoControllers.values) {
      try {
        controller.dispose();
      } catch (_) {}
    }
    _videoControllers.clear();

    // Finally dispose page controller
    try {
      _pageController.dispose();
    } catch (_) {}

    super.dispose();
  }
}
