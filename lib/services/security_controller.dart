import 'dart:async';
import 'package:flutter/material.dart';
import 'core/security_state.dart';
import 'core/platform_channel.dart';
import 'data/event_repository.dart';

/// Central app controller: holds the authoritative SecurityState, exposes it to
/// the UI via ChangeNotifier, and bridges native channels + event repository.
class SecurityController extends ChangeNotifier {
  final PlatformChannels _channels = PlatformChannels();
  final SecurityEventRepository _repo;

  SecurityState _state = SecurityState.initial();
  SecurityState get state => _state;

  bool _authenticated = false;
  bool get isAuthenticated => _authenticated;

  bool _hasPassword = false;
  bool get hasPassword => _hasPassword;

  SecurityController(this._repo);

  Future<void> init() async {
    _hasPassword = await _channels.hasPasswordSet();
    await refreshState();
    // subscribe to native events (best-effort)
    _channels.eventStream.listen((_) => refreshState());
  }

  Future<void> refreshState() async {
    _state = await _channels.getSecurityState();
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

  Future<void> pauseProtection() async {
    await _channels.pauseProtection();
    await refreshState();
  }

  Future<void> resumeProtection() async {
    await _channels.resumeProtection();
    await refreshState();
  }

  Future<void> openAccessibilitySettings() => _channels.openAccessibilitySettings();
  Future<void> openDeviceAdminSettings() => _channels.openDeviceAdminSettings();

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
}
