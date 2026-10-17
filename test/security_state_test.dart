import 'package:flutter_test/flutter_test.dart';
import 'package:dns_guardian/core/security_state.dart';

void main() {
  group('SecurityState overall verdict', () {
    test('initial state is unknown and not fully protected', () {
      final s = SecurityState.initial();
      expect(s.overall, LayerState.unknown);
      expect(s.isFullyProtected, isFalse);
    });

    test('all verified => activeVerified', () {
      final layers = SecurityState.initial().layers.map((l) =>
          l.copyWith(state: LayerState.activeVerified)).toList();
      final s = SecurityState(layers: layers);
      expect(s.overall, LayerState.activeVerified);
      expect(s.isFullyProtected, isTrue);
    });

    test('one inactive layer => overall inactive (never green)', () {
      final layers = SecurityState.initial().layers.map((l) =>
          l.copyWith(state: LayerState.activeVerified)).toList();
      // make accessibility inactive
      final idx = layers.indexWhere((l) => l.id == 'accessibility_service');
      layers[idx] = layers[idx].copyWith(state: LayerState.inactive);
      final s = SecurityState(layers: layers);
      expect(s.overall, LayerState.inactive);
      expect(s.isFullyProtected, isFalse);
    });

    test('permission required => not fully protected', () {
      final layers = SecurityState.initial().layers.map((l) =>
          l.copyWith(state: LayerState.activeVerified)).toList();
      final idx = layers.indexWhere((l) => l.id == 'private_dns_monitor');
      layers[idx] = layers[idx].copyWith(state: LayerState.permissionRequired);
      final s = SecurityState(layers: layers);
      expect(s.overall, LayerState.permissionRequired);
    });

    test('unknown layer => unknown overall', () {
      final layers = SecurityState.initial().layers.map((l) =>
          l.copyWith(state: LayerState.activeVerified)).toList();
      final idx = layers.indexWhere((l) => l.id == 'recovery_boot');
      layers[idx] = layers[idx].copyWith(state: LayerState.unknown);
      final s = SecurityState(layers: layers);
      expect(s.overall, LayerState.unknown);
    });

    test('device admin unsupported is ignored (optional layer)', () {
      final layers = SecurityState.initial().layers.map((l) =>
          l.copyWith(state: LayerState.activeVerified)).toList();
      final idx = layers.indexWhere((l) => l.id == 'device_admin');
      layers[idx] = layers[idx].copyWith(state: LayerState.unsupported);
      final s = SecurityState(layers: layers);
      expect(s.overall, LayerState.activeVerified);
    });

    test('paused protection reports unsupported', () {
      final s = SecurityState(
        layers: SecurityState.initial().layers
            .map((l) => l.copyWith(state: LayerState.activeVerified))
            .toList(),
        protectionPaused: true,
      );
      expect(s.overall, LayerState.unsupported);
    });

    test('layerById returns correct layer', () {
      final s = SecurityState.initial();
      expect(s.layerById('password_lock')?.id, 'password_lock');
      expect(s.layerById('nonexistent'), isNull);
    });

    test('json round trip', () {
      final s = SecurityState.initial();
      final json = s.toJson();
      final restored = SecurityState.fromJson(json);
      expect(restored.layers.length, s.layers.length);
      expect(restored.overall, s.overall);
    });
  });

  group('LayerState helpers', () {
    test('requiresAttention flags bad states', () {
      expect(LayerState.inactive.requiresAttention, isTrue);
      expect(LayerState.permissionRequired.requiresAttention, isTrue);
      expect(LayerState.recoveryRequired.requiresAttention, isTrue);
      expect(LayerState.unknown.requiresAttention, isTrue);
      expect(LayerState.activeVerified.requiresAttention, isFalse);
      expect(LayerState.activeUnverified.requiresAttention, isFalse);
      expect(LayerState.unsupported.requiresAttention, isFalse);
    });
  });
}
