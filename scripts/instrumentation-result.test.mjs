import { test } from 'node:test';
import assert from 'node:assert/strict';
import { completedTests } from './instrumentation-result.mjs';

const success = 'INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n';
test('accepts a completed instrumentation assertion', () => assert.equal(completedTests(success, 1), 1));
test('rejects failed assertion even when am instrument exits zero', () => {
  assert.throws(() => completedTests(success.replace('STATUS_CODE: 0', 'STATUS_CODE: -2'), 1));
});
test('rejects skipped, empty and incomplete runs', () => {
  for (const output of ['', 'OK (0 tests)\nINSTRUMENTATION_CODE: -1\n',
    success.replace('STATUS_CODE: 0', 'STATUS_CODE: -3'), success.replace('INSTRUMENTATION_CODE: -1', '')]) {
    assert.throws(() => completedTests(output, 1));
  }
});
test('rejects an unexpected test count', () => assert.throws(() => completedTests(success, 7)));
