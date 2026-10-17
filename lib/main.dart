import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';

import 'app.dart';
import 'core/lifecycle_lock.dart';
import 'core/startup_diagnostics.dart';
import 'data/event_repository.dart';
import 'services/security_controller.dart';

void main() {
  runZonedGuarded(() {
    WidgetsFlutterBinding.ensureInitialized();

    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      StartupDiagnostics.error(
        'flutter_framework',
        details.exception,
        details.stack ?? StackTrace.current,
      );
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      StartupDiagnostics.error('uncaught_async', error, stack);
      return true;
    };

    StartupDiagnostics.stage('run_app');
    // No internet permission requested; offline-first.
    runApp(Bootstrap(repo: SecurityEventRepository()));
  }, (error, stack) {
    StartupDiagnostics.error('root_zone', error, stack);
  });
}

/// Initializes local persistence and native state before starting the app.
/// Every awaited stage is bounded and failures become a retryable screen rather
/// than leaving the user on an endless progress indicator.
class Bootstrap extends StatefulWidget {
  final SecurityEventRepository repo;
  const Bootstrap({super.key, required this.repo});

  @override
  State<Bootstrap> createState() => _BootstrapState();
}

enum _StartupStatus { loading, ready, failed }

class _BootstrapState extends State<Bootstrap> {
  static const _stageTimeout = Duration(seconds: 15);

  late final SecurityController _controller;
  late final LifecycleLockObserver _lifecycle;
  _StartupStatus _status = _StartupStatus.loading;
  String _stage = 'bootstrap_created';
  String? _errorReference;
  bool _initializing = false;

  @override
  void initState() {
    super.initState();
    StartupDiagnostics.stage(_stage);
    _controller = SecurityController(widget.repo);
    _lifecycle = LifecycleLockObserver(_controller);
    WidgetsBinding.instance.addObserver(_lifecycle);
    unawaited(_init());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(_lifecycle);
    _controller.dispose();
    unawaited(widget.repo.close());
    super.dispose();
  }

  void _setStage(String stage) {
    _stage = stage;
    StartupDiagnostics.stage(stage);
  }

  Future<void> _init() async {
    if (_initializing) return;
    _initializing = true;
    if (mounted) {
      setState(() {
        _status = _StartupStatus.loading;
        _errorReference = null;
      });
    }

    try {
      _setStage('database_initialization');
      await widget.repo.init().timeout(_stageTimeout);

      _setStage('controller_initialization');
      await _controller
          .init(onStage: _setStage)
          .timeout(const Duration(seconds: 30));

      _setStage('startup_complete');
      if (mounted) setState(() => _status = _StartupStatus.ready);
    } catch (error, stack) {
      StartupDiagnostics.error(_stage, error, stack);
      final reference = 'START-${DateTime.now().millisecondsSinceEpoch}';
      if (mounted) {
        setState(() {
          _status = _StartupStatus.failed;
          _errorReference = reference;
        });
      }
    } finally {
      _initializing = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    switch (_status) {
      case _StartupStatus.ready:
        return App(controller: _controller);
      case _StartupStatus.failed:
        return _StartupFailureScreen(
          stage: _stage,
          reference: _errorReference ?? 'START-UNKNOWN',
          onRetry: _init,
        );
      case _StartupStatus.loading:
        return _StartupLoadingScreen(stage: _stage);
    }
  }
}

class _StartupLoadingScreen extends StatelessWidget {
  final String stage;
  const _StartupLoadingScreen({required this.stage});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        body: Center(
          child: Semantics(
            label: 'جاري بدء التطبيق',
            child: const CircularProgressIndicator(),
          ),
        ),
      ),
    );
  }
}

class _StartupFailureScreen extends StatelessWidget {
  final String stage;
  final String reference;
  final Future<void> Function() onRetry;

  const _StartupFailureScreen({
    required this.stage,
    required this.reference,
    required this.onRetry,
  });

  String get _safeStageLabel {
    switch (stage) {
      case 'database_initialization':
        return 'تهيئة قاعدة البيانات المحلية';
      case 'native_password_status':
        return 'قراءة حالة قفل التطبيق';
      case 'native_security_state':
        return 'قراءة حالة الحماية';
      case 'native_event_stream':
      case 'controller_initialization':
        return 'تهيئة مراقبة الحالة';
      default:
        return 'بدء التطبيق';
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      locale: const Locale('ar'),
      home: Directionality(
        textDirection: TextDirection.rtl,
        child: Scaffold(
          body: SafeArea(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline, size: 64, color: Colors.red),
                      const SizedBox(height: 16),
                      const Text(
                        'تعذر بدء DNS Guardian',
                        style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'فشلت مرحلة: $_safeStageLabel. لم يتم تغيير إعدادات DNS. يمكنك المحاولة مرة أخرى.',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'رمز التشخيص: $reference',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 20),
                      FilledButton.icon(
                        onPressed: onRetry,
                        icon: const Icon(Icons.refresh),
                        label: const Text('إعادة المحاولة'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
