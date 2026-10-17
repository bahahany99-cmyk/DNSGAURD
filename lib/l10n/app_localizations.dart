// Generated-style localization accessors. (Hand-written for offline build.)
import 'dart:async';
import 'package:flutter/widgets.dart';
import 'app_ar.arb.dart' as ar;
import 'app_en.arb.dart' as en;

class AppLocalizations {
  final Locale locale;
  AppLocalizations(this.locale);

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  static const supportedLocales = [Locale('ar'), Locale('en')];

  static AppLocalizations of(BuildContext context) =>
      Localizations.of<AppLocalizations>(context, AppLocalizations)!;

  Map<String, String> get _strings =>
      locale.languageCode == 'ar' ? ar.arStrings : en.enStrings;

  String get appTitle => _strings['appTitle'] ?? 'DNS Guardian';
  String get dashboard => _strings['dashboard'] ?? 'Dashboard';
  String get overallStatus => _strings['overallStatus'] ?? 'Status';
  String get protectionActive => _strings['protectionActive'] ?? 'Active';
  String get protectionInactive => _strings['protectionInactive'] ?? 'Inactive';
  String get protectionUnknown => _strings['protectionUnknown'] ?? 'Unknown';
  String get activeUnverified =>
      _strings['activeUnverified'] ?? 'Active, not fully verified';
  String get privateDnsMonitor => _strings['privateDnsMonitor'] ?? 'Private DNS';
  String get accessibilityService => _strings['accessibilityService'] ?? 'Accessibility';
  String get deviceAdmin => _strings['deviceAdmin'] ?? 'Device Admin';
  String get appManagementMonitor => _strings['appManagementMonitor'] ?? 'App Mgmt';
  String get passwordLock => _strings['passwordLock'] ?? 'Password';
  String get securityHistory => _strings['securityHistory'] ?? 'History';
  String get recoveryBoot => _strings['recoveryBoot'] ?? 'Reboot Recovery';
  String get overallAssessment => _strings['overallAssessment'] ?? 'Assessment';
  String get lockPrompt => _strings['lockPrompt'] ?? 'Password';
  String get unlock => _strings['unlock'] ?? 'Unlock';
  String get passwordHint => _strings['passwordHint'] ?? 'Password';
  String get incorrectPassword => _strings['incorrectPassword'] ?? 'Wrong';
  String get enrollPassword => _strings['enrollPassword'] ?? 'Create password';
  String get enrollHint => _strings['enrollHint'] ?? 'Strong password';
  String get historyTitle => _strings['historyTitle'] ?? 'History';
  String get noHistory => _strings['noHistory'] ?? 'No events';
  String get historyLoadError =>
      _strings['historyLoadError'] ?? 'History could not be read';
  String get historyIntegrityWarning =>
      _strings['historyIntegrityWarning'] ?? 'History integrity check failed';
  String get historyRetentionNote =>
      _strings['historyRetentionNote'] ?? 'Clearing app data erases history.';
  String get filterAll => _strings['filterAll'] ?? 'All';
  String get settings => _strings['settings'] ?? 'Settings';
  String get accessDenied => _strings['accessDenied'] ?? 'Denied';
  String get enableAccessibility => _strings['enableAccessibility'] ?? 'Enable Accessibility';
  String get accessibilityGuide => _strings['accessibilityGuide'] ?? 'Guide';
  String get localDnsVpn => _strings['localDnsVpn'] ?? 'Local DNS VPN';
  String get localDnsVpnEnabled =>
      _strings['localDnsVpnEnabled'] ?? 'Local DNS VPN active';
  String get localDnsVpnGuide =>
      _strings['localDnsVpnGuide'] ?? 'Enable the local DNS VPN';
  String get localDnsVpnStarted =>
      _strings['localDnsVpnStarted'] ?? 'Local DNS VPN started';
  String get localDnsVpnStopped =>
      _strings['localDnsVpnStopped'] ?? 'Local DNS VPN stopped';
  String get localDnsVpnAlwaysOnNote =>
      _strings['localDnsVpnAlwaysOnNote'] ?? 'Supports Android Always-on VPN';
  String get enableBlockingOverlay =>
      _strings['enableBlockingOverlay'] ?? 'Enable blocking overlay';
  String get blockingOverlayEnabled =>
      _strings['blockingOverlayEnabled'] ?? 'Overlay permission enabled';
  String get blockingOverlayGuide =>
      _strings['blockingOverlayGuide'] ?? 'Overlay permission is required';
  String get blockingOverlayInstructions =>
      _strings['blockingOverlayInstructions'] ?? 'Allow display over other apps';
  String get secureSettingsPermission =>
      _strings['secureSettingsPermission'] ?? 'Private DNS auto-healing';
  String get secureSettingsEnabled =>
      _strings['secureSettingsEnabled'] ?? 'ADB permission enabled';
  String get secureSettingsAdbGuide =>
      _strings['secureSettingsAdbGuide'] ?? 'ADB permission required';
  String get activateDeviceAdmin => _strings['activateDeviceAdmin'] ?? 'Device Admin';
  String get deviceAdminGuide => _strings['deviceAdminGuide'] ?? 'Guide';
  String get rebootRecoveryNote => _strings['rebootRecoveryNote'] ?? 'Note';
  String get limitationNote => _strings['limitationNote'] ?? 'Note';
  String get close => _strings['close'] ?? 'Close';
  String get ok => _strings['ok'] ?? 'OK';
  String get cancel => _strings['cancel'] ?? 'Cancel';
  String get save => _strings['save'] ?? 'Save';
  String get attempted => _strings['attempted'] ?? 'Attempted';
  String get verified => _strings['verified'] ?? 'Verified';
  String get redirected => _strings['redirected'] ?? 'Redirected';
  String get detected => _strings['detected'] ?? 'Detected';
  String get unknown => _strings['unknown'] ?? 'Unknown';
  String get permissionRequired => _strings['permissionRequired'] ?? 'Permission';
  String get inactive => _strings['inactive'] ?? 'Inactive';
  String get unsupported => _strings['unsupported'] ?? 'Unsupported';
  String get recoveryRequired => _strings['recoveryRequired'] ?? 'Manual';
  String get monitoringGap => _strings['monitoringGap'] ?? 'Gap';
  String get eventPrivateDnsAccess => _strings['eventPrivateDnsAccess'] ?? 'Private DNS access';
  String get eventDnsChanged => _strings['eventDnsChanged'] ?? 'DNS changed';
  String get eventAppInfo => _strings['eventAppInfo'] ?? 'App info';
  String get eventUninstall => _strings['eventUninstall'] ?? 'Uninstall';
  String get eventAccessibilitySettings => _strings['eventAccessibilitySettings'] ?? 'A11y settings';
  String get eventDeviceAdminDeactivate => _strings['eventDeviceAdminDeactivate'] ?? 'DA deactivate';
  String get eventComponentDisabled => _strings['eventComponentDisabled'] ?? 'Component disabled';
  String get eventRecoveryFailed => _strings['eventRecoveryFailed'] ?? 'Recovery failed';
  String get eventUnauthorizedAccess => _strings['eventUnauthorizedAccess'] ?? 'Unauth access';
  String get eventStartup => _strings['eventStartup'] ?? 'Startup';
  String get eventMonitoringGap => _strings['eventMonitoringGap'] ?? 'Gap';
  String get continueStr => _strings['continue'] ?? 'Continue';
  String get learnMore => _strings['learnMore'] ?? 'Learn more';
  String get pauseProtection => _strings['pauseProtection'] ?? 'Pause';
  String get resumeProtection => _strings['resumeProtection'] ?? 'Resume';
  String get pausedForMaintenance => _strings['pausedForMaintenance'] ?? 'Paused';
  String get queryError => _strings['queryError'] ?? 'Status query failed';
  String get pauseSucceeded => _strings['pauseSucceeded'] ?? 'Response paused';
  String get resumeSucceeded => _strings['resumeSucceeded'] ?? 'Response resumed';
  String get actionFailed => _strings['actionFailed'] ?? 'Action could not be verified';
  String get accessibilityOpenedInstructions =>
      _strings['accessibilityOpenedInstructions'] ?? 'Enable DNS Guardian in Accessibility settings';
  String get deviceAdminActivated =>
      _strings['deviceAdminActivated'] ?? 'Device Admin activated';
  String get deviceAdminNotActivated =>
      _strings['deviceAdminNotActivated'] ?? 'Device Admin is not active';
  String get passwordConfigured =>
      _strings['passwordConfigured'] ?? 'App password is configured';
  String get whatIsPrivateDns => _strings['whatIsPrivateDns'] ?? 'What is Private DNS';
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) =>
      ['ar', 'en'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async => AppLocalizations(locale);

  @override
  bool shouldReload(covariant LocalizationsDelegate<AppLocalizations> old) => false;
}
