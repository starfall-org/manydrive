import 'package:manydrive/features/drive/domain/entities/credential.dart';

/// Abstract repository for managing credentials
abstract class CredentialRepository {
  /// Get the currently selected credential email
  Future<String?> getSelectedEmail();

  /// Set the selected credential
  Future<void> setSelectedEmail(String email);

  /// Save a new credential
  Future<void> saveCredential(String jsonString);

  /// Get credential by email
  Future<Credential?> getCredential(String email);

  /// Delete a credential
  Future<String?> deleteCredential(String email);

  /// List all credentials
  Future<List<Credential>> listCredentials();
}
