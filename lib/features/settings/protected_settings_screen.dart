import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:dns_guardian/l10n/app_localizations.dart';
import 'package:dns_guardian/services/security_controller.dart';

/// Protected settings — only reachable after authentication (router guard).
/// Any direct navigation without auth is rejected and logged.
class ProtectedSettingsScreen extends StatefulWidget {
  const ProtectedSettingsScreen({super.key});

  @override
  State<ProtectedSettingsScreen> createState() => _ProtectedSettingsScreenState();
}

class _ProtectedSettingsScreenState extends State<ProtectedSettingsScreen> {
  @override
  Widget build(BuildContext context) {
    final c = Provider.of<SecurityController>(context);
    final l = AppLocalizations.of(context);

    // Defense in depth: even if router guard fails, reject here.
    if (!c.canAccessProtected()) {
      // Log unauthorized attempt and bounce to lock.
      c.logEvent(
        type: 'UNAUTHORIZED_ACCESS_ATTEMPT',
        source: 'ui:ProtectedSettingsScreen',
        component: 'protected_settings',
        diagnostic: 'rejected unauthenticated access',
      );
      WidgetsBinding.instance.addPostFrameCallback((_) => context.go('/lock'));
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(title: Text(l.settings)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          FutureBuilder<bool>(
            future: c.isLocalDnsVpnRunning(),
            builder: (context, snapshot) {
              final running = snapshot.data == true;
              return SwitchListTile(
                secondary: Icon(
                  Icons.vpn_lock,
                  color: running ? Colors.green : Colors.orange,
                ),
                title: Text(l.localDnsVpn),
                subtitle: Text(
                  running ? l.localDnsVpnEnabled : l.localDnsVpnGuide,
                ),
                value: running,
                onChanged: snapshot.connectionState == ConnectionState.waiting
                    ? null
                    : (enabled) async {
                        final verified =
                            await c.setLocalDnsVpnEnabled(enabled);
                        if (!mounted) return;
                        setState(() {});
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              verified
                                  ? (enabled
                                      ? l.localDnsVpnStarted
                                      : l.localDnsVpnStopped)
                                  : l.actionFailed,
                            ),
                          ),
                        );
                      },
              );
            },
          ),
          Padding(
            padding: const EdgeInsetsDirectional.only(
              start: 16,
              end: 16,
              bottom: 8,
            ),
            child: Text(
              l.localDnsVpnAlwaysOnNote,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.accessibility),
            title: Text(l.enableAccessibility),
            subtitle: Text(l.accessibilityGuide),
            trailing: const Icon(Icons.open_in_new),
            onTap: () async {
              final opened = await c.openAccessibilitySettings();
              if (!mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(opened ? l.accessibilityOpenedInstructions : l.actionFailed),
                ),
              );
            },
          ),
          FutureBuilder<bool>(
            future: c.canDrawOverlays(),
            builder: (context, snapshot) {
              final enabled = snapshot.data == true;
              return ListTile(
                leading: Icon(
                  Icons.layers,
                  color: enabled ? Colors.green : Colors.orange,
                ),
                title: Text(l.enableBlockingOverlay),
                subtitle: Text(
                  enabled ? l.blockingOverlayEnabled : l.blockingOverlayGuide,
                ),
                trailing: Icon(enabled ? Icons.verified : Icons.open_in_new),
                onTap: enabled
                    ? null
                    : () async {
                        final opened = await c.openOverlayPermissionSettings();
                        if (!mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              opened ? l.blockingOverlayInstructions : l.actionFailed,
                            ),
                          ),
                        );
                      },
              );
            },
          ),
          FutureBuilder<bool>(
            future: c.hasWriteSecureSettings(),
            builder: (context, snapshot) {
              final granted = snapshot.data == true;
              return ListTile(
                leading: Icon(
                  Icons.dns,
                  color: granted ? Colors.green : Colors.orange,
                ),
                title: Text(l.secureSettingsPermission),
                subtitle: Text(
                  granted
                      ? l.secureSettingsEnabled
                      : l.secureSettingsAdbGuide,
                ),
                trailing: Icon(
                  granted ? Icons.verified : Icons.warning_amber,
                  color: granted ? Colors.green : Colors.orange,
                ),
              );
            },
          ),
          ListTile(
            leading: const Icon(Icons.admin_panel_settings),
            title: Text(l.activateDeviceAdmin),
            subtitle: Text(l.deviceAdminGuide),
            trailing: const Icon(Icons.open_in_new),
            onTap: () async {
              final active = await c.openDeviceAdminSettings();
              if (!mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    active ? l.deviceAdminActivated : l.deviceAdminNotActivated,
                  ),
                ),
              );
            },
          ),
          const Divider(),
          ListTile(
            leading: Icon(c.state.protectionPaused ? Icons.play_arrow : Icons.pause),
            title: Text(
              c.state.protectionPaused ? l.resumeProtection : l.pauseProtection,
            ),
            subtitle: Text(l.pausedForMaintenance),
            onTap: () async {
              final wasPaused = c.state.protectionPaused;
              final verified = wasPaused
                  ? await c.resumeProtection()
                  : await c.pauseProtection();
              if (!mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    verified
                        ? (wasPaused ? l.resumeSucceeded : l.pauseSucceeded)
                        : l.actionFailed,
                  ),
                ),
              );
            },
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.orange.withOpacity(0.12),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(l.limitationNote),
          ),
          // NOTE: No "Clear History" button is provided by design (requirement 11).
          const SizedBox(height: 12),
          if (c.hasPassword)
            Text(
              l.passwordConfigured,
              style: Theme.of(context).textTheme.bodySmall,
            ),
        ],
      ),
    );
  }
}
