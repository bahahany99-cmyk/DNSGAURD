/// Security state models — single source of truth mirrored from native Kotlin.
/// UI must never override the manager's verdict.

enum LayerState {
  activeVerified,
  activeUnverified,
  inactive,
  permissionRequired,
  recoveryRequired,
  unsupported,
  unknown,
  error;

  String labelKey() {
    switch (this) {
      case LayerState.activeVerified:
        return 'protectionActive';
      case LayerState.activeUnverified:
        return 'protectionActive';
      case LayerState.inactive:
        return 'inactive';
      case LayerState.permissionRequired:
        return 'permissionRequired';
      case LayerState.recoveryRequired:
        return 'recoveryRequired';
      case LayerState.unsupported:
        return 'unsupported';
      case LayerState.unknown:
        return 'unknown';
      case LayerState.error:
        return 'error';
    }
  }

  bool get isFullyOk => this == LayerState.activeVerified;

  /// Whether this state should block an overall "green" verdict.
  bool get requiresAttention =>
      this == LayerState.inactive ||
      this == LayerState.permissionRequired ||
      this == LayerState.recoveryRequired ||
      this == LayerState.unknown ||
      this == LayerState.error ||
      this == LayerState.activeUnverified;
}

LayerState _parseLayerState(Object? value) {
  if (value is! String) return LayerState.error;
  final normalized = value.replaceAll('_', '').toLowerCase();
  for (final state in LayerState.values) {
    if (state.name.toLowerCase() == normalized) return state;
  }
  // Unknown wire values indicate a protocol mismatch, not an unknown device state.
  return LayerState.error;
}

class ProtectionLayer {
  final String id;
  final String titleKey;
  final LayerState state;
  final int? lastVerifiedAt; // epoch millis
  final String? detailKey; // optional localized detail

  const ProtectionLayer({
    required this.id,
    required this.titleKey,
    required this.state,
    this.lastVerifiedAt,
    this.detailKey,
  });

  ProtectionLayer copyWith({
    LayerState? state,
    int? lastVerifiedAt,
    String? detailKey,
  }) =>
      ProtectionLayer(
        id: id,
        titleKey: titleKey,
        state: state ?? this.state,
        lastVerifiedAt: lastVerifiedAt ?? this.lastVerifiedAt,
        detailKey: detailKey ?? this.detailKey,
      );

  factory ProtectionLayer.fromJson(Map<String, dynamic> json) => ProtectionLayer(
        id: json['id'] as String,
        titleKey: json['titleKey'] as String,
        state: _parseLayerState(json['state']),
        lastVerifiedAt: json['lastVerifiedAt'] as int?,
        detailKey: json['detailKey'] as String?,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'titleKey': titleKey,
        'state': state.name,
        'lastVerifiedAt': lastVerifiedAt,
        'detailKey': detailKey,
      };

  /// The 7 canonical layers required for an honest overall verdict.
  static const List<String> requiredLayerIds = [
    'private_dns_monitor',
    'accessibility_service',
    'app_management_monitor',
    'password_lock',
    'security_history',
    'recovery_boot',
  ];
}

class SecurityState {
  final List<ProtectionLayer> layers;
  final int? lastRecoveryCheckAt;
  final bool protectionPaused; // authenticated temporary pause

  const SecurityState({
    required this.layers,
    this.lastRecoveryCheckAt,
    this.protectionPaused = false,
  });

  factory SecurityState.initial() => SecurityState(
        layers: [
          const ProtectionLayer(
              id: 'private_dns_monitor',
              titleKey: 'privateDnsMonitor',
              state: LayerState.unknown),
          const ProtectionLayer(
              id: 'accessibility_service',
              titleKey: 'accessibilityService',
              state: LayerState.unknown),
          const ProtectionLayer(
              id: 'app_management_monitor',
              titleKey: 'appManagementMonitor',
              state: LayerState.unknown),
          const ProtectionLayer(
              id: 'password_lock', titleKey: 'passwordLock', state: LayerState.unknown),
          const ProtectionLayer(
              id: 'security_history',
              titleKey: 'securityHistory',
              state: LayerState.unknown),
          const ProtectionLayer(
              id: 'recovery_boot', titleKey: 'recoveryBoot', state: LayerState.unknown),
          const ProtectionLayer(
              id: 'device_admin', titleKey: 'deviceAdmin', state: LayerState.unknown),
        ],
        lastRecoveryCheckAt: null,
        protectionPaused: false,
      );

  factory SecurityState.queryError() => SecurityState(
        layers: SecurityState.initial()
            .layers
            .map((layer) => layer.copyWith(state: LayerState.error))
            .toList(),
      );

  factory SecurityState.fromJson(Map<String, dynamic> json) => SecurityState(
        layers: (json['layers'] as List)
            .map((e) => ProtectionLayer.fromJson(e as Map<String, dynamic>))
            .toList(),
        lastRecoveryCheckAt: json['lastRecoveryCheckAt'] as int?,
        protectionPaused: json['protectionPaused'] as bool? ?? false,
      );

  Map<String, dynamic> toJson() => {
        'layers': layers.map((e) => e.toJson()).toList(),
        'lastRecoveryCheckAt': lastRecoveryCheckAt,
        'protectionPaused': protectionPaused,
      };

  ProtectionLayer? layerById(String id) =>
      layers.where((l) => l.id == id).isEmpty ? null : layers.firstWhere((l) => l.id == id);

  /// Overall verdict derived honestly from required layers.
  /// Never returns fully-protected if any required layer needs attention.
  LayerState get overall {
    if (protectionPaused) return LayerState.inactive; // automatic response is paused
    LayerState worst = LayerState.activeVerified;
    for (final l in layers) {
      if (l.id == 'device_admin') {
        // Ordinary Device Admin is an explicitly optional extra layer.
        continue;
      }
      if (l.state.requiresAttention) {
        // pick the most severe
        if (l.state.index > worst.index) worst = l.state;
      }
    }
    return worst;
  }

  bool get isFullyProtected => overall == LayerState.activeVerified;
}
