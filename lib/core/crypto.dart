import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';

/// Does NOT store or expose the password. Provides only hashing/verification
/// helpers used by the security repository. Real password hashing is performed
/// on the native side (Argon2id/PBKDF2) and exposed via platform channel;
/// these Dart helpers cover the event-log hash-chain which is safe to do here.

class HashChain {
  static const String zeroHash = '0000000000000000000000000000000000000000000000000000000000000000';

  /// Canonical JSON for a record (excluding its own hash).
  static String canonicalJson(Map<String, dynamic> record) {
    final sorted = Map.fromEntries(
      record.entries.where((e) => e.key != 'hash').toList()
        ..sort((a, b) => a.key.compareTo(b.key)),
    );
    return jsonEncode(sorted);
  }

  static String hashRecord(Map<String, dynamic> record, String prevHash) {
    final payload = utf8.encode('${prevHash}:${canonicalJson(record)}');
    return sha256.convert(payload).toString();
  }

  static String computeInitialHash(Map<String, dynamic> record) =>
      hashRecord(record, zeroHash);

  /// Verify a chain of records in order. Returns the index of the first
  /// tampered record, or -1 if all good.
  static int verifyChain(List<Map<String, dynamic>> records) {
    String prev = zeroHash;
    for (int i = 0; i < records.length; i++) {
      final expected = hashRecord(records[i], prev);
      if (records[i]['hash'] != expected) return i;
      prev = expected;
    }
    return -1;
  }
}

/// Simple strength estimation (no external dependency). Returns 0..4.
int estimatePasswordStrength(String password) {
  if (password.isEmpty) return 0;
  int score = 0;
  if (password.length >= 12) score++;
  if (password.length >= 16) score++;
  final hasLower = password.contains(RegExp(r'[a-z]'));
  final hasUpper = password.contains(RegExp(r'[A-Z]'));
  final hasDigit = password.contains(RegExp(r'[0-9]'));
  final hasSpecial = password.contains(RegExp(r'[^A-Za-z0-9]'));
  int classes = [hasLower, hasUpper, hasDigit, hasSpecial].where((b) => b).length;
  if (classes >= 3) score++;
  if (classes == 4 && password.length >= 12) score++;
  return score.clamp(0, 4);
}

/// Generate a random salt (hex) using a secure-ish RNG (platform secure RNG is preferred).
String generateSalt([int bytes = 16]) {
  final rng = Random.secure();
  final b = Uint8List(bytes);
  for (int i = 0; i < bytes; i++) b[i] = rng.nextInt(256);
  return b.map((e) => e.toRadixString(16).padLeft(2, '0')).join();
}
