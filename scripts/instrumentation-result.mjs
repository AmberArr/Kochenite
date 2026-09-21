export function completedTests(output, expected) {
  const statuses = [...output.matchAll(/^INSTRUMENTATION_STATUS_CODE: (-?\d+)$/gm)]
    .map(match => Number(match[1]));
  const completed = statuses.filter(code => code === 0).length;
  const reported = output.match(/OK \((\d+) tests?\)/);
  if (!/^INSTRUMENTATION_CODE: -1$/m.test(output) || reported === null ||
      Number(reported[1]) !== completed || completed !== expected ||
      statuses.filter(code => code === 1).length !== expected ||
      statuses.some(code => code !== 0 && code !== 1)) {
    throw new Error('Instrumentation did not complete all tests successfully');
  }
  return completed;
}
