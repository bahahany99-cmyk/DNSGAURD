import 'package:dns_guardian/core/lifecycle_lock.dart';
import 'package:dns_guardian/core/platform_channel.dart';
import 'package:dns_guardian/core/security_state.dart';
import 'package:dns_guardian/data/event_repository.dart';
import 'package:dns_guardian/services/security_controller.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

SecurityState _state({required bool paused}) => SecurityState(
      layers: SecurityState.initial()
          .layers
          .map((layer) => layer.copyWith(state: LayerState.activeVerified))
          .toList(),
      protectionPaused: paused,
    );

class _FakeChannels extends PlatformChannels {
  SecurityState nextState = _state(paused: false);
  bool pauseResult = true;
  bool resumeResult = true;
  bool enrollResult = true;
  int stateQueries = 0;

  @override
  Future<SecurityState> getSecurityState() async {
    stateQueries++;
    return nextState;
  }

  @override
  Future<bool> pauseProtection() async => pauseResult;

  @override
  Future<bool> resumeProtection() async => resumeResult;

  @override
  Future<bool> enrollPassword(String password) async => enrollResult;

  @override
  Stream<dynamic> get eventStream => const Stream<dynamic>.empty();
}

void main() {
  test('password configuration is separate from authentication and lock', () async {
    final controller = SecurityController(
      SecurityEventRepository(),
      channels: _FakeChannels(),
    );

    expect(await controller.enrollPassword('non-sensitive-test-value'), isTrue);
    expect(controller.hasPassword, isTrue);
    expect(controller.isAuthenticated, isFalse);

    await controller.setAuthenticated(true);
    expect(controller.isAuthenticated, isTrue);
    controller.lockNow();
    expect(controller.hasPassword, isTrue);
    expect(controller.isAuthenticated, isFalse);
  });

  test('background lifecycle transition locks immediately', () async {
    final controller = SecurityController(
      SecurityEventRepository(),
      channels: _FakeChannels(),
    );
    await controller.setAuthenticated(true);
    final observer = LifecycleLockObserver(controller);

    observer.didChangeAppLifecycleState(AppLifecycleState.inactive);

    expect(controller.isAuthenticated, isFalse);
  });

  group('verified automatic-response pause transitions', () {
    test('pause succeeds only when persistence and refreshed state agree', () async {
      final channels = _FakeChannels()..nextState = _state(paused: true);
      final controller = SecurityController(
        SecurityEventRepository(),
        channels: channels,
      );

      expect(await controller.pauseProtection(), isTrue);
      expect(controller.state.protectionPaused, isTrue);
      expect(channels.stateQueries, 1);
    });

    test('pause reports failure when refreshed state did not change', () async {
      final channels = _FakeChannels()..nextState = _state(paused: false);
      final controller = SecurityController(
        SecurityEventRepository(),
        channels: channels,
      );

      expect(await controller.pauseProtection(), isFalse);
      expect(controller.state.protectionPaused, isFalse);
    });

    test('pause reports failure when native persistence failed', () async {
      final channels = _FakeChannels()
        ..pauseResult = false
        ..nextState = _state(paused: true);
      final controller = SecurityController(
        SecurityEventRepository(),
        channels: channels,
      );

      expect(await controller.pauseProtection(), isFalse);
    });

    test('resume succeeds only after refreshed state is unpaused', () async {
      final channels = _FakeChannels()..nextState = _state(paused: false);
      final controller = SecurityController(
        SecurityEventRepository(),
        channels: channels,
      );

      expect(await controller.resumeProtection(), isTrue);
      expect(controller.state.protectionPaused, isFalse);
    });

    test('pause notifies listeners so the UI can update', () async {
      final channels = _FakeChannels()..nextState = _state(paused: true);
      final controller = SecurityController(
        SecurityEventRepository(),
        channels: channels,
      );
      var notifications = 0;
      controller.addListener(() => notifications++);

      expect(await controller.pauseProtection(), isTrue);
      expect(controller.state.protectionPaused, isTrue);
      expect(notifications, greaterThan(0));
    });
  });
}
