import 'package:flutter/services.dart';
import 'security_state.dart';

/// Thin wrapper around the native platform channels defined in the Kotlin side.
/// Methods are guarded so the app degrades gracefully when the native side is
/// unavailable (e.g. during early development without the service running).
class PlatformChannels {
  static const MethodChannel _stateChannel =
      MethodChannel('dns_guardian/security_state');
  static const MethodChannel _authChannel =
      MethodChannel('dns_guardian/auth');
  static const EventChannel _eventChannel =
      EventChannel('dns_guardian/security_events');

  static const MethodChannel _actionChannel =
      MethodChannel('dns_guardian/actions');

  Future<SecurityState> getSecurityState() async {
    try {
      final result = await _stateChannel.invokeMethod('getState');
      if (result is Map) {
        return SecurityState.fromJson(Map<String, dynamic>.from(result));
      }
    } on PlatformException catch (_) {
      // native unavailable; keep current
    }
    return SecurityState.initial();
  }

  Future<void> refreshState() async {
    try {
      await _stateChannel.invokeMethod('refresh');
    } on PlatformException catch (_) {}
  }

  Future<bool> hasPasswordSet() async {
    try {
      final r = await _authChannel.invokeMethod('hasPassword');
      return r == true;
    } on PlatformException catch (_) {
      return false;
    }
  }

  /// Dev/test only: set a temporary password. Must be stripped from release.
  Future<bool> devSetPassword(String password) async {
    try {
      final r = await _authChannel.invokeMethod('devSetPassword', {'password': password});
      return r == true;
    } on PlatformException catch (_) {
      return false;
    }
  }

  Future<bool> verifyPassword(String password) async {
    try {
      final r = await _authChannel.invokeMethod('verifyPassword', {'password': password});
      return r == true;
    } on PlatformException catch (_) {
      return false;
    }
  }

  /// Persist the final owner password (Phase 9). Requires explicit flag.
  Future<bool> enrollPassword(String password) async {
    try {
      final r = await _authChannel
          .invokeMethod('enrollPassword', {'password': password});
      return r == true;
    } on PlatformException catch (_) {
      return false;
    }
  }

  Future<void> pauseProtection() async {
    try {
      await _actionChannel.invokeMethod('pauseProtection');
    } on PlatformException catch (_) {}
  }

  Future<void> resumeProtection() async {
    try {
      await _actionChannel.invokeMethod('resumeProtection');
    } on PlatformException catch (_) {}
  }

  Future<void> openAccessibilitySettings() async {
    try {
      await _actionChannel.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (_) {}
  }

  Future<void> openDeviceAdminSettings() async {
    try {
      await _actionChannel.invokeMethod('openDeviceAdminSettings');
    } on PlatformException catch (_) {}
  }

  Stream<dynamic> get eventStream => _eventChannel.receiveBroadcastStream();
}
