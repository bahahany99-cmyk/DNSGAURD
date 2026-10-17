import 'package:dns_guardian/core/platform_channel.dart';
import 'package:dns_guardian/core/security_state.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const stateChannel = MethodChannel('dns_guardian/security_state');
  const actionChannel = MethodChannel('dns_guardian/actions');

  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(stateChannel, null);
    messenger.setMockMethodCallHandler(actionChannel, null);
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

  test('queries and opens overlay permission through the action channel', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(actionChannel, (call) async {
      calls.add(call.method);
      return true;
    });
    final channels = PlatformChannels();

    expect(await channels.canDrawOverlays(), isTrue);
    expect(await channels.openOverlayPermissionSettings(), isTrue);
    expect(calls, ['canDrawOverlays', 'openOverlayPermissionSettings']);
  });

  test('queries and controls the local DNS VPN', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(actionChannel, (call) async {
      calls.add(call);
      return true;
    });
    final channels = PlatformChannels();

    expect(await channels.isLocalDnsVpnRunning(), isTrue);
    expect(await channels.setLocalDnsVpnEnabled(true), isTrue);
    expect(calls.map((call) => call.method),
        ['isLocalDnsVpnRunning', 'setLocalDnsVpnEnabled']);
    expect(calls.last.arguments, <String, Object?>{'enabled': true});
  });

  test('queries the ADB WRITE_SECURE_SETTINGS grant', () async {
    final calls = <String>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(actionChannel, (call) async {
      calls.add(call.method);
      return true;
    });

    expect(await PlatformChannels().hasWriteSecureSettings(), isTrue);
    expect(calls, ['hasWriteSecureSettings']);
  });
}
