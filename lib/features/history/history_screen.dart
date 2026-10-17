import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart' as intl;
import 'package:dns_guardian/l10n/app_localizations.dart';
import 'package:dns_guardian/services/security_controller.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  List<Map<String, dynamic>> _events = [];
  String? _filter;
  bool _loaded = false;
  bool _loadFailed = false;
  bool _chainBroken = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _load();
  }

  Future<void> _load() async {
    final c = context.read<SecurityController>();
    if (mounted) {
      setState(() {
        _loaded = false;
        _loadFailed = false;
      });
    }
    try {
      final events = await c.getHistory();
      final broken = await c.verifyChain();
      if (!mounted) return;
      setState(() {
        _events = events;
        _chainBroken = broken >= 0;
        _loaded = true;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadFailed = true;
        _loaded = true;
      });
    }
  }

  String _label(AppLocalizations l, String type) {
    switch (type) {
      case 'PRIVATE_DNS_ACCESS_ATTEMPT':
        return l.eventPrivateDnsAccess;
      case 'DNS_CONFIG_CHANGED':
        return l.eventDnsChanged;
      case 'APP_INFO_ATTEMPT':
        return l.eventAppInfo;
      case 'UNINSTALL_ATTEMPT':
        return l.eventUninstall;
      case 'ACCESSIBILITY_SETTINGS_ACCESS':
        return l.eventAccessibilitySettings;
      case 'DEVICE_ADMIN_DEACTIVATE_ATTEMPT':
        return l.eventDeviceAdminDeactivate;
      case 'COMPONENT_DISABLED':
        return l.eventComponentDisabled;
      case 'RECOVERY_FAILED':
        return l.eventRecoveryFailed;
      case 'UNAUTHORIZED_ACCESS_ATTEMPT':
        return l.eventUnauthorizedAccess;
      case 'STARTUP_RECOVERY_CHECK':
        return l.eventStartup;
      case 'MONITORING_GAP_SUSPECTED':
        return l.eventMonitoringGap;
      default:
        return type;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final types = <String>{for (final e in _events) e['type'] as String}.toList();
    final shown = _events
        .where((e) => _filter == null || e['type'] == _filter)
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: Text(l.historyTitle),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: !_loaded
          ? const Center(child: CircularProgressIndicator())
          : _loadFailed
              ? Center(child: Text(l.historyLoadError))
              : Column(
              children: [
                if (_chainBroken)
                  Container(
                    width: double.infinity,
                    color: Colors.red.withOpacity(0.12),
                    padding: const EdgeInsets.all(12),
                    child: Text(l.historyIntegrityWarning),
                  ),
                // Filter chips (no clear-history button — requirement)
                SizedBox(
                  height: 48,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    children: [
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: ChoiceChip(
                          label: Text(l.filterAll),
                          selected: _filter == null,
                          onSelected: (_) => setState(() => _filter = null),
                        ),
                      ),
                      for (final t in types)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: ChoiceChip(
                            label: Text(_label(l, t)),
                            selected: _filter == t,
                            onSelected: (_) =>
                                setState(() => _filter = _filter == t ? null : t),
                          ),
                        ),
                    ],
                  ),
                ),
                const Divider(),
                Expanded(
                  child: shown.isEmpty
                      ? Center(child: Text(l.noHistory))
                      : ListView.builder(
                          itemCount: shown.length,
                          itemBuilder: (ctx, i) {
                            final e = shown[i];
                            final ts = DateTime.fromMillisecondsSinceEpoch(
                                e['tsMillis'] as int);
                            final verified = e['responseVerified'] as bool? ?? false;
                            return ListTile(
                              leading: Icon(
                                verified ? Icons.verified : Icons.history,
                                color: verified ? Colors.green : Colors.grey,
                              ),
                              title: Text(_label(l, e['type'] as String)),
                              subtitle: Text(
                                '${intl.DateFormat.yMd().add_Hms().format(ts)}\n'
                                '${e['source'] ?? ''} ${e['component'] ?? ''}',
                              ),
                              trailing: e['responseAttempted'] != null
                                  ? Text(
                                      '${l.attempted}: ${e['responseAttempted']}',
                                      style: Theme.of(context).textTheme.bodySmall,
                                    )
                                  : null,
                              isThreeLine: true,
                            );
                          },
                        ),
                ),
                Container(
                  color: Colors.amber.withOpacity(0.12),
                  padding: const EdgeInsets.all(8),
                  child: Text(
                    '${l.limitationNote} ${l.historyRetentionNote}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
              ],
            ),
    );
  }
}
