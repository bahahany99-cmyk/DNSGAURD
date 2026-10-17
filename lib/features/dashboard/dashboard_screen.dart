import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart' as intl;
import 'package:dns_guardian/core/security_state.dart';
import 'package:dns_guardian/l10n/app_localizations.dart';
import 'package:dns_guardian/services/security_controller.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  Color _colorFor(LayerState s) {
    switch (s) {
      case LayerState.activeVerified:
        return Colors.green;
      case LayerState.activeUnverified:
        return Colors.amber;
      case LayerState.inactive:
        return Colors.red;
      case LayerState.permissionRequired:
        return Colors.orange;
      case LayerState.recoveryRequired:
        return Colors.deepOrange;
      case LayerState.unsupported:
        return Colors.grey;
      case LayerState.unknown:
        return Colors.amber;
      case LayerState.error:
        return Colors.redAccent;
    }
  }

  IconData _iconFor(LayerState s) {
    switch (s) {
      case LayerState.activeVerified:
        return Icons.verified_user;
      case LayerState.activeUnverified:
        return Icons.check_circle_outline;
      case LayerState.inactive:
        return Icons.error;
      case LayerState.permissionRequired:
        return Icons.warning;
      case LayerState.recoveryRequired:
        return Icons.build;
      case LayerState.unsupported:
        return Icons.block;
      case LayerState.unknown:
        return Icons.help;
      case LayerState.error:
        return Icons.error_outline;
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = Provider.of<SecurityController>(context);
    final l = AppLocalizations.of(context);
    final overall = c.state.overall;

    return Scaffold(
      appBar: AppBar(
        title: Text(l.appTitle),
        actions: [
          IconButton(
            icon: const Icon(Icons.history),
            tooltip: l.historyTitle,
            onPressed: () => context.go('/history'),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: l.settings,
            onPressed: () => context.go('/settings'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => c.refreshState(),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              color: _colorFor(overall).withOpacity(0.12),
              child: ListTile(
                leading: Icon(_iconFor(overall), color: _colorFor(overall), size: 40),
                title: Text(l.overallAssessment, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: Text(_overallLabel(l, overall)),
              ),
            ),
            if (c.state.protectionPaused)
              Container(
                margin: const EdgeInsets.symmetric(vertical: 8),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.orange.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(l.pausedForMaintenance),
              ),
            const SizedBox(height: 8),
            ...c.state.layers.map((layer) => _LayerTile(layer: layer, l: l)),
            const SizedBox(height: 16),
            ExpansionTile(
              title: Text(l.learnMore),
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(l.limitationNote),
                ),
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(l.whatIsPrivateDns),
                ),
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(l.rebootRecoveryNote),
                ),
              ],
            ),
          ],
        ),
      ),
      floatingActionButton: c.isAuthenticated
          ? FloatingActionButton.extended(
              onPressed: () async {
                final wasPaused = c.state.protectionPaused;
                final verified = wasPaused
                    ? await c.resumeProtection()
                    : await c.pauseProtection();
                if (!context.mounted) return;
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
              label: Text(c.state.protectionPaused ? l.resumeProtection : l.pauseProtection),
              icon: Icon(c.state.protectionPaused ? Icons.play_arrow : Icons.pause),
            )
          : null,
    );
  }

  String _overallLabel(AppLocalizations l, LayerState s) {
    switch (s) {
      case LayerState.activeVerified:
        return l.protectionActive;
      case LayerState.activeUnverified:
        return l.activeUnverified;
      case LayerState.inactive:
        return l.protectionInactive;
      case LayerState.permissionRequired:
        return l.permissionRequired;
      case LayerState.recoveryRequired:
        return l.recoveryRequired;
      case LayerState.unsupported:
        return l.unsupported;
      case LayerState.unknown:
        return l.protectionUnknown;
      case LayerState.error:
        return l.queryError;
    }
  }
}

class _LayerTile extends StatelessWidget {
  final ProtectionLayer layer;
  final AppLocalizations l;
  const _LayerTile({required this.layer, required this.l});

  String _title() {
    switch (layer.id) {
      case 'private_dns_monitor':
        return l.privateDnsMonitor;
      case 'accessibility_service':
        return l.accessibilityService;
      case 'device_admin':
        return l.deviceAdmin;
      case 'app_management_monitor':
        return l.appManagementMonitor;
      case 'password_lock':
        return l.passwordLock;
      case 'security_history':
        return l.securityHistory;
      case 'recovery_boot':
        return l.recoveryBoot;
      default:
        return layer.id;
    }
  }

  String _stateLabel() {
    switch (layer.state) {
      case LayerState.activeVerified:
        return l.protectionActive;
      case LayerState.activeUnverified:
        return l.activeUnverified;
      case LayerState.inactive:
        return l.inactive;
      case LayerState.permissionRequired:
        return l.permissionRequired;
      case LayerState.recoveryRequired:
        return l.recoveryRequired;
      case LayerState.unsupported:
        return l.unsupported;
      case LayerState.unknown:
        return l.unknown;
      case LayerState.error:
        return l.queryError;
    }
  }

  @override
  Widget build(BuildContext context) {
    final stateColor = {
      LayerState.activeVerified: Colors.green,
      LayerState.activeUnverified: Colors.amber,
      LayerState.inactive: Colors.red,
      LayerState.permissionRequired: Colors.orange,
      LayerState.recoveryRequired: Colors.deepOrange,
      LayerState.unsupported: Colors.grey,
      LayerState.unknown: Colors.amber,
      LayerState.error: Colors.redAccent,
    }[layer.state]!;

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6),
      child: ListTile(
        leading: Icon(_iconForState(layer.state), color: stateColor),
        title: Text(_title()),
        subtitle: Text(_stateLabel()),
        trailing: layer.lastVerifiedAt != null
            ? Text(
                intl.DateFormat.Hm().format(
                  DateTime.fromMillisecondsSinceEpoch(layer.lastVerifiedAt!),
                ),
                style: Theme.of(context).textTheme.bodySmall,
              )
            : null,
      ),
    );
  }

  IconData _iconForState(LayerState s) {
    switch (s) {
      case LayerState.activeVerified:
        return Icons.verified;
      case LayerState.activeUnverified:
        return Icons.check_circle_outline;
      case LayerState.inactive:
        return Icons.error;
      case LayerState.permissionRequired:
        return Icons.warning;
      case LayerState.recoveryRequired:
        return Icons.build;
      case LayerState.unsupported:
        return Icons.block;
      case LayerState.unknown:
        return Icons.help;
      case LayerState.error:
        return Icons.error_outline;
    }
  }
}
