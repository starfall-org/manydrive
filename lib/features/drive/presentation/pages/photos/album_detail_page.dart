import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/features/drive/data/datasources/remote/google_photos_datasource.dart';
import 'package:manydrive/features/drive/domain/repositories/google_photos_repository.dart';
import 'package:manydrive/features/drive/presentation/pages/photos/photo_viewer_page.dart';

class AlbumDetailPage extends StatefulWidget {
  final GooglePhotoAlbum album;
  final GooglePhotosRepository photosRepository;

  const AlbumDetailPage({
    super.key,
    required this.album,
    required this.photosRepository,
  });

  @override
  State<AlbumDetailPage> createState() => _AlbumDetailPageState();
}

class _AlbumDetailPageState extends State<AlbumDetailPage> {
  List<GooglePhotoItem> _mediaItems = [];
  bool _isLoading = true;
  bool _isLoadingMore = false;
  String? _pageToken;
  String? _error;
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    _loadAlbumItems();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 300) {
      _loadAlbumItems(loadMore: true);
    }
  }

  Future<void> _loadAlbumItems({bool loadMore = false}) async {
    if (loadMore) {
      if (_pageToken == null || _isLoadingMore || _isLoading) return;
      setState(() => _isLoadingMore = true);
    } else {
      setState(() {
        _isLoading = true;
        _error = null;
      });
    }

    try {
      final page = await widget.photosRepository.getAlbumMediaItems(
        widget.album.id,
        pageToken: loadMore ? _pageToken : null,
      );
      if (mounted) {
        setState(() {
          if (loadMore) {
            _mediaItems.addAll(page.items);
          } else {
            _mediaItems = page.items;
          }
          _pageToken = page.nextPageToken;
          _isLoading = false;
          _isLoadingMore = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          if (!loadMore) _error = e.toString();
          _isLoading = false;
          _isLoadingMore = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.album.title),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadAlbumItems,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text('Error: $_error'))
              : _mediaItems.isEmpty
                  ? Center(
                      child: Text(
                        'Album is empty',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.5),
                        ),
                      ),
                    )
                  : GridView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.all(8),
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 3,
                        crossAxisSpacing: 8,
                        mainAxisSpacing: 8,
                      ),
                      itemCount: _mediaItems.length + (_isLoadingMore ? 1 : 0),
                      itemBuilder: (context, index) {
                        if (index >= _mediaItems.length) {
                          return const Center(
                            child: SizedBox(
                              width: 24,
                              height: 24,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          );
                        }
                        final item = _mediaItems[index];
                        return GestureDetector(
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => PhotoViewerPage(
                                  item: item,
                                  photosRepository: widget.photosRepository,
                                ),
                              ),
                            );
                          },
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              ClipRRect(
                                borderRadius: BorderRadius.circular(8),
                                child: CachedNetworkImage(
                                  imageUrl: item.thumbnailUrl,
                                  fit: BoxFit.cover,
                                  placeholder: (context, url) => Container(
                                    color: Theme.of(context).colorScheme.surfaceContainerHighest,
                                    child: const Center(
                                      child: SizedBox(
                                        width: 20,
                                        height: 20,
                                        child: CircularProgressIndicator(strokeWidth: 2),
                                      ),
                                    ),
                                  ),
                                  errorWidget: (context, url, error) => Container(
                                    color: Theme.of(context).colorScheme.surfaceContainerHighest,
                                    child: const Icon(Icons.broken_image),
                                  ),
                                ),
                              ),
                              if (item.isVideo)
                                const Center(
                                  child: Icon(
                                    Icons.play_circle_fill,
                                    color: Colors.white,
                                    size: 32,
                                  ),
                                ),
                            ],
                          ),
                        );
                      },
                    ),
    );
  }
}
