import 'package:dns_guardian/data/event_repository.dart';
import 'package:dns_guardian/main.dart';
import 'package:flutter_test/flutter_test.dart';

class _FailingRepository extends SecurityEventRepository {
  int attempts = 0;

  @override
  Future<void> init({bool inMemory = false}) async {
    attempts++;
    throw StateError('simulated local database failure');
  }

  @override
  Future<void> close() async {}
}

void main() {
  testWidgets('startup failure replaces spinner with retryable safe error',
      (tester) async {
    final repository = _FailingRepository();

    await tester.pumpWidget(Bootstrap(repo: repository));
    await tester.pump();

    expect(find.text('تعذر بدء DNS Guardian'), findsOneWidget);
    expect(find.textContaining('تهيئة قاعدة البيانات المحلية'), findsOneWidget);
    expect(find.text('إعادة المحاولة'), findsOneWidget);
    expect(find.textContaining('simulated local database failure'), findsNothing);
    expect(repository.attempts, 1);

    await tester.tap(find.text('إعادة المحاولة'));
    await tester.pump();
    expect(repository.attempts, 2);
    expect(find.text('تعذر بدء DNS Guardian'), findsOneWidget);
  });
}
