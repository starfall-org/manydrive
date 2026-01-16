import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

void showAboutAppDialog(BuildContext context) {
  showDialog(
    context: context,
    builder:
        (context) => Padding(
          padding: const EdgeInsets.all(16.0),
          child: AlertDialog(
            title: const Text("About"),
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                ListTile(
                  leading: const Icon(Icons.code),
                  title: const Text("GitHub"),
                  onTap: () => _launchURL('https://github.com/starfall-org'),
                ),
              ],
            ),
            actions: [
              TextButton(
                child: const Text("Close"),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
        ),
  );
}

Future<void> _launchURL(String url) async {
  if (await canLaunchUrl(Uri.parse(url))) {
    await launchUrl(Uri.parse(url));
  } else {
    throw 'Cannot open URL $url';
  }
}
