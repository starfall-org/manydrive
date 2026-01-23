import 'package:manydrive/core/services/settings_service.dart';
import 'package:manydrive/features/drive/data/datasources/local/credential_local_datasource.dart';
import 'package:manydrive/features/drive/data/datasources/local/file_cache_datasource.dart';
import 'package:manydrive/features/drive/data/datasources/remote/google_drive_datasource.dart';
import 'package:manydrive/features/drive/data/datasources/remote/s3_drive_datasource.dart';
import 'package:manydrive/features/drive/data/repositories/credential_repository_impl.dart';
import 'package:manydrive/features/drive/data/repositories/drive_repository_impl.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';
import 'package:manydrive/features/drive/domain/repositories/drive_repository.dart';

/// Simple dependency injection container
/// In a larger app, consider using get_it or riverpod
class InjectionContainer {
  static final InjectionContainer _instance = InjectionContainer._internal();
  factory InjectionContainer() => _instance;
  InjectionContainer._internal();

  // Data sources
  late final GoogleDriveDataSource _googleDriveDataSource;
  late final S3DriveDataSource _s3DriveDataSource;
  late final CredentialLocalDataSource _credentialLocalDataSource;
  late final FileCacheDataSource _fileCacheDataSource;

  // Services
  late final SettingsService settingsService;

  // Repositories
  late final DriveRepository driveRepository;
  late final CredentialRepository credentialRepository;

  bool _initialized = false;

  Future<void> init() async {
    if (_initialized) return;

    // Initialize services
    settingsService = SettingsService();
    await settingsService.init();

    // Initialize data sources
    _googleDriveDataSource = GoogleDriveDataSource();
    _s3DriveDataSource = S3DriveDataSource();
    _credentialLocalDataSource = CredentialLocalDataSource();
    _fileCacheDataSource = FileCacheDataSource();

    // Initialize repositories
    driveRepository = DriveRepositoryImpl(
      _googleDriveDataSource,
      _s3DriveDataSource,
      _fileCacheDataSource,
    );

    credentialRepository = CredentialRepositoryImpl(_credentialLocalDataSource);

    _initialized = true;
  }
}

/// Global instance for easy access
final injector = InjectionContainer();
