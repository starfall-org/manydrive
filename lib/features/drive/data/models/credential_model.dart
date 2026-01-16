import 'package:manydrive/features/drive/domain/entities/credential.dart';

/// Data model for Credential with serialization capabilities
class CredentialModel extends Credential {
  const CredentialModel({
    required super.clientEmail,
    required super.projectId,
    required super.rawData,
  });

  /// Create from JSON map
  factory CredentialModel.fromJson(Map<String, dynamic> json) {
    return CredentialModel(
      clientEmail: json['client_email'] ?? '',
      projectId: json['project_id'] ?? '',
      rawData: json,
    );
  }

  /// Convert to domain entity
  Credential toEntity() {
    return Credential(
      clientEmail: clientEmail,
      projectId: projectId,
      rawData: rawData,
    );
  }
}
