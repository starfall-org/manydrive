import 'dart:io';
import 'dart:typed_data';

import 'package:chewie/chewie.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/services/notification_service.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
import 'package:manydrive/injection_container.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

class VideoPlayerPage extends StatefulWidget {
  final DriveFile file;
  final List<DriveFile>? allFiles;
  final DriveRepository driveRepository;
  final VideoPlayerController? initialController;

  const VideoPlayerPage({
    super.key,
    required this.file,
    required this.driveRepository,
    this.allFiles,
    this.initialController,
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
  final Set<int> _initializingIndexes = {};

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
    if (mounted) {
      setState(() {
        _autoPlayNext = prefs.getBool('video_autoplay_next') ?? true;
      });
    }
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

      if (mounted) {
        setState(() => _isLoading = false);
      }

      await _initializePlayer(_currentIndex, initialController: widget.initialController);

      if (_currentIndex < _videoFiles.length - 1) {
        _preloadPlayer(_currentIndex + 1);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _videoFiles = [widget.file];
          _currentIndex = 0;
        });
      }
      _pageController = PageController(initialPage: 0);
      await _initializePlayer(0);
    }
  }

  Future<void> _initializePlayer(int index, {VideoPlayerController? initialController}) async {
    if (index < 0 || index >= _videoFiles.length) return;

    if (_videoControllers.containsKey(index) ||
        _initializingIndexes.contains(index)) {
      return;
    }

    _initializingIndexes.add(index);

    try {
      final driveFile = _videoFiles[index];
      VideoPlayerController videoController;

      if (initialController != null && index == _currentIndex) {
        videoController = initialController;
      } else {
        final settings = injector.settingsService;

        if (widget.driveRepository.isS3 && settings.useS3PresignedUrl) {
          final presignedUrl = await widget.driveRepository.getPresignedUrl(driveFile);
          if (presignedUrl != null) {
            videoController = VideoPlayerController.networkUrl(Uri.parse(presignedUrl));
          } else {
            final videoData = await widget.driveRepository.getFileBytes(driveFile);
            final cacheKey = driveFile.id.replaceAll('/', '_');
            final savedFile = await _saveVideoToCache(cacheKey, videoData);
            videoController = VideoPlayerController.file(savedFile);
          }
        } else {
          final cacheKey = driveFile.id.replaceAll('/', '_');
          final cachedFile = await _getCachedVideo(cacheKey);

          if (cachedFile != null && await cachedFile.exists() && settings.enableFileCache) {
            videoController = VideoPlayerController.file(cachedFile);
          } else {
            final videoData = await widget.driveRepository.getFileBytes(driveFile);
            if (settings.enableFileCache) {
              final savedFile = await _saveVideoToCache(cacheKey, videoData);
              videoController = VideoPlayerController.file(savedFile);
            } else {
              final cacheDir = await getTemporaryDirectory();
              final tempFile = File('${cacheDir.path}/temp_${DateTime.now().millisecondsSinceEpoch}.mp4');
              await tempFile.writeAsBytes(videoData);
              videoController = VideoPlayerController.file(tempFile);
            }
          }
        }

        await videoController.initialize();
      }

      if (!mounted || (index - _currentIndex).abs() > 1) {
        _initializingIndexes.remove(index);
        await videoController.dispose();
        return;
      }

      videoController.addListener(() => _checkVideoEnd(index));

      final chewieController = ChewieController(
        videoPlayerController: videoController,
        aspectRatio:
            videoController.value.aspectRatio > 0
                ? videoController.value.aspectRatio
                : 16 / 9,
        autoPlay: index == _currentIndex,
        looping: false,
        autoInitialize: true,
        showControlsOnInitialize: false,
        errorBuilder: (context, errorMessage) => const SizedBox.shrink(),
        additionalOptions: (context) {
          return [
            OptionItem(
              onTap: (ctx) {
                Navigator.of(ctx).pop();
                if (mounted) {
                  setState(() => _autoPlayNext = !_autoPlayNext);
                  _saveAutoPlaySetting(_autoPlayNext);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        _autoPlayNext ? 'Autoplay: ON' : 'Autoplay: OFF',
                      ),
                      duration: const Duration(seconds: 1),
                      behavior: SnackBarBehavior.floating,
                    ),
                  );
                }
              },
              iconData:
                  _autoPlayNext ? Icons.playlist_play : Icons.playlist_remove,
              title: _autoPlayNext ? 'Disable Autoplay' : 'Enable Autoplay',
            ),
          ];
        },
      );

      if (mounted) {
        setState(() {
          _videoControllers[index] = videoController;
          _chewieControllers[index] = chewieController;
          _initializingIndexes.remove(index);
        });

        if (index == _currentIndex) {
          videoController.play();
          _updateNotification(driveFile.name, true);
        }
      } else {
        _initializingIndexes.remove(index);
        chewieController.dispose();
        videoController.dispose();
      }
    } catch (e) {
      _initializingIndexes.remove(index);
      if (mounted) {
        showErrorSnackBar(context, "Failed to initialize video: $e");
      }
    }
  }

  void _stopAndClose() {
    MiniPlayerController().close();
    NotificationService().cancel(9991);
    Navigator.of(context).pop();
  }

  void _updateNotification(String title, bool isPlaying) {
    NotificationService().showMediaNotification(
      id: 9991,
      title: title,
      subtitle: 'Video Playing',
      isPlaying: isPlaying,
    );
  }

  Future<void> _preloadPlayer(int index) async {
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
    if (index != _currentIndex) return;

    if (!_videoControllers.containsKey(index)) return;

    final controller = _videoControllers[index]!;
    if (!controller.value.isInitialized) return;

    final isEnded = controller.value.position >= controller.value.duration;
    final isNotPlaying = !controller.value.isPlaying;

    if (isEnded && isNotPlaying) {
      if (_autoPlayNext && _currentIndex < _videoFiles.length - 1) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted && _currentIndex == index) {
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
    if (!mounted) return;

    setState(() => _currentIndex = index);

    _videoControllers.forEach((i, controller) {
      if (i != index) {
        controller.pause();
      }
    });

    if (_videoControllers.containsKey(index)) {
      _videoControllers[index]!.play();
      _updateNotification(_videoFiles[index].name, true);
    } else {
      _initializePlayer(index);
    }

    final indexesToDispose =
        _videoControllers.keys.where((i) => (i - index).abs() > 2).toList();
    for (var i in indexesToDispose) {
      _disposeControllerAt(i);
    }

    if (index < _videoFiles.length - 1) _preloadPlayer(index + 1);
    if (index > 0) _preloadPlayer(index - 1);
  }

  void _disposeControllerAt(int index) {
    if (_chewieControllers.containsKey(index)) {
      _chewieControllers[index]!.dispose();
      _chewieControllers.remove(index);
    }
    if (_videoControllers.containsKey(index)) {
      _videoControllers[index]!.dispose();
      _videoControllers.remove(index);
    }
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
          final currentController = _videoControllers[_currentIndex];
          if (currentController != null) {
            MiniPlayerController().showVideo(
              controller: currentController,
              title: _videoFiles[_currentIndex].name,
              file: _videoFiles[_currentIndex],
              driveRepository: widget.driveRepository,
              allFiles: _videoFiles,
            );
          }

          final bgPlayback = injector.settingsService.backgroundPlayback;
          if (!bgPlayback) {
            for (var entry in _videoControllers.entries) {
              try {
                if (entry.value.value.isInitialized) {
                  await entry.value.pause();
                }
              } catch (_) {}
            }
          }
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(
          backgroundColor: Colors.black,
          foregroundColor: Colors.white,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: Colors.white),
            onPressed: () => Navigator.of(context).pop(),
            tooltip: 'Thu nhỏ',
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.close_rounded, color: Colors.white, size: 26),
              tooltip: 'Đóng hoàn toàn',
              onPressed: _stopAndClose,
            ),
          ],
          title: Text(
            _videoFiles.isNotEmpty ? _videoFiles[_currentIndex].name : widget.file.name,
            style: const TextStyle(fontSize: 16),
          ),
        ),
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

    final miniController = MiniPlayerController();
    bool keepCurrent =
        miniController.isShowing &&
        _videoControllers.values.contains(miniController.videoController);

    for (var entry in _chewieControllers.entries) {
      try {
        entry.value.dispose();
      } catch (_) {}
    }
    _chewieControllers.clear();

    for (var entry in _videoControllers.entries) {
      try {
        if (!keepCurrent || entry.value != miniController.videoController) {
          entry.value.dispose();
        }
      } catch (_) {}
    }
    _videoControllers.clear();

    try {
      _pageController.dispose();
    } catch (_) {}

    NotificationService().cancel(9991);

    super.dispose();
  }
}
