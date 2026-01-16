import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import 'package:manydrive/features/drive/domain/repositories/credential_repository.dart';

void showLoginDialog(
  BuildContext context,
  CredentialRepository credentialRepository,
  Function(String) onLogin,
) {
  final controller = TextEditingController();

  showDialog(
    context: context,
    builder:
        (context) => AlertDialog(
          title: const Text('Login'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ConstrainedBox(
                  constraints: const BoxConstraints(maxHeight: 300),
                  child: TextField(
                    controller: controller,
                    maxLines: null,
                    decoration: const InputDecoration(
                      hintText: 'Enter or select JSON file',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                ElevatedButton(
                  onPressed: () async {
                    try {
                      final result = await FilePicker.platform.pickFiles(
                        type: FileType.custom,
                        allowedExtensions: ['json'],
                      );

                      if (result != null) {
                        final file = File(result.files.single.path!);
                        final content = await file.readAsString();

                        if (_isValidJson(content)) {
                          controller.text = content;
                        } else {
                          _showErrorDialog(
                            context,
                            'Invalid JSON file or missing "client_email" key.',
                          );
                        }
                      }
                    } catch (e) {
                      _showErrorDialog(context, 'Error reading file: $e');
                    }
                  },
                  child: const Text('Select JSON file'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                if (_isValidJson(controller.text)) {
                  Navigator.of(context).pop();
                  try {
                    credentialRepository.saveCredential(controller.text);
                    final creds = jsonDecode(controller.text);
                    onLogin(creds['client_email']);
                  } catch (e) {
                    _showErrorDialog(context, e.toString());
                  }
                } else {
                  _showErrorDialog(
                    context,
                    'The JSON file is not valid or does not contain "client_email".',
                  );
                }
              },
              child: const Text('OK'),
            ),
          ],
        ),
  ).then((_) => controller.dispose());
}

bool _isValidJson(String content) {
  try {
    final data = jsonDecode(content);
    return data is Map<String, dynamic> && data.containsKey('client_email');
  } catch (_) {
    return false;
  }
}

void _showErrorDialog(BuildContext context, String message) {
  showDialog(
    context: context,
    builder:
        (context) => AlertDialog(
          title: const Text('Error'),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('OK'),
            ),
          ],
        ),
  );
}
