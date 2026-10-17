import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:dns_guardian/l10n/app_localizations.dart';
import 'package:dns_guardian/services/security_controller.dart';

/// First-run onboarding: explains the permissions and limitations honestly.
class OnboardingScreen extends StatelessWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = Provider.of<SecurityController>(context);
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.appTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l.whatIsPrivateDns, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          _Card(
            icon: Icons.accessibility,
            title: l.enableAccessibility,
            body: l.accessibilityGuide,
            onTap: () => c.openAccessibilitySettings(),
          ),
          _Card(
            icon: Icons.admin_panel_settings,
            title: l.activateDeviceAdmin,
            body: l.deviceAdminGuide,
            onTap: () => c.openDeviceAdminSettings(),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.red.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(l.limitationNote),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => context.go('/enroll'),
            child: Text(l.enrollPassword),
          ),
        ],
      ),
    );
  }
}

class _Card extends StatelessWidget {
  final IconData icon;
  final String title;
  final String body;
  final VoidCallback onTap;
  const _Card({required this.icon, required this.title, required this.body, required this.onTap});

  @override
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.symmetric(vertical: 6),
        child: ListTile(
          leading: Icon(icon),
          title: Text(title),
          subtitle: Text(body),
          trailing: const Icon(Icons.open_in_new),
          onTap: onTap,
        ),
      );
}
