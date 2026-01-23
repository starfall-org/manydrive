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
  showDialog(
    context: context,
    builder:
        (context) => _LoginDialog(
          credentialRepository: credentialRepository,
          onLogin: onLogin,
        ),
  );
}

class _LoginDialog extends StatefulWidget {
  final CredentialRepository credentialRepository;
  final Function(String) onLogin;

  const _LoginDialog({
    required this.credentialRepository,
    required this.onLogin,
  });

  @override
  State<_LoginDialog> createState() => _LoginDialogState();
}

class _LoginDialogState extends State<_LoginDialog>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _serviceAccountController = TextEditingController();

  final _s3EndpointController = TextEditingController();
  final _s3AccessKeyController = TextEditingController();
  final _s3SecretKeyController = TextEditingController();
  final _s3BucketController = TextEditingController();
  final _s3RegionController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _serviceAccountController.dispose();
    _s3EndpointController.dispose();
    _s3AccessKeyController.dispose();
    _s3SecretKeyController.dispose();
    _s3BucketController.dispose();
    _s3RegionController.dispose();
    super.dispose();
  }

  bool _isValidServiceAccount(String content) {
    try {
      final data = jsonDecode(content);
      return data is Map<String, dynamic> && data.containsKey('client_email');
    } catch (_) {
      return false;
    }
  }

  void _handleLogin() {
    if (_tabController.index == 0) {
      // Service Account
      final content = _serviceAccountController.text;
      if (_isValidServiceAccount(content)) {
        try {
          widget.credentialRepository.saveCredential(content);
          final creds = jsonDecode(content);
          widget.onLogin(creds['client_email']);
          Navigator.of(context).pop();
        } catch (e) {
          _showErrorDialog(context, e.toString());
        }
      } else {
        _showErrorDialog(
          context,
          'The JSON file is not valid or does not contain "client_email".',
        );
      }
    } else {
      // S3
      if (_s3EndpointController.text.isEmpty ||
          _s3AccessKeyController.text.isEmpty ||
          _s3SecretKeyController.text.isEmpty ||
          _s3BucketController.text.isEmpty) {
        _showErrorDialog(context, 'Please fill all required S3 fields.');
        return;
      }

      final s3Data = {
        's3_endpoint': _s3EndpointController.text,
        's3_access_key': _s3AccessKeyController.text,
        's3_secret_key': _s3SecretKeyController.text,
        's3_bucket': _s3BucketController.text,
        's3_region': _s3RegionController.text,
      };

      try {
        final jsonString = jsonEncode(s3Data);
        widget.credentialRepository.saveCredential(jsonString);
        widget.onLogin(_s3EndpointController.text);
        Navigator.of(context).pop();
      } catch (e) {
        _showErrorDialog(context, e.toString());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Login'),
      contentPadding: const EdgeInsets.fromLTRB(24, 8, 24, 0),
      content: SizedBox(
        width: 400,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TabBar(
              controller: _tabController,
              tabs: const [Tab(text: 'Service Account'), Tab(text: 'S3')],
            ),
            const SizedBox(height: 16),
            SizedBox(
              height: 320,
              child: TabBarView(
                controller: _tabController,
                children: [
                  _buildServiceAccountTab(),
                  _buildS3Tab(),
                ],
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        TextButton(onPressed: _handleLogin, child: const Text('OK')),
      ],
    );
  }

  Widget _buildServiceAccountTab() {
    return Column(
      children: [
        Expanded(
          child: TextField(
            controller: _serviceAccountController,
            maxLines: null,
            expands: true,
            textAlignVertical: TextAlignVertical.top,
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

                if (_isValidServiceAccount(content)) {
                  _serviceAccountController.text = content;
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
    );
  }

  Widget _buildS3Tab() {
    return SingleChildScrollView(
      child: Column(
        children: [
          TextField(
            controller: _s3EndpointController,
            decoration: const InputDecoration(
              labelText: 'Endpoint (e.g. https://s3.amazonaws.com)',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _s3AccessKeyController,
            decoration: const InputDecoration(
              labelText: 'Access Key',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _s3SecretKeyController,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: 'Secret Key',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _s3BucketController,
            decoration: const InputDecoration(
              labelText: 'Bucket Name',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _s3RegionController,
            decoration: const InputDecoration(
              labelText: 'Region (optional)',
              border: OutlineInputBorder(),
            ),
          ),
        ],
      ),
    );
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
