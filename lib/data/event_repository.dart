import 'dart:convert';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:path/path.dart' as p;
import 'package:crypto/crypto.dart';
import 'dart:convert' show utf8;

/// Local, append-only security-event store with a tamper-evident hash chain.
/// No update/delete API is exposed to the UI (requirement: no in-app history deletion).
/// System "Clear data" can still erase the whole DB — acknowledged as out-of-scope.

class SecurityEventRepository {
  static const String table = 'security_events';
  Database? _db;
  bool _ffiInitialized = false;

  SecurityEventRepository();

  /// Initialize. [inMemory] is used by unit tests (sqflite_ffi).
  Future<void> init({bool inMemory = false, String? path}) async {
    if (inMemory) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
      _db = await databaseFactoryFfi.openDatabase(inMemoryDatabasePath);
    } else {
      final dbPath = p.join(await getDatabasesPath(), 'dns_guardian.db');
      _db = await openDatabase(dbPath, version: 1, onCreate: _onCreate);
    }
    await _ensureSchema();
  }

  Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $table (
        id TEXT PRIMARY KEY,
        type TEXT NOT NULL,
        tsMillis INTEGER NOT NULL,
        elapsedRealtimeMs INTEGER NOT NULL,
        source TEXT,
        component TEXT,
        responseAttempted TEXT,
        responseVerified INTEGER NOT NULL DEFAULT 0,
        prevHash TEXT NOT NULL,
        hash TEXT NOT NULL,
        diagnostic TEXT
      )
    ''');
  }

  Future<void> _ensureSchema() async {
    final tables = await _db!.query('sqlite_master',
        where: 'type = ? AND name = ?', whereArgs: ['table', table]);
    if (tables.isEmpty) {
      await _onCreate(_db!, 1);
    }
  }

  String _zeroHash() =>
      '0000000000000000000000000000000000000000000000000000000000000000';

  Future<String> _lastHash() async {
    final rows = await _db!.query(table,
        columns: ['hash'], orderBy: 'tsMillis DESC', limit: 1);
    return rows.isEmpty ? _zeroHash() : rows.first['hash'] as String;
  }

  /// Append an event. Computes the hash chain. Returns the stored record map.
  Future<Map<String, dynamic>> addEvent({
    required String type,
    required int tsMillis,
    required int elapsedRealtimeMs,
    String? source,
    String? component,
    String? responseAttempted,
    bool responseVerified = false,
    String? diagnostic,
    String? id,
  }) async {
    final prevHash = await _lastHash();
    final record = <String, dynamic>{
      'id': id ?? _uuid(),
      'type': type,
      'tsMillis': tsMillis,
      'elapsedRealtimeMs': elapsedRealtimeMs,
      'source': source,
      'component': component,
      'responseAttempted': responseAttempted,
      'responseVerified': responseVerified ? 1 : 0,
      'prevHash': prevHash,
      'diagnostic': diagnostic,
    };
    final canonical = _canonical(record);
    final hash = sha256.convert(utf8.encode('$prevHash:$canonical')).toString();
    record['hash'] = hash;
    await _db!.insert(table, {
      'id': record['id'],
      'type': record['type'],
      'tsMillis': record['tsMillis'],
      'elapsedRealtimeMs': record['elapsedRealtimeMs'],
      'source': record['source'],
      'component': record['component'],
      'responseAttempted': record['responseAttempted'],
      'responseVerified': record['responseVerified'],
      'prevHash': record['prevHash'],
      'hash': record['hash'],
      'diagnostic': record['diagnostic'],
    });
    return record;
  }

  String _canonical(Map<String, dynamic> r) {
    final sorted = Map<String, dynamic>.from(r)
      ..remove('hash');
    final keys = sorted.keys.toList()..sort();
    final m = {for (var k in keys) k: sorted[k]};
    return jsonEncode(m);
  }

  String _uuid() {
    // Simple unique id without extra dependency (time + random).
    return '${DateTime.now().microsecondsSinceEpoch}-${(1000 + (DateTime.now().millisecond)).toString()}';
  }

  Future<List<Map<String, dynamic>>> getAllEvents() async {
    final rows = await _db!.query(table, orderBy: 'tsMillis DESC');
    return rows.map((r) {
      final m = Map<String, dynamic>.from(r);
      m['responseVerified'] = (r['responseVerified'] as int) == 1;
      return m;
    }).toList();
  }

  Future<List<Map<String, dynamic>>> getEventsByType(String type) async {
    final all = await getAllEvents();
    return all.where((e) => e['type'] == type).toList();
  }

  /// Verify the entire chain. Returns -1 if intact, else first tampered index.
  Future<int> verifyChain() async {
    final rows = await _db!.query(table, orderBy: 'tsMillis ASC');
    String prev = _zeroHash();
    for (int i = 0; i < rows.length; i++) {
      final r = Map<String, dynamic>.from(rows[i]);
      final record = {
        'id': r['id'],
        'type': r['type'],
        'tsMillis': r['tsMillis'],
        'elapsedRealtimeMs': r['elapsedRealtimeMs'],
        'source': r['source'],
        'component': r['component'],
        'responseAttempted': r['responseAttempted'],
        'responseVerified': r['responseVerified'],
        'prevHash': r['prevHash'],
        'diagnostic': r['diagnostic'],
      };
      final canonical = _canonical(record);
      final expected = sha256.convert(utf8.encode('$prev:$canonical')).toString();
      if (expected != r['hash']) return i;
      prev = expected;
    }
    return -1;
  }

  Future<int> count() async {
    final r = await _db!.query(table, columns: ['COUNT(*) as c']);
    return (r.first['c'] as int?) ?? 0;
  }

  Future<void> close() async => await _db?.close();
}
