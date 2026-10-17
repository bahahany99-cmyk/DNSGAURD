import 'package:dns_guardian/core/lifecycle_lock.dart';
import 'package:dns_guardian/core/platform_channel.dart';
import 'package:dns_guardian/core/security_state.dart';
import 'package:dns_guardian/data/event_repository.dart';
import 'package:dns_guardian/features/dashboard/dashboard_screen.dart';
import 'package:dns_guardian/l10n/app_localizations.dart';
import 'package:dns_guardian/services/security_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

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
  Stream<dynamic> get eventStream => const Stream<dynamic>.empty();
}

void main() {
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

    testWidgets('dashboard updates and shows verified pause feedback',
        (tester) async {
      final channels = _FakeChannels()..nextState = _state(paused: false);
      final controller = SecurityController(
        SecurityEventRepository(),
        channels: channels,
      );
      await controller.refreshState();
      await controller.setAuthenticated(true);

      await tester.pumpWidget(
        ChangeNotifierProvider.value(
          value: controller,
          child: const MaterialApp(
            locale: Locale('en'),
            localizationsDelegates: [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: AppLocalizations.supportedLocales,
            home: DashboardScreen(),
          ),
        ),
      );

      channels.nextState = _state(paused: true);
      await tester.tap(find.byIcon(Icons.pause));
      await tester.pumpAndSettle();

      expect(find.text('Automatic response paused and verified'), findsOneWidget);
      expect(find.text('Resume automatic response'), findsOneWidget);
      expect(controller.state.protectionPaused, isTrue);
    });
  });
}
