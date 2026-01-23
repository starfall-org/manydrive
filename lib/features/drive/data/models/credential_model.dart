import 'package:manydrive/features/drive/domain/entities/credential.dart';

/// Data model for Credential with serialization capabilities
class CredentialModel extends Credential {
  const CredentialModel({
    super.clientEmail,
    super.projectId,
    super.s3Endpoint,
    super.s3AccessKey,
    super.s3SecretKey,
    super.s3Bucket,
    super.s3Region,
    required super.rawData,
  });

  /// Create from JSON map
  factory CredentialModel.fromJson(Map<String, dynamic> json) {
    return CredentialModel(
      clientEmail: json['client_email'],
      projectId: json['project_id'],
      s3Endpoint: json['s3_endpoint'],
      s3AccessKey: json['s3_access_key'],
      s3SecretKey: json['s3_secret_key'],
      s3Bucket: json['s3_bucket'],
      s3Region: json['s3_region'],
      rawData: json,
    );
  }

  /// Convert to domain entity
  Credential toEntity() {
    return Credential(
      clientEmail: clientEmail,
      projectId: projectId,
      s3Endpoint: s3Endpoint,
      s3AccessKey: s3AccessKey,
      s3SecretKey: s3SecretKey,
      s3Bucket: s3Bucket,
      s3Region: s3Region,
      rawData: rawData,
    );
  }
}
