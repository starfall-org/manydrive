import 'dart:io';
import 'dart:typed_data';

import 'package:chewie/chewie.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/app/common/snackbar.dart';
import 'package:manydrive/app/models/file_model.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';

class VideoPlayerWidget extends StatefulWidget {
  final FileModel fileModel;
  final List<FileModel>? allFiles;

  const VideoPlayerWidget({super.key, required this.fileModel, this.allFiles});

  @override
  VideoPlayerWidgetState createState() => VideoPlayerWidgetState();
}

class VideoPlayerWidgetState extends State<VideoPlayerWidget> {
  late PageController _pageController;
  List<FileModel> _videoFiles = [];
  int _currentIndex = 0;
  bool _isLoading = true;
  bool _autoPlayNext = true;

  // Cache cho video players
  final Map<int, VideoPlayerController> _videoControllers = {};
  final Map<int, ChewieController> _chewieControllers = {};

  @override
  void initState() {
    super.initState();
    _loadAutoPlaySetting();
    _loadVideoList();
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
      // Nếu có allFiles được truyền vào, lọc video từ đó
      if (widget.allFiles != null && widget.allFiles!.isNotEmpty) {
        _videoFiles =
            widget.allFiles!
                .where(
                  (file) => file.file.mimeType?.startsWith('video/') == true,
                )
                .toList();
      } else {
        // Nếu không có, chỉ dùng video hiện tại
        _videoFiles = [widget.fileModel];
      }

      // Tìm index của video hiện tại
      _currentIndex = _videoFiles.indexWhere(
        (file) => file.file.id == widget.fileModel.file.id,
      );

      if (_currentIndex == -1) {
        _currentIndex = 0;
        _videoFiles = [widget.fileModel];
      }

      // Khởi tạo PageController với index hiện tại
      _pageController = PageController(initialPage: _currentIndex);

      setState(() {
        _isLoading = false;
      });

      // Khởi tạo player cho video hiện tại
      await _initializePlayer(_currentIndex);

      // Preload video tiếp theo nếu có
      if (_currentIndex < _videoFiles.length - 1) {
        _preloadPlayer(_currentIndex + 1);
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _videoFiles = [widget.fileModel];
        _currentIndex = 0;
      });
      _pageController = PageController(initialPage: 0);
      await _initializePlayer(0);
    }
  }

  Future<void> _initializePlayer(int index) async {
    // Nếu đã có controller, không cần tạo lại
    if (_videoControllers.containsKey(index) &&
        _videoControllers[index]!.value.isInitialized) {
      return;
    }

    try {
      final videoData = await _videoFiles[index].getBytes();
      final cacheKey =
          _videoFiles[index].file.id ?? _generateCacheKey(videoData);

      final cachedFile = await _getCachedVideo(cacheKey);

      VideoPlayerController videoController;
      if (cachedFile != null && await cachedFile.exists()) {
        videoController = VideoPlayerController.file(cachedFile);
      } else {
        final savedFile = await _saveVideoToCache(cacheKey, videoData);
        videoController = VideoPlayerController.file(savedFile);
      }

      await videoController.initialize();

      // Thêm listener sau khi initialize để tránh lỗi
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
      );

      setState(() {
        _videoControllers[index] = videoController;
        _chewieControllers[index] = chewieController;
      });
    } catch (e) {
      if (mounted) {
        showErrorSnackBar(
          context,
          "Failed to initialize video player: $e",
          duration: const Duration(seconds: 5),
        );
      }
    }
  }

  Future<void> _preloadPlayer(int index) async {
    if (index < 0 || index >= _videoFiles.length) return;
    if (_videoControllers.containsKey(index)) return;

    await _initializePlayer(index);
  }

  // Tạo cache key từ hash của video data
  String _generateCacheKey(Uint8List data) {
    final hash = sha256.convert(data);
    return hash.toString();
  }

  // Lấy video từ cache nếu có
  Future<File?> _getCachedVideo(String cacheKey) async {
    try {
      final cacheDir = await getTemporaryDirectory();
      final videoDir = Directory('${cacheDir.path}/video_cache');

      if (!await videoDir.exists()) {
        await videoDir.create(recursive: true);
      }

      final cachedFile = File('${videoDir.path}/$cacheKey.mp4');

      if (await cachedFile.exists()) {
        return cachedFile;
      }

      return null;
    } catch (e) {
      return null;
    }
  }

  // Lưu video vào cache
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
        // Tự động chuyển sang video tiếp theo
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            _pageController.nextPage(
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
            );
          }
        });
      }
      // Đã loại bỏ cơ chế tự động thoát khi hết video
    }
  }

  void _onPageChanged(int index) {
    setState(() {
      _currentIndex = index;
    });

    // Pause video trước
    for (var i = 0; i < _videoFiles.length; i++) {
      if (i != index && _videoControllers.containsKey(i)) {
        _videoControllers[i]!.pause();
      }
    }

    // Play video hiện tại
    if (_videoControllers.containsKey(index)) {
      _videoControllers[index]!.play();
    }

    // Preload video tiếp theo
    if (index < _videoFiles.length - 1) {
      _preloadPlayer(index + 1);
    }
    // Preload video trước
    if (index > 0) {
      _preloadPlayer(index - 1);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return Scaffold(
        backgroundColor: Colors.black,
        body: const Center(
          child: CircularProgressIndicator(color: Colors.white),
        ),
      );
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            PageView.builder(
              controller: _pageController,
              onPageChanged: _onPageChanged,
              itemCount: _videoFiles.length,
              itemBuilder: (context, index) {
                return Container(
                  color: Colors.black,
                  child: Center(
                    child:
                        _chewieControllers.containsKey(index)
                            ? Chewie(controller: _chewieControllers[index]!)
                            : const CircularProgressIndicator(
                              color: Colors.white,
                            ),
                  ),
                );
              },
            ),

            // Top bar với indicator và nút autoplay
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.black.withValues(alpha: 0.7),
                      Colors.transparent,
                    ],
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 8,
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      // Video counter
                      if (_videoFiles.length > 1)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.black54,
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Text(
                            '${_currentIndex + 1} / ${_videoFiles.length}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 14,
                            ),
                          ),
                        )
                      else
                        const SizedBox.shrink(),

                      // Autoplay toggle button
                      if (_videoFiles.length > 1)
                        Container(
                          decoration: BoxDecoration(
                            color: Colors.black54,
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: IconButton(
                            icon: Icon(
                              _autoPlayNext
                                  ? Icons.playlist_play
                                  : Icons.playlist_remove,
                              color: Colors.white,
                            ),
                            tooltip:
                                _autoPlayNext
                                    ? 'Autoplay: ON'
                                    : 'Autoplay: OFF',
                            onPressed: () {
                              setState(() {
                                _autoPlayNext = !_autoPlayNext;
                              });
                              _saveAutoPlaySetting(_autoPlayNext);

                              // Show snackbar
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(
                                    _autoPlayNext
                                        ? 'Autoplay enabled'
                                        : 'Autoplay disabled',
                                  ),
                                  duration: const Duration(seconds: 1),
                                  behavior: SnackBarBehavior.floating,
                                ),
                              );
                            },
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _pageController.dispose();

    // Dispose tất cả controllers
    for (var controller in _videoControllers.values) {
      controller.dispose();
    }
    for (var controller in _chewieControllers.values) {
      controller.dispose();
    }

    _videoControllers.clear();
    _chewieControllers.clear();

    super.dispose();
  }
}
