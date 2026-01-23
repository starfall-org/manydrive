/// Domain entity representing a Drive credential (Google Service Account or S3)
class Credential {
  final String? clientEmail;
  final String? projectId;
  final String? s3Endpoint;
  final String? s3AccessKey;
  final String? s3SecretKey;
  final String? s3Bucket;
  final String? s3Region;
  final Map<String, dynamic> rawData;

  const Credential({
    this.clientEmail,
    this.projectId,
    this.s3Endpoint,
    this.s3AccessKey,
    this.s3SecretKey,
    this.s3Bucket,
    this.s3Region,
    required this.rawData,
  });

  bool get isS3 => rawData.containsKey('s3_endpoint') || s3Endpoint != null;

  String get username {
    if (isS3) {
      return s3Bucket ?? 'S3 Bucket';
    }
    return clientEmail?.split('@').first ?? 'Unknown';
  }
}
