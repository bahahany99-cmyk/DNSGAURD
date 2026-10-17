import 'dart:async';
import 'package:flutter/material.dart';
import 'package:dns_guardian/core/security_state.dart';
import 'package:dns_guardian/core/platform_channel.dart';
import 'package:dns_guardian/data/event_repository.dart';
import 'package:dns_guardian/core/startup_diagnostics.dart';

/// Central app controller: holds the authoritative SecurityState, exposes it to
/// the UI via ChangeNotifier, and bridges native channels + event repository.
class SecurityController extends ChangeNotifier {
  final PlatformChannels _channels;
  final SecurityEventRepository _repo;

  SecurityState _state = SecurityState.initial();
  SecurityState get state => _state;

  bool _authenticated = false;
  bool get isAuthenticated => _authenticated;

  bool _hasPassword = false;
  bool get hasPassword => _hasPassword;

  StreamSubscription<dynamic>? _eventSubscription;

  SecurityController(this._repo, {PlatformChannels? channels})
      : _channels = channels ?? PlatformChannels();

  Future<void> init({void Function(String stage)? onStage}) async {
    onStage?.call('native_password_status');
    _hasPassword = await _channels
        .hasPasswordSet()
        .timeout(const Duration(seconds: 10));

    onStage?.call('native_security_state');
    await refreshState(timeout: const Duration(seconds: 10));

    onStage?.call('native_event_stream');
    // Subscription setup is best-effort. Stream failures are logged locally and
    // must not prevent the first usable screen from appearing.
    _eventSubscription ??= _channels.eventStream.listen(
      (_) => refreshState(),
      onError: (Object error, StackTrace stack) {
        StartupDiagnostics.error('native_event_stream', error, stack);
      },
    );
  }

  Future<void> refreshState({Duration? timeout}) async {
    final request = _channels.getSecurityState();
    _state = timeout == null ? await request : await request.timeout(timeout);
    notifyListeners();
  }

  Future<void> setAuthenticated(bool v) async {
    _authenticated = v;
    notifyListeners();
  }

  Future<bool> verifyPassword(String password) async {
    final ok = await _channels.verifyPassword(password);
    if (ok) {
      _authenticated = true;
      notifyListeners();
    }
    return ok;
  }

  Future<bool> enrollPassword(String password) async {
    final ok = await _channels.enrollPassword(password);
    if (ok) _hasPassword = true;
    notifyListeners();
    return ok;
  }

  Future<bool> devSetPassword(String password) async {
    final ok = await _channels.devSetPassword(password);
    if (ok) _hasPassword = true;
    notifyListeners();
    return ok;
  }

  /// Immediately lock (zero grace period) — called on app background.
  void lockNow() {
    _authenticated = false;
    notifyListeners();
  }

  Future<bool> pauseProtection() async {
    final persisted = await _channels.pauseProtection();
    await refreshState();
    return persisted && _state.protectionPaused;
  }

  Future<bool> resumeProtection() async {
    final persisted = await _channels.resumeProtection();
    await refreshState();
    return persisted && !_state.protectionPaused;
  }

  Future<bool> openAccessibilitySettings() async {
    final opened = await _channels.openAccessibilitySettings();
    // LifecycleLock also refreshes after the user returns from Android Settings.
    await refreshState();
    return opened;
  }

  Future<bool> canDrawOverlays() => _channels.canDrawOverlays();

  Future<bool> hasWriteSecureSettings() => _channels.hasWriteSecureSettings();

  Future<bool> openOverlayPermissionSettings() async {
    final opened = await _channels.openOverlayPermissionSettings();
    await refreshState();
    return opened;
  }

  Future<bool> openDeviceAdminSettings() async {
    final active = await _channels.openDeviceAdminSettings();
    await refreshState();
    return active;
  }

  Future<bool> isLocalDnsVpnRunning() => _channels.isLocalDnsVpnRunning();

  Future<bool> setLocalDnsVpnEnabled(bool enabled) async {
    final accepted = await _channels.setLocalDnsVpnEnabled(enabled);
    if (!accepted) return false;
    // Service establishment/teardown is asynchronous after Android consent.
    await Future<void>.delayed(const Duration(milliseconds: 500));
    return await _channels.isLocalDnsVpnRunning() == enabled;
  }

  Future<List<Map<String, dynamic>>> getHistory() => _repo.getAllEvents();

  /// Public wrapper for hash-chain verification (tamper detection).
  Future<int> verifyChain() => _repo.verifyChain();

  /// Append an event locally (used by the app for actions not driven by the
  /// native service, e.g., unauthorized access attempts in the UI).
  Future<void> logEvent({
    required String type,
    String? source,
    String? component,
    String? responseAttempted,
    bool responseVerified = false,
    String? diagnostic,
  }) async {
    await _repo.addEvent(
      type: type,
      tsMillis: DateTime.now().millisecondsSinceEpoch,
      elapsedRealtimeMs:
          DateTime.now().microsecondsSinceEpoch, // placeholder; native uses true uptime
      source: source,
      component: component,
      responseAttempted: responseAttempted,
      responseVerified: responseVerified,
      diagnostic: diagnostic,
    );
  }

  /// Access guard for protected settings.
  bool canAccessProtected() => _authenticated && _hasPassword;

  Future<void> logout() => setAuthenticated(false);

  @override
  void dispose() {
    unawaited(_eventSubscription?.cancel());
    super.dispose();
  }
}
