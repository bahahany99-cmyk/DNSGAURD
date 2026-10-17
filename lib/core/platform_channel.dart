import 'package:flutter/services.dart';
import 'security_state.dart';
import 'startup_diagnostics.dart';

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
    StartupDiagnostics.stage('security_state_request_sent');
    try {
      final result = await _stateChannel.invokeMethod('getState');
      StartupDiagnostics.stage('security_state_response_received');
      if (result is! Map) {
        throw const FormatException('security state response is not a map');
      }
      final state = SecurityState.fromJson(Map<String, dynamic>.from(result));
      StartupDiagnostics.stage('security_state_response_decoded');
      return state;
    } catch (error, stack) {
      StartupDiagnostics.error('native_security_state', error, stack);
      return SecurityState.queryError();
    }
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
    } catch (error, stack) {
      StartupDiagnostics.error('native_password_status', error, stack);
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

  Future<bool> pauseProtection() async {
    try {
      return await _actionChannel.invokeMethod<bool>('pauseProtection') == true;
    } catch (error, stack) {
      StartupDiagnostics.error('pause_automatic_response', error, stack);
      return false;
    }
  }

  Future<bool> resumeProtection() async {
    try {
      return await _actionChannel.invokeMethod<bool>('resumeProtection') == true;
    } catch (error, stack) {
      StartupDiagnostics.error('resume_automatic_response', error, stack);
      return false;
    }
  }

  Future<bool> openAccessibilitySettings() async {
    try {
      return await _actionChannel.invokeMethod<bool>('openAccessibilitySettings') == true;
    } catch (error, stack) {
      StartupDiagnostics.error('open_accessibility_settings', error, stack);
      return false;
    }
  }

  Future<bool> openDeviceAdminSettings() async {
    try {
      return await _actionChannel.invokeMethod<bool>('openDeviceAdminSettings') == true;
    } catch (error, stack) {
      StartupDiagnostics.error('open_device_admin_activation', error, stack);
      return false;
    }
  }

  Stream<dynamic> get eventStream => _eventChannel.receiveBroadcastStream();
}
