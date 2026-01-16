import 'package:manydrive/features/drive/data/datasources/local/credential_local_datasource.dart';
import 'package:manydrive/features/drive/data/models/credential_model.dart';
import 'package:manydrive/features/drive/domain/entities/credential.dart';
import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';

/// Implementation of CredentialRepository
class CredentialRepositoryImpl implements CredentialRepository {
  final CredentialLocalDataSource _localDataSource;

  CredentialRepositoryImpl(this._localDataSource);

  @override
  Future<String?> getSelectedEmail() {
    return _localDataSource.getSelectedEmail();
  }

  @override
  Future<void> setSelectedEmail(String email) {
    return _localDataSource.setSelectedEmail(email);
  }

  @override
  Future<void> saveCredential(String jsonString) {
    return _localDataSource.saveCredential(jsonString);
  }

  @override
  Future<Credential?> getCredential(String email) async {
    final data = await _localDataSource.getCredential(email);
    if (data == null) return null;
    return CredentialModel.fromJson(data).toEntity();
  }

  @override
  Future<String?> deleteCredential(String email) {
    return _localDataSource.deleteCredential(email);
  }

  @override
  Future<List<Credential>> listCredentials() async {
    final dataList = await _localDataSource.listCredentials();
    return dataList
        .map((data) => CredentialModel.fromJson(data).toEntity())
        .toList();
  }
}
