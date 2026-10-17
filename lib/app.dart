import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'l10n/app_localizations.dart';
import 'services/security_controller.dart';
import 'features/dashboard/dashboard_screen.dart';
import 'features/lock/lock_screen.dart';
import 'features/lock/password_enrollment_screen.dart';
import 'features/history/history_screen.dart';
import 'features/settings/protected_settings_screen.dart';
import 'features/onboarding/onboarding_screen.dart';

class App extends StatelessWidget {
  final SecurityController controller;

  App({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: controller,
      child: Consumer<SecurityController>(
        builder: (ctx, c, _) {
          // Re-evaluate router guards whenever auth/password state changes
          // (the controller initializes asynchronously after repo load).
          WidgetsBinding.instance.addPostFrameCallback((_) => _router.refresh());
          return MaterialApp.router(
            title: 'DNS Guardian',
            debugShowCheckedModeBanner: false,
            theme: ThemeData(
              useMaterial3: true,
              colorSchemeSeed: const Color(0xFF1B5E20),
              brightness: Brightness.light,
            ),
            darkTheme: ThemeData(
              useMaterial3: true,
              colorSchemeSeed: const Color(0xFF2E7D32),
              brightness: Brightness.dark,
            ),
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: AppLocalizations.supportedLocales,
            locale: const Locale('ar'), // Arabic-first
            routerConfig: _router,
          );
        },
      ),
    );
  }

  late final GoRouter _router = GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final c = Provider.of<SecurityController>(context, listen: false);
      final loc = state.matchedLocation;
      // Lock screen gates everything except onboarding/lock/enroll.
      final public = {'/', '/lock', '/enroll', '/onboarding'};
      if (!c.hasPassword) {
        // First run: must enroll.
        if (loc != '/enroll') return '/enroll';
        return null;
      }
      if (!c.isAuthenticated && !public.contains(loc)) {
        return '/lock';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/', builder: (c, s) => const DashboardScreen()),
      GoRoute(path: '/lock', builder: (c, s) => const LockScreen()),
      GoRoute(path: '/enroll', builder: (c, s) => const PasswordEnrollmentScreen()),
      GoRoute(path: '/history', builder: (c, s) => const HistoryScreen()),
      GoRoute(path: '/settings', builder: (c, s) => const ProtectedSettingsScreen()),
      GoRoute(path: '/onboarding', builder: (c, s) => const OnboardingScreen()),
    ],
  );
}
