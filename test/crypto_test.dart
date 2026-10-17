import 'package:flutter_test/flutter_test.dart';
import 'package:dns_guardian/core/crypto.dart';

void main() {
  group('HashChain', () {
    test('initial record hash is deterministic and non-zero', () {
      final a = {'type': 'X', 'tsMillis': 1};
      final b = {'type': 'X', 'tsMillis': 1};
      expect(HashChain.hashRecord(a, HashChain.zeroHash),
          HashChain.hashRecord(b, HashChain.zeroHash));
      expect(HashChain.hashRecord(a, HashChain.zeroHash), isNot(HashChain.zeroHash));
    });

    test('chain verifies intact', () {
      final r1 = {'type': 'A', 'tsMillis': 1};
      final r2 = {'type': 'B', 'tsMillis': 2};
      final r3 = {'type': 'C', 'tsMillis': 3};
      final h1 = HashChain.hashRecord(r1, HashChain.zeroHash);
      final h2 = HashChain.hashRecord(r2, h1);
      final h3 = HashChain.hashRecord(r3, h2);
      final chain = [
        {...r1, 'hash': h1},
        {...r2, 'hash': h2},
        {...r3, 'hash': h3},
      ];
      expect(HashChain.verifyChain(chain), -1);
    });

    test('tampered record breaks chain at its index', () {
      final r1 = {'type': 'A', 'tsMillis': 1};
      final r2 = {'type': 'B', 'tsMillis': 2};
      final r3 = {'type': 'C', 'tsMillis': 3};
      final h1 = HashChain.hashRecord(r1, HashChain.zeroHash);
      final h2 = HashChain.hashRecord(r2, h1);
      final h3 = HashChain.hashRecord(r3, h2);
      final chain = [
        {...r1, 'hash': h1},
        {...r2, 'hash': 'deadbeef'}, // tampered
        {...r3, 'hash': h3},
      ];
      expect(HashChain.verifyChain(chain), 1);
    });
  });

  group('password strength', () {
    test('empty is 0', () => expect(estimatePasswordStrength(''), 0));
    test('weak short', () => expect(estimatePasswordStrength('abc'), lessThan(2)));
    test('strong mixed', () {
      final s = estimatePasswordStrength('Tr0ub4dour&3Xq!');
      expect(s, greaterThanOrEqualTo(3));
    });
    test('no false high for pure lowercase long', () {
      // 16+ lowercase only => score 1 (length) but classes=1 => +0
      expect(estimatePasswordStrength('aaaaaaaaaaaaaaaa'), lessThan(4));
    });
  });

  group('salt', () {
    test('generateSalt is hex and length matches bytes*2', () {
      final s = generateSalt();
      expect(s.length, 32);
      expect(RegExp(r'^[0-9a-f]+$').hasMatch(s), isTrue);
    });
    test('salts differ', () => expect(generateSalt(), isNot(generateSalt())));
  });
}
