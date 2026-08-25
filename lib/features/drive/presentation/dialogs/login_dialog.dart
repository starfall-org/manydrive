import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_sign_in/google_sign_in.dart';
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
  int _selectedTabIndex = 0;
  final _serviceAccountController = TextEditingController();

  final _s3EndpointController = TextEditingController();
  final _s3AccessKeyController = TextEditingController();
  final _s3SecretKeyController = TextEditingController();
  final _s3BucketController = TextEditingController();
  final _s3RegionController = TextEditingController();

  final _oauthEmailController = TextEditingController();
  final _oauthAccessTokenController = TextEditingController();
  final _oauthRefreshTokenController = TextEditingController();
  final _oauthWebClientIdController = TextEditingController();

  @override
  void dispose() {
    _serviceAccountController.dispose();
    _s3EndpointController.dispose();
    _s3AccessKeyController.dispose();
    _s3SecretKeyController.dispose();
    _s3BucketController.dispose();
    _s3RegionController.dispose();
    _oauthEmailController.dispose();
    _oauthAccessTokenController.dispose();
    _oauthRefreshTokenController.dispose();
    _oauthWebClientIdController.dispose();
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

  Future<void> _handleGoogleNativeSignIn() async {
    try {
      final webClientId = _oauthWebClientIdController.text.trim();
      final googleSignIn = GoogleSignIn(
        serverClientId: webClientId.isNotEmpty ? webClientId : null,
        scopes: [
          'https://www.googleapis.com/auth/drive',
          'https://www.googleapis.com/auth/photoslibrary.readonly',
          'https://www.googleapis.com/auth/photoslibrary',
        ],
      );

      final account = await googleSignIn.signIn();
      if (account != null) {
        final auth = await account.authentication;
        final credData = {
          'auth_type': 'oauth',
          'client_email': account.email,
          'access_token': auth.accessToken,
          'id_token': auth.idToken,
          'display_name': account.displayName,
          if (webClientId.isNotEmpty) 'server_client_id': webClientId,
        };
        widget.credentialRepository.saveCredential(jsonEncode(credData));
        widget.onLogin(account.email);
        if (mounted) Navigator.of(context).pop();
      }
    } catch (e) {
      if (mounted) {
        final errStr = e.toString();
        if (errStr.contains('10') || errStr.contains('sign_in_failed') || errStr.contains('DEVELOPER_ERROR')) {
          _showOAuthSetupGuideDialog(context, errStr);
        } else {
          _showErrorDialog(context, 'Google Sign-In failed: $errStr');
        }
      }
    }
  }

  void _showOAuthSetupGuideDialog(BuildContext context, String errorDetails) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: const [
            Icon(Icons.warning_amber_rounded, color: Colors.orange),
            SizedBox(width: 8),
            Expanded(child: Text('Lỗi Google Sign-In (Code 10)', style: TextStyle(fontSize: 18))),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Lỗi Code 10 (DEVELOPER_ERROR) xảy ra khi ứng dụng chưa được cấu hình đúng trên Google Cloud Console.',
                style: TextStyle(fontWeight: FontWeight.w500),
              ),
              const SizedBox(height: 12),
              const Text('Các bước cần thực hiện để sửa lỗi:', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              const Text('1. Package Name của ứng dụng:'),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(8),
                margin: const EdgeInsets.symmetric(vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.grey.shade200,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Row(
                  children: [
                    const Expanded(
                      child: Text(
                        'com.starfall.gsadrive',
                        style: TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.bold, color: Colors.black87),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.copy, size: 18, color: Colors.black87),
                      onPressed: () {
                        Clipboard.setData(const ClipboardData(text: 'com.starfall.gsadrive'));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Đã sao chép Package Name!')),
                        );
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              const Text('2. Thêm SHA-1 Certificate Fingerprint:'),
              const Text(
                '• Mở Google Cloud Console -> APIs & Services -> Credentials.\n'
                '• Tạo hoặc chỉnh sửa OAuth 2.0 Client ID loại Android.\n'
                '• Điền Package Name trên và thêm mã SHA-1 fingerprint của keystore (Debug/Release).',
                style: TextStyle(fontSize: 13, color: Colors.black87),
              ),
              const SizedBox(height: 8),
              const Text('3. Web Client ID (serverClientId):'),
              const Text(
                '• Tạo thêm một OAuth 2.0 Client ID loại Web Application.\n'
                '• Sao chép Client ID đó và dán vào ô "Web Client ID (optional)" bên dưới trước khi bấm đăng nhập.',
                style: TextStyle(fontSize: 13, color: Colors.black87),
              ),
              const SizedBox(height: 12),
              ExpansionTile(
                title: const Text('Chi tiết lỗi kĩ thuật', style: TextStyle(fontSize: 13, color: Colors.grey)),
                children: [
                  SelectableText(
                    errorDetails,
                    style: const TextStyle(fontSize: 12, fontFamily: 'monospace', color: Colors.redAccent),
                  ),
                ],
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Đã hiểu'),
          ),
        ],
      ),
    );
  }

  void _handleLogin() {
    if (_selectedTabIndex == 0) {
      if (_oauthEmailController.text.isEmpty ||
          _oauthAccessTokenController.text.isEmpty) {
        _showErrorDialog(
          context,
          'Vui lòng nhập Email và Access Token hoặc bấm nút Sign in with Google.',
        );
        return;
      }

      final oauthData = {
        'auth_type': 'oauth',
        'client_email': _oauthEmailController.text.trim(),
        'access_token': _oauthAccessTokenController.text.trim(),
        'refresh_token': _oauthRefreshTokenController.text.trim().isEmpty
            ? null
            : _oauthRefreshTokenController.text.trim(),
      };

      try {
        final jsonString = jsonEncode(oauthData);
        widget.credentialRepository.saveCredential(jsonString);
        widget.onLogin(_oauthEmailController.text.trim());
        Navigator.of(context).pop();
      } catch (e) {
        _showErrorDialog(context, e.toString());
      }
    } else if (_selectedTabIndex == 1) {
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
          'File JSON không hợp lệ hoặc không chứa "client_email".',
        );
      }
    } else {
      if (_s3EndpointController.text.isEmpty ||
          _s3AccessKeyController.text.isEmpty ||
          _s3SecretKeyController.text.isEmpty ||
          _s3BucketController.text.isEmpty) {
        _showErrorDialog(context, 'Vui lòng điền đầy đủ các trường S3 bắt buộc.');
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
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Container(
        constraints: const BoxConstraints(maxWidth: 460),
        padding: const EdgeInsets.all(24),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Icon(Icons.lock_open_rounded, color: colorScheme.primary, size: 28),
                  const SizedBox(width: 12),
                  Text(
                    'Đăng nhập tài khoản',
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment<int>(
                    value: 0,
                    label: Text('Google OAuth', style: TextStyle(fontSize: 12)),
                    icon: Icon(Icons.g_mobiledata, size: 20),
                  ),
                  ButtonSegment<int>(
                    value: 1,
                    label: Text('Service Account', style: TextStyle(fontSize: 12)),
                    icon: Icon(Icons.key, size: 16),
                  ),
                  ButtonSegment<int>(
                    value: 2,
                    label: Text('S3 Storage', style: TextStyle(fontSize: 12)),
                    icon: Icon(Icons.cloud_outlined, size: 16),
                  ),
                ],
                selected: {_selectedTabIndex},
                onSelectionChanged: (Set<int> newSelection) {
                  setState(() {
                    _selectedTabIndex = newSelection.first;
                  });
                },
              ),
              const SizedBox(height: 20),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 200),
                child: _buildCurrentTabContent(),
              ),
              const SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Hủy'),
                  ),
                  const SizedBox(width: 8),
                  FilledButton.icon(
                    onPressed: _handleLogin,
                    icon: const Icon(Icons.check, size: 18),
                    label: const Text('Đăng nhập'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCurrentTabContent() {
    switch (_selectedTabIndex) {
      case 0:
        return _buildGoogleOAuthTab();
      case 1:
        return _buildServiceAccountTab();
      case 2:
        return _buildS3Tab();
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _buildGoogleOAuthTab() {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      key: const ValueKey(0),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        OutlinedButton.icon(
          style: OutlinedButton.styleFrom(
            minimumSize: const Size.fromHeight(50),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            side: BorderSide(color: colorScheme.outline),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            backgroundColor: colorScheme.surfaceContainerLow,
          ),
          onPressed: _handleGoogleNativeSignIn,
          icon: Image.network(
            'https://upload.wikimedia.org/wikipedia/commons/5/53/Google_%22G%22_Logo.svg',
            height: 22,
            errorBuilder: (_, __, ___) => Icon(Icons.account_circle, color: colorScheme.primary),
          ),
          label: Text(
            'Đăng nhập bằng Google',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w600,
              color: colorScheme.onSurface,
            ),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _oauthWebClientIdController,
          decoration: InputDecoration(
            labelText: 'Web Client ID (serverClientId - không bắt buộc)',
            hintText: 'xxxx.apps.googleusercontent.com',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
            prefixIcon: const Icon(Icons.qr_code, size: 20),
          ),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            const Expanded(child: Divider()),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text(
                'HOẶC Nhập OAuth Web Token',
                style: TextStyle(fontSize: 11, color: colorScheme.outline, fontWeight: FontWeight.bold),
              ),
            ),
            const Expanded(child: Divider()),
          ],
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _oauthEmailController,
          decoration: InputDecoration(
            labelText: 'Google Account Email',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
            prefixIcon: const Icon(Icons.email_outlined, size: 20),
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _oauthAccessTokenController,
          decoration: InputDecoration(
            labelText: 'OAuth Access Token',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
            prefixIcon: const Icon(Icons.key, size: 20),
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _oauthRefreshTokenController,
          decoration: InputDecoration(
            labelText: 'Refresh Token (không bắt buộc)',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
            prefixIcon: const Icon(Icons.refresh_outlined, size: 20),
          ),
        ),
      ],
    );
  }

  Widget _buildServiceAccountTab() {
    return Column(
      key: const ValueKey(1),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: 140,
          child: TextField(
            controller: _serviceAccountController,
            maxLines: null,
            expands: true,
            textAlignVertical: TextAlignVertical.top,
            decoration: InputDecoration(
              hintText: 'Dán nội dung JSON file ở đây...',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
          ),
        ),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          style: OutlinedButton.styleFrom(
            minimumSize: const Size.fromHeight(44),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
          onPressed: () async {
            try {
              final result = await FilePicker.platform.pickFiles(
                type: FileType.custom,
                allowedExtensions: ['json'],
              );

              if (result != null && result.files.single.path != null) {
                final file = File(result.files.single.path!);
                final content = await file.readAsString();

                if (_isValidServiceAccount(content)) {
                  setState(() {
                    _serviceAccountController.text = content;
                  });
                } else {
                  _showErrorDialog(
                    context,
                    'File JSON không hợp lệ hoặc thiếu thuộc tính "client_email".',
                  );
                }
              }
            } catch (e) {
              _showErrorDialog(context, 'Lỗi đọc file: $e');
            }
          },
          icon: const Icon(Icons.folder_open),
          label: const Text('Chọn file JSON từ thiết bị'),
        ),
      ],
    );
  }

  Widget _buildS3Tab() {
    return Column(
      key: const ValueKey(2),
      children: [
        TextField(
          controller: _s3EndpointController,
          decoration: InputDecoration(
            labelText: 'Endpoint (VD: https://s3.amazonaws.com)',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _s3AccessKeyController,
          decoration: InputDecoration(
            labelText: 'Access Key',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _s3SecretKeyController,
          obscureText: true,
          decoration: InputDecoration(
            labelText: 'Secret Key',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _s3BucketController,
          decoration: InputDecoration(
            labelText: 'Bucket Name',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _s3RegionController,
          decoration: InputDecoration(
            labelText: 'Region (không bắt buộc)',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            isDense: true,
          ),
        ),
      ],
    );
  }
}

void _showErrorDialog(BuildContext context, String message) {
  showDialog(
    context: context,
    builder:
        (context) => AlertDialog(
          title: const Text('Thông báo lỗi'),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Đóng'),
            ),
          ],
        ),
  );
}
