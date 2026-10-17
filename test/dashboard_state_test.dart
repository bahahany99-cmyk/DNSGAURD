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

class _DashboardChannels extends PlatformChannels {
  _DashboardChannels(this.value);

  final SecurityState value;

  @override
  Future<SecurityState> getSecurityState() async => value;
}

void main() {
  testWidgets(
      'dashboard renders verified, disabled, permission, unknown, unsupported, and error states',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(1200, 2200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final initial = SecurityState.initial().layers;
    final states = <LayerState>[
      LayerState.activeVerified,
      LayerState.inactive,
      LayerState.permissionRequired,
      LayerState.unknown,
      LayerState.unsupported,
      LayerState.error,
      LayerState.activeUnverified,
    ];
    final value = SecurityState(
      layers: [
        for (var i = 0; i < initial.length; i++)
          initial[i].copyWith(state: states[i]),
      ],
    );
    final controller = SecurityController(
      SecurityEventRepository(),
      channels: _DashboardChannels(value),
    );
    await controller.refreshState();

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
    await tester.pump();

    expect(find.text('Protection active'), findsOneWidget);
    expect(find.text('Inactive'), findsOneWidget);
    expect(find.text('Permission required'), findsOneWidget);
    expect(find.text('Unknown'), findsOneWidget);
    expect(find.text('Unsupported on this device'), findsOneWidget);
    expect(find.text('Status query failed'), findsNWidgets(2));
    expect(
      find.text('Active, but protection is not fully verified'),
      findsOneWidget,
    );
  });
}
