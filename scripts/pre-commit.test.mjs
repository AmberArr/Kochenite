import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const hook = fileURLToPath(new URL('../.githooks/pre-commit', import.meta.url));

function fixture(run) {
  const directory = mkdtempSync(join(tmpdir(), 'kochenite-hook-'));
  const git = (...args) => {
    const result = spawnSync('git', args, { cwd: directory, encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    return result.stdout;
  };
  const write = (name, value) => writeFileSync(join(directory, name), value);
  try {
    git('init', '-q');
    write('build.gradle.kts', 'plugins {}\n');
    write('README.md', 'Initial\n');
    writeFileSync(join(directory, 'gradlew'), '#!/bin/sh\nprintf "%s\\n" "$*" > invoked\nexit "${HOOK_TEST_EXIT:-0}"\n', { mode: 0o755 });
    git('add', 'build.gradle.kts', 'README.md', 'gradlew');
    git('-c', 'user.name=Hook Test', '-c', 'user.email=hook@example.invalid', '-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'Fixture');
    const invoke = (exit = '0') => spawnSync('sh', [hook], {
      cwd: directory, encoding: 'utf8', env: { ...process.env, HOOK_TEST_EXIT: exit },
    });
    run({ git, write, invoke, directory });
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test('documentation-only commit skips Gradle', () => fixture(({ git, write, invoke, directory }) => {
  write('README.md', 'Updated\n');
  git('add', 'README.md');
  assert.equal(invoke().status, 0);
  assert.throws(() => readFileSync(join(directory, 'invoked')));
}));

test('Kotlin commit runs check only and preserves index/worktree', () => fixture(({ git, write, invoke, directory }) => {
  write('build.gradle.kts', 'plugins { base }\n');
  git('add', 'build.gradle.kts');
  const index = git('write-tree');
  assert.equal(invoke().status, 0);
  assert.equal(readFileSync(join(directory, 'invoked'), 'utf8'), '--console=plain spotlessCheck\n');
  assert.equal(git('write-tree'), index);
  assert.equal(git('diff'), '');
}));

test('partially staged Kotlin fails without invoking Gradle or modifying files', () => fixture(({ git, write, invoke, directory }) => {
  write('build.gradle.kts', 'plugins {base}\n');
  git('add', 'build.gradle.kts');
  write('build.gradle.kts', 'plugins { base }\n');
  const index = git('write-tree');
  const diff = git('diff');
  assert.equal(invoke().status, 1);
  assert.throws(() => readFileSync(join(directory, 'invoked')));
  assert.equal(git('write-tree'), index);
  assert.equal(git('diff'), diff);
}));

test('formatter failures block the commit', () => fixture(({ git, write, invoke }) => {
  write('build.gradle.kts', 'plugins {base}\n');
  git('add', 'build.gradle.kts');
  assert.equal(invoke('1').status, 1);
}));

test('module Kotlin paths trigger checks while unrelated unstaged docs are preserved', () => fixture(({ git, write, invoke, directory }) => {
  mkdirSync(join(directory, 'app/src/main/kotlin'), { recursive: true });
  write('app/src/main/kotlin/Sample.kt', 'class Sample\n');
  git('add', 'app/src/main/kotlin/Sample.kt');
  write('README.md', 'Unstaged notes\n');
  const diff = git('diff');
  assert.equal(invoke().status, 0);
  assert.equal(readFileSync(join(directory, 'invoked'), 'utf8'), '--console=plain spotlessCheck\n');
  assert.equal(git('diff'), diff);
}));

test('staged whitespace errors block even when worktree is fixed', () => fixture(({ git, write, invoke, directory }) => {
  write('README.md', 'Trailing whitespace  \n');
  git('add', 'README.md');
  write('README.md', 'Trailing whitespace\n');
  assert.notEqual(invoke().status, 0);
  assert.throws(() => readFileSync(join(directory, 'invoked')));
}));
