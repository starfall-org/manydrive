import 'package:cached_network_image/cached_network_image.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:manydrive/core/utils/snackbar.dart';
import 'package:manydrive/features/drive/data/datasources/remote/google_photos_datasource.dart';
import 'package:manydrive/features/drive/domain/repositories/google_photos_repository.dart';
import 'package:manydrive/features/drive/presentation/pages/photos/album_detail_page.dart';
import 'package:manydrive/features/drive/presentation/pages/photos/photo_viewer_page.dart';
import 'package:manydrive/injection_container.dart';

class GooglePhotosPage extends StatefulWidget {
  const GooglePhotosPage({super.key});

  @override
  State<GooglePhotosPage> createState() => GooglePhotosPageState();
}

class GooglePhotosPageState extends State<GooglePhotosPage>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final GooglePhotosRepository _photosRepository = injector.googlePhotosRepository;

  List<GooglePhotoItem> _mediaItems = [];
  List<GooglePhotoAlbum> _albums = [];
  String? _itemsPageToken;
  String? _albumsPageToken;

  bool _isLoadingItems = true;
  bool _isLoadingAlbums = true;
  bool _isLoadingMoreItems = false;
  bool _isLoadingMoreAlbums = false;
  String? _itemsError;
  String? _albumsError;

  final ScrollController _photosScrollController = ScrollController();
  final ScrollController _albumsScrollController = ScrollController();

  void clearAndReload() {
    setState(() {
      _mediaItems = [];
      _albums = [];
      _itemsPageToken = null;
      _albumsPageToken = null;
      _isLoadingItems = true;
      _isLoadingAlbums = true;
      _itemsError = null;
      _albumsError = null;
    });
    _loadPhotos();
    _loadAlbums();
  }

  void refresh() {
    _loadPhotos();
    _loadAlbums();
  }

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _photosScrollController.addListener(_onPhotosScroll);
    _albumsScrollController.addListener(_onAlbumsScroll);
    _loadPhotos();
    _loadAlbums();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _photosScrollController.dispose();
    _albumsScrollController.dispose();
    super.dispose();
  }

  void _onPhotosScroll() {
    if (_photosScrollController.position.pixels >=
        _photosScrollController.position.maxScrollExtent - 300) {
      _loadPhotos(loadMore: true);
    }
  }

  void _onAlbumsScroll() {
    if (_albumsScrollController.position.pixels >=
        _albumsScrollController.position.maxScrollExtent - 300) {
      _loadAlbums(loadMore: true);
    }
  }

  Future<void> _loadPhotos({bool loadMore = false}) async {
    if (loadMore) {
      if (_itemsPageToken == null || _isLoadingMoreItems || _isLoadingItems) {
        return;
      }
      setState(() => _isLoadingMoreItems = true);
    } else {
      setState(() {
        _isLoadingItems = true;
        _itemsError = null;
      });
    }

    try {
      final page = await _photosRepository.getMediaItems(
        pageToken: loadMore ? _itemsPageToken : null,
      );
      if (mounted) {
        setState(() {
          if (loadMore) {
            _mediaItems.addAll(page.items);
          } else {
            _mediaItems = page.items;
          }
          _itemsPageToken = page.nextPageToken;
          _isLoadingItems = false;
          _isLoadingMoreItems = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          if (!loadMore) _itemsError = e.toString();
          _isLoadingItems = false;
          _isLoadingMoreItems = false;
        });
      }
    }
  }

  Future<void> _loadAlbums({bool loadMore = false}) async {
    if (loadMore) {
      if (_albumsPageToken == null || _isLoadingMoreAlbums || _isLoadingAlbums) {
        return;
      }
      setState(() => _isLoadingMoreAlbums = true);
    } else {
      setState(() {
        _isLoadingAlbums = true;
        _albumsError = null;
      });
    }

    try {
      final page = await _photosRepository.getAlbums(
        pageToken: loadMore ? _albumsPageToken : null,
      );
      if (mounted) {
        setState(() {
          if (loadMore) {
            _albums.addAll(page.items);
          } else {
            _albums = page.items;
          }
          _albumsPageToken = page.nextPageToken;
          _isLoadingAlbums = false;
          _isLoadingMoreAlbums = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          if (!loadMore) _albumsError = e.toString();
          _isLoadingAlbums = false;
          _isLoadingMoreAlbums = false;
        });
      }
    }
  }

  Future<void> _uploadMedia() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'gif', 'mp4', 'mov'],
      );

      if (result != null && result.files.single.path != null) {
        final path = result.files.single.path!;
        String? selectedAlbumId;

        // Prompt user if they want to choose an album
        if (_albums.isNotEmpty && mounted) {
          selectedAlbumId = await showDialog<String>(
            context: context,
            builder: (context) {
              return AlertDialog(
                title: const Text('Select Target Album'),
                content: SizedBox(
                  width: 300,
                  height: 250,
                  child: ListView.builder(
                    itemCount: _albums.length + 1,
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        return ListTile(
                          title: const Text('Main Google Photos Library'),
                          onTap: () => Navigator.pop(context, null),
                        );
                      }
                      final album = _albums[index - 1];
                      return ListTile(
                        title: Text(album.title),
                        onTap: () => Navigator.pop(context, album.id),
                      );
                    },
                  ),
                ),
              );
            },
          );
        }

        if (!mounted) return;
        showSuccessSnackBar(context, 'Uploading media item...');

        await _photosRepository.uploadMediaItem(path, albumId: selectedAlbumId);

        if (mounted) {
          showSuccessSnackBar(context, 'Media uploaded successfully!');
          _loadPhotos();
        }
      }
    } catch (e) {
      if (mounted) {
        showErrorSnackBar(context, 'Failed to upload media: $e');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: Column(
        children: [
          Material(
            color: colorScheme.surfaceContainerLow,
            child: Row(
              children: [
                Expanded(
                  child: TabBar(
                    controller: _tabController,
                    indicatorSize: TabBarIndicatorSize.tab,
                    labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    unselectedLabelStyle: const TextStyle(fontWeight: FontWeight.normal, fontSize: 13),
                    tabs: const [
                      Tab(icon: Icon(Icons.photo), text: 'Photos'),
                      Tab(icon: Icon(Icons.photo_album), text: 'Albums'),
                    ],
                  ),
                ),

              ],
            ),
          ),
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                _buildPhotosTab(),
                _buildAlbumsTab(),
              ],
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _uploadMedia,
        child: const Icon(Icons.add_a_photo),
      ),
    );
  }

  Widget _buildPhotosTab() {
    if (_isLoadingItems) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_itemsError != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Error loading photos: $_itemsError'),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: _loadPhotos,
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    if (_mediaItems.isEmpty) {
      return Center(
        child: Text(
          'No photos found in Google Photos',
          style: TextStyle(
            color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.5),
          ),
        ),
      );
    }

    return GridView.builder(
      controller: _photosScrollController,
      padding: const EdgeInsets.all(8),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
      ),
      itemCount: _mediaItems.length + (_isLoadingMoreItems ? 1 : 0),
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
                  photosRepository: _photosRepository,
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
    );
  }

  Widget _buildAlbumsTab() {
    if (_isLoadingAlbums) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_albumsError != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Error loading albums: $_albumsError'),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: _loadAlbums,
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    if (_albums.isEmpty) {
      return Center(
        child: Text(
          'No albums found',
          style: TextStyle(
            color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.5),
          ),
        ),
      );
    }

    return ListView.builder(
      controller: _albumsScrollController,
      padding: const EdgeInsets.all(8),
      itemCount: _albums.length + (_isLoadingMoreAlbums ? 1 : 0),
      itemBuilder: (context, index) {
        if (index >= _albums.length) {
          return const Padding(
            padding: EdgeInsets.all(16),
            child: Center(
              child: SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          );
        }
        final album = _albums[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            leading: album.coverPhotoBaseUrl != null
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: CachedNetworkImage(
                      imageUrl: '${album.coverPhotoBaseUrl}=w100-h100',
                      width: 50,
                      height: 50,
                      fit: BoxFit.cover,
                      placeholder: (context, url) => Container(
                        width: 50,
                        height: 50,
                        color: Colors.grey.shade300,
                      ),
                      errorWidget: (context, url, error) => Container(
                        width: 50,
                        height: 50,
                        color: Colors.grey.shade300,
                        child: const Icon(Icons.photo_album),
                      ),
                    ),
                  )
                : const Icon(Icons.photo_album, size: 40),
            title: Text(album.title, style: const TextStyle(fontWeight: FontWeight.bold)),
            subtitle: Text('${album.mediaItemsCount} items'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => AlbumDetailPage(
                    album: album,
                    photosRepository: _photosRepository,
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }
}
