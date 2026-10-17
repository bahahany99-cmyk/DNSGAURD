import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:dns_guardian/core/crypto.dart';
import 'package:dns_guardian/l10n/app_localizations.dart';
import 'package:dns_guardian/services/security_controller.dart';

class PasswordEnrollmentScreen extends StatefulWidget {
  const PasswordEnrollmentScreen({super.key});

  @override
  State<PasswordEnrollmentScreen> createState() => _PasswordEnrollmentScreenState();
}

class _PasswordEnrollmentScreenState extends State<PasswordEnrollmentScreen> {
  final _c1 = TextEditingController();
  final _c2 = TextEditingController();
  int _strength = 0;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _c1.dispose();
    _c2.dispose();
    super.dispose();
  }

  void _onChanged(String v) => setState(() => _strength = estimatePasswordStrength(v));

  Future<void> _submit(bool finalEnroll) async {
    final a = _c1.text;
    final b = _c2.text;
    if (a.length < 12) {
      setState(() => _error = AppLocalizations.of(context).enrollHint);
      return;
    }
    if (a != b) {
      setState(() => _error = 'Passwords do not match');
      return;
    }
    setState(() => _busy = true);
    final c = context.read<SecurityController>();
    final ok = finalEnroll ? await c.enrollPassword(a) : await c.devSetPassword(a);
    if (!mounted) return;
    if (ok) {
      // Dev enroll should still require lock afterwards in release; here we go to lock.
      await c.verifyPassword(a); // authenticate immediately after enroll
      context.go('/');
    } else {
      setState(() {
        _busy = false;
        _error = 'Enrollment failed (native unavailable?)';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final strengthLabel = ['', 'Weak', 'Fair', 'Good', 'Strong'][_strength];
    return Scaffold(
      appBar: AppBar(title: Text(l.enrollPassword)),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 380),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(l.enrollHint, textAlign: TextAlign.center),
                const SizedBox(height: 16),
                TextField(
                  controller: _c1,
                  obscureText: true,
                  onChanged: _onChanged,
                  decoration: InputDecoration(
                    labelText: l.passwordHint,
                    border: const OutlineInputBorder(),
                    errorText: _error,
                  ),
                ),
                const SizedBox(height: 8),
                LinearProgressIndicator(value: _strength / 4),
                Text('Strength: $strengthLabel'),
                const SizedBox(height: 12),
                TextField(
                  controller: _c2,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Confirm password',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _busy ? null : () => _submit(false),
                  child: _busy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Set (dev)'),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _busy ? null : () => _submit(true),
                  child: const Text('Final enrollment (Phase 9)'),
                ),
                const SizedBox(height: 12),
                Text(l.limitationNote, style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
