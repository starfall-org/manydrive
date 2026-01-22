import 'dart:io';
import 'dart:typed_data';

import 'package:chewie/chewie.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/domain/entities/drive_file.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';
import 'package:manydrive/features/drive/presentation/state/mini_player_controller.dart';
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

      await _initializePlayer(_currentIndex);

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

  Future<void> _initializePlayer(int index) async {
    // 1. Kiểm tra nếu index nằm ngoài dải hợp lệ
    if (index < 0 || index >= _videoFiles.length) return;

    // 2. Nếu đã có controller hoặc đang khởi tạo rồi thì bỏ qua
    if (_videoControllers.containsKey(index) ||
        _initializingIndexes.contains(index)) {
      return;
    }

    _initializingIndexes.add(index);

    try {
      final driveFile = _videoFiles[index];
      final videoData = await widget.driveRepository.getFileBytes(driveFile);

      // Kiểm tra xem index còn hợp lệ sau khi await (người dùng có thể đã lướt đi quá xa)
      if (!mounted || (index - _currentIndex).abs() > 1) {
        _initializingIndexes.remove(index);
        return;
      }

      final cacheKey = driveFile.id;
      final cachedFile = await _getCachedVideo(cacheKey);

      VideoPlayerController videoController;
      if (cachedFile != null && await cachedFile.exists()) {
        videoController = VideoPlayerController.file(cachedFile);
      } else {
        final savedFile = await _saveVideoToCache(cacheKey, videoData);
        videoController = VideoPlayerController.file(savedFile);
      }

      await videoController.initialize();

      if (!mounted || (index - _currentIndex).abs() > 1) {
        _initializingIndexes.remove(index);
        await videoController.dispose();
        return;
      }

      // Đảm bảo index này vẫn là index chúng ta muốn khởi tạo
      videoController.addListener(() => _checkVideoEnd(index));

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

        // Nếu vừa khởi tạo đúng trang hiện tại thì cho phát luôn
        if (index == _currentIndex) {
          videoController.play();
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
    // Chỉ xử lý nếu trang kết thúc là trang hiện tại đang xem
    if (index != _currentIndex) return;

    if (!_videoControllers.containsKey(index)) return;

    final controller = _videoControllers[index]!;
    if (!controller.value.isInitialized) return;

    final isEnded = controller.value.position >= controller.value.duration;
    final isNotPlaying = !controller.value.isPlaying;

    if (isEnded && isNotPlaying) {
      if (_autoPlayNext && _currentIndex < _videoFiles.length - 1) {
        // Sử dụng post frame callback để tránh lỗi khi đang build UI
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

    // 1. Tạm dừng tất cả các video không phải trang hiện tại
    _videoControllers.forEach((i, controller) {
      if (i != index) {
        controller.pause();
      }
    });

    // 2. Phát video trang hiện tại nếu đã sẵn sàng
    if (_videoControllers.containsKey(index)) {
      _videoControllers[index]!.play();
    } else {
      // Nếu chưa có thì khởi tạo ngay lập tức
      _initializePlayer(index);
    }

    // 3. Quản lý bộ nhớ: Giải phóng các controller ở quá xa (cách > 2 trang)
    final indexesToDispose =
        _videoControllers.keys.where((i) => (i - index).abs() > 2).toList();
    for (var i in indexesToDispose) {
      _disposeControllerAt(i);
    }

    // 4. Preload các trang lân cận
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
            );
          }

          // Pause OTHER videos before popping
          for (var entry in _videoControllers.entries) {
            if (entry.key != _currentIndex) {
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

    // Dispose chewie controllers first
    for (var entry in _chewieControllers.entries) {
      try {
        entry.value.dispose();
      } catch (_) {}
    }
    _chewieControllers.clear();

    // Then dispose video controllers, except the one in mini player
    for (var entry in _videoControllers.entries) {
      try {
        if (!keepCurrent || entry.value != miniController.videoController) {
          entry.value.dispose();
        }
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
