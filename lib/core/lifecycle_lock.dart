import 'package:flutter/widgets.dart';
import 'services/security_controller.dart';

/// Immediately locks the protected UI when the app leaves the foreground.
/// Zero grace period (requirement 10.3). Also re-checks state on resume.
class LifecycleLockObserver extends WidgetsBindingObserver {
  final SecurityController controller;

  LifecycleLockObserver(this.controller);

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.inactive:
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
      case AppLifecycleState.hidden:
        // Lock immediately on any background transition.
        controller.lockNow();
        break;
      case AppLifecycleState.resumed:
        // Re-verify protection state on return.
        controller.refreshState();
        break;
    }
  }
}
