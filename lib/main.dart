import 'package:flutter/material.dart';
import 'app.dart';
import 'core/lifecycle_lock.dart';
import 'data/event_repository.dart';
import 'services/security_controller.dart';

void main() {
  // No internet permission requested; offline-first.
  final repo = SecurityEventRepository();
  // Repository init is async; we run a minimal bootstrap.
  runApp(Bootstrap(repo: repo));
}

/// Initializes the repository then starts the app.
class Bootstrap extends StatefulWidget {
  final SecurityEventRepository repo;
  const Bootstrap({super.key, required this.repo});

  @override
  State<Bootstrap> createState() => _BootstrapState();
}

class _BootstrapState extends State<Bootstrap> {
  late final SecurityController _controller;
  late final LifecycleLockObserver _lifecycle;
  bool _ready = false;

  @override
  void initState() {
    super.initState();
    _controller = SecurityController(widget.repo);
    _lifecycle = LifecycleLockObserver(_controller);
    WidgetsBinding.instance.addObserver(_lifecycle);
    _init();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(_lifecycle);
    super.dispose();
  }

  Future<void> _init() async {
    await widget.repo.init(); // local sqlite
    await _controller.init();
    if (mounted) setState(() => _ready = true);
  }

  @override
  Widget build(BuildContext context) {
    if (!_ready) {
      return const MaterialApp(
        home: Scaffold(body: Center(child: CircularProgressIndicator())),
      );
    }
    return App(controller: _controller);
  }
}
