import 'dart:developer' as developer;

/// Local-only startup diagnostics. Messages go to Android logcat through the
/// Flutter engine. Never pass passwords, hashes, keys, DNS hostnames, or user
/// content to this logger.
class StartupDiagnostics {
  static const String loggerName = 'DNSGuardian.Startup';

  static void stage(String stage) {
    developer.log('stage=$stage', name: loggerName);
  }

  static void error(String stage, Object error, StackTrace stackTrace) {
    // Log only the exception type in the message. The local stack trace is
    // retained for developer diagnosis and is never displayed in the UI.
    developer.log(
      'stage=$stage failed type=${error.runtimeType}',
      name: loggerName,
      error: error,
      stackTrace: stackTrace,
      level: 1000,
    );
  }
}
