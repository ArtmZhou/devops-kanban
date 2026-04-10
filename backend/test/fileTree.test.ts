import * as test from 'node:test';
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { getFileTree } from '../src/utils/fileTree.js';

async function withTempDir(run: (dir: string) => Promise<void>) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'filetree-test-'));
  try {
    await run(dir);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test.test('getFileTree returns file tree for a directory', async () => {
  await withTempDir(async (dir) => {
    fs.mkdirSync(path.join(dir, 'src'));
    fs.writeFileSync(path.join(dir, 'src', 'app.ts'), 'console.log(1);');
    fs.writeFileSync(path.join(dir, 'README.md'), '# Hello');

    const tree = getFileTree(dir, dir);

    assert.equal(tree.name, path.basename(dir));
    assert.equal(tree.type, 'directory');
    assert.ok(Array.isArray(tree.children));
    assert.ok(tree.children!.length > 0);
  });
});

test.test('getFileTree excludes .git and node_modules directories', async () => {
  await withTempDir(async (dir) => {
    fs.mkdirSync(path.join(dir, '.git'));
    fs.writeFileSync(path.join(dir, '.git', 'config'), '');
    fs.mkdirSync(path.join(dir, 'node_modules'));
    fs.writeFileSync(path.join(dir, 'node_modules', 'pkg.js'), '');
    fs.mkdirSync(path.join(dir, 'src'));
    fs.writeFileSync(path.join(dir, 'src', 'app.ts'), '');

    const tree = getFileTree(dir, dir);
    const gitNode = tree.children!.find((c) => c.name === '.git');
    const nodeModulesNode = tree.children!.find((c) => c.name === 'node_modules');

    assert.ok(gitNode);
    assert.deepEqual(gitNode.children, []);
    assert.ok(nodeModulesNode);
    assert.deepEqual(nodeModulesNode.children, []);
  });
});

test.test('getFileTree marks binary files as binary', async () => {
  await withTempDir(async (dir) => {
    const binaryPath = path.join(dir, 'image.png');
    fs.writeFileSync(binaryPath, Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x00, 0x0d]));
    fs.writeFileSync(path.join(dir, 'text.txt'), 'hello');

    const tree = getFileTree(dir, dir);
    const binaryFile = tree.children!.find((c) => c.name === 'image.png');
    const textFile = tree.children!.find((c) => c.name === 'text.txt');

    assert.equal(binaryFile?.isBinary, true);
    assert.equal(textFile?.isBinary, false);
  });
});
