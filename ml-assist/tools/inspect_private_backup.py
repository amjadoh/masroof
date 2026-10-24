#!/usr/bin/env python3
"""Inspect an authenticated backup in memory; optionally prepare PRIVATE human-labelled inputs.

Requires cryptography. Never prints SMS, identities, amounts or passwords.
Raw private output must stay outside the repository; Kotlin performs feature cleaning.
"""
import argparse
import collections
import getpass
import hashlib
import io
import json
import os
import pathlib
import sqlite3
import struct
import sys
import zipfile

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC


def read_backup(path, password):
    encrypted = path.read_bytes()
    if not 58 <= len(encrypted) <= 256 * 1024 * 1024:
        raise ValueError('Invalid envelope size')
    header = encrypted[:42]
    version, iterations = struct.unpack('>HI', header[8:14])
    if header[:8] != b'MSRFENV1' or version != 1 or not 1 <= iterations <= 1_000_000:
        raise ValueError('Invalid envelope')
    key = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=header[14:30], iterations=iterations).derive(password)
    plaintext = AESGCM(key).decrypt(header[30:42], encrypted[42:], header)
    with zipfile.ZipFile(io.BytesIO(plaintext)) as archive:
        entries = archive.infolist()
        if len(entries) != 3 or {e.filename for e in entries} != {'manifest.json', 'preferences.json', 'masroof.db'}:
            raise ValueError('Invalid archive entries')
        if any(e.file_size > 128 * 1024 * 1024 for e in entries):
            raise ValueError('Oversized archive entry')
        db = sqlite3.connect(':memory:')
        db.deserialize(archive.read('masroof.db'))
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA query_only=ON')
        if db.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
            raise ValueError('Database integrity check failed')
        return db, json.loads(archive.read('preferences.json'))


def human_cases(db):
    # EXPENSE/INCOME, ownership and inferred parsed families are not family labels.
    explicit = {'EXTERNAL_TRANSFER_IN': 'TRANSFER_IN', 'EXTERNAL_TRANSFER_OUT': 'TRANSFER_OUT',
                'CREDIT_CARD_PAYMENT': 'CARD_PAYMENT', 'REFUND': 'REFUND', 'CASH_WITHDRAWAL': 'WITHDRAWAL',
                'BILL_PAYMENT': 'BILL_PAYMENT', 'LOAN_REPAYMENT': 'FINANCING_INSTALLMENT', 'FEE': 'FEE'}
    corrected = {r[0] for r in db.execute('SELECT DISTINCT targetRawSmsId FROM user_correction')}
    latest = {}
    for row in db.execute('''SELECT r.rawSmsId,r.resolutionKind,r.resolvedAtEpochMillis,t.type,s.body,p.bankId
        FROM review_item r JOIN raw_sms s ON s.id=r.rawSmsId
        JOIN parsed_event p ON p.rawSmsId=r.rawSmsId
        LEFT JOIN financial_transaction t ON t.id=r.resolvedTransactionId
        WHERE r.status='RESOLVED' AND r.resolutionKind IN
        ('USER_NON_FINANCIAL','USER_FINANCIAL_TYPE','USER_EXTERNAL_TRANSFER')
        ORDER BY r.resolvedAtEpochMillis,r.id'''):
        latest[row['rawSmsId']] = row
    cases, excluded = [], collections.Counter()
    for identity, row in latest.items():
        if identity in corrected:
            excluded['NEEDS_CORRECTION_REVIEW'] += 1
            continue
        if row['bankId'] != 'BANK_ALJAZIRA':
            excluded['UNSUPPORTED_BANK'] += 1
            continue
        family = 'NON_FINANCIAL' if row['resolutionKind'] == 'USER_NON_FINANCIAL' else explicit.get(row['type'])
        if family is None:
            excluded['AMBIGUOUS_FAMILY'] += 1
            continue
        cases.append(dict(id='private-' + hashlib.sha256(identity.encode()).hexdigest()[:20], body=row['body'], family=family))
    return cases, dict(excluded)


def private_write(path, value):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as stream:
        json.dump(value, stream, ensure_ascii=False, sort_keys=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('backup', type=pathlib.Path)
    parser.add_argument('--password-stdin', action='store_true')
    parser.add_argument('--private-output', type=pathlib.Path)
    args = parser.parse_args()
    password = (sys.stdin.readline().rstrip('\r\n') if args.password_stdin else getpass.getpass('Backup password: ')).encode()
    db, preferences = read_backup(args.backup, password)
    try:
        cases, excluded = human_cases(db)
        analysis = json.loads(preferences['labAnalysis']) if preferences.get('labAnalysis') else {}
        methods = collections.Counter(json.loads(row['payload']).get('method') for row in analysis.get('snapshots', []))
        counts = {table: db.execute('SELECT COUNT(*) FROM ' + table).fetchone()[0]
                  for table in ('raw_sms', 'financial_transaction', 'commitment', 'user_correction')}
        print(json.dumps(dict(integrity='ok', counts=counts, humanFamilyExamples=len(cases),
                              excluded=excluded, publishedMethods=dict(methods)), sort_keys=True))
        if args.private_output:
            root = pathlib.Path(__file__).resolve().parents[2]
            directory = args.private_output.resolve()
            if directory == root or root in directory.parents:
                raise ValueError('Private corpus output must be outside the repository')
            directory.mkdir(parents=True, exist_ok=True, mode=0o700)
            partitions = {case['id']: 'train' for case in cases}
            private_write(directory / 'reviewed-input.json', dict(cases=cases))
            private_write(directory / 'splits.json', dict(partitions=partitions, version='private-provisional-v1', provisional=True))
    finally:
        db.close()


if __name__ == '__main__':
    main()
