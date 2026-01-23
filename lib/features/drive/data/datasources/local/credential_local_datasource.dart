import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Local data source for credentials using SharedPreferences
class CredentialLocalDataSource {
  static const _credentialsKey = 'gauth_credentials';
  static const _selectedEmailKey = 'selected_client_email';

  Future<String?> getSelectedEmail() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_selectedEmailKey);
  }

  Future<void> setSelectedEmail(String email) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_selectedEmailKey, email);
  }

  Future<void> saveCredential(String jsonString) async {
    final prefs = await SharedPreferences.getInstance();
    List<String> credList = prefs.getStringList(_credentialsKey) ?? [];

    final newCred = jsonDecode(jsonString);
    bool updated = false;

    final String identifier =
        newCred['client_email'] ?? newCred['s3_endpoint'] ?? 'unknown';

    for (int i = 0; i < credList.length; i++) {
      final existingCred = jsonDecode(credList[i]);
      final String existingIdentifier =
          existingCred['client_email'] ?? existingCred['s3_endpoint'] ?? '';
      if (existingIdentifier == identifier) {
        credList[i] = jsonString;
        updated = true;
        break;
      }
    }

    if (!updated) {
      credList.add(jsonString);
    }

    await prefs.setStringList(_credentialsKey, credList);
  }

  Future<Map<String, dynamic>?> getCredential(String emailOrEndpoint) async {
    final credentials = await listCredentials();
    for (final cred in credentials) {
      final identifier = cred['client_email'] ?? cred['s3_endpoint'];
      if (identifier == emailOrEndpoint) {
        return cred;
      }
    }
    return null;
  }

  Future<String?> deleteCredential(String emailOrEndpoint) async {
    final prefs = await SharedPreferences.getInstance();
    List<String> credList = prefs.getStringList(_credentialsKey) ?? [];

    for (final cred in credList) {
      final jsonCred = jsonDecode(cred);
      final identifier = jsonCred['client_email'] ?? jsonCred['s3_endpoint'];
      if (identifier == emailOrEndpoint) {
        credList.remove(cred);
        await prefs.setStringList(_credentialsKey, credList);
        return emailOrEndpoint;
      }
    }
    return null;
  }

  Future<List<Map<String, dynamic>>> listCredentials() async {
    final prefs = await SharedPreferences.getInstance();
    final credList = prefs.getStringList(_credentialsKey) ?? [];
    return credList
        .map((cred) => jsonDecode(cred) as Map<String, dynamic>)
        .toList();
  }
}
