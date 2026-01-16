/// Domain entity representing a Google Service Account credential
class Credential {
  final String clientEmail;
  final String projectId;
  final Map<String, dynamic> rawData;

  const Credential({
    required this.clientEmail,
    required this.projectId,
    required this.rawData,
  });

  String get username => clientEmail.split('@').first;
}
