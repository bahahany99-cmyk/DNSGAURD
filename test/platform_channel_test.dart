import 'package:dns_guardian/core/platform_channel.dart';
import 'package:dns_guardian/core/security_state.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const stateChannel = MethodChannel('dns_guardian/security_state');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(stateChannel, null);
  });

  test('decodes a successful Android platform response', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(stateChannel, (call) async {
      expect(call.method, 'getState');
      return <Object?, Object?>{
        'layers': <Object?>[
          <Object?, Object?>{
            'id': 'accessibility_service',
            'titleKey': 'accessibilityService',
            'state': 'activeVerified',
            'lastVerifiedAt': null,
            'detailKey': null,
          },
          <Object?, Object?>{
            'id': 'device_admin',
            'titleKey': 'deviceAdmin',
            'state': 'inactive',
            'lastVerifiedAt': null,
            'detailKey': null,
          },
        ],
        'lastRecoveryCheckAt': null,
        'protectionPaused': false,
      };
    });

    final state = await PlatformChannels().getSecurityState();

    expect(state.layers[0].state, LayerState.activeVerified);
    expect(state.layers[1].state, LayerState.inactive);
  });

  test('native channel exception becomes an honest query error', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(stateChannel, (_) async {
      throw PlatformException(code: 'STATE_UNAVAILABLE');
    });

    final state = await PlatformChannels().getSecurityState();

    expect(state.overall, LayerState.error);
    expect(state.layers.every((layer) => layer.state == LayerState.error), isTrue);
  });

  test('missing native response fields become an honest query error', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(stateChannel, (_) async => <Object?, Object?>{});

    final state = await PlatformChannels().getSecurityState();

    expect(state.overall, LayerState.error);
  });
}
