/* z2term play — シェルと仮想ファイルの確認用台本（サイトからは読み込まない）。
   使い方: node docs/play/test/shell.test.js
   DOM に触らない部品（core / fs / shell / cmds / action の読み取り）だけを確かめる。 */
'use strict';
var path = require('path');
var base = path.join(__dirname, '..');
['strings.ja.js', 'strings.en.js', 'help.js', 'core.js', 'fs.js', 'shell.js', 'cmds.js'].forEach(function (f) { require(path.join(base, f)); });
var Z = globalThis.Z2P;
Z.lang = 'ja';
/* action.js の読み取りだけを使う（実行は DOM が要る） */
require(path.join(base, 'action.js'));

var fails = 0, n = 0;
function eq(name, got, want) {
  n++;
  if (got !== want) { fails++; console.log('NG ' + name + '\n   got:  ' + JSON.stringify(got) + '\n   want: ' + JSON.stringify(want)); }
  else console.log('ok ' + name);
}

async function run(sh, src, stdin) {
  var out = '', err = '';
  var st = await sh.run(src, function (s) { out += s; }, function (s) { err += s; }, { aborted: false }, stdin);
  return { out: out.replace(/\x1b\[[0-9;]*m/g, ''), err: err, st: st };
}

(async function () {
  var fs = new Z.FS(null);
  var sh = new Z.Shell({ fs: fs });

  eq('echo', (await run(sh, 'echo hello world')).out, 'hello world\n');
  eq('quotes', (await run(sh, 'echo "a  b" \'c  d\' e\\ f')).out, 'a  b c  d e f\n');
  eq('var', (await run(sh, 'X=42; echo "x=$X" ${X}y')).out, 'x=42 42y\n');
  eq('empty var vanishes', (await run(sh, 'echo a $NOPE b')).out, 'a b\n');
  eq('$?', (await run(sh, 'false; echo $?')).out, '1\n');
  eq('&& ||', (await run(sh, 'true && echo y || echo n; false && echo y || echo n')).out, 'y\nn\n');
  eq('cmd subst', (await run(sh, 'echo "[$(echo in $(echo deep))]"')).out, '[in deep]\n');
  eq('redirect >', (await run(sh, 'echo one > f.txt; echo two >> f.txt; cat f.txt')).out, 'one\ntwo\n');
  eq('file exists', fs.readFile('/root/f.txt'), 'one\ntwo\n');
  eq('pipe grep', (await run(sh, 'printf "a\\nb\\nab\\n" | grep a | wc -l')).out.trim(), '2');
  eq('sort uniq', (await run(sh, 'printf "b\\na\\nb\\n" | sort | uniq -c')).out, '      1 a\n      2 b\n');
  eq('head tail', (await run(sh, 'seq 10 | head -n 3 | tail -n 1')).out, '3\n');
  eq('mkdir cd pwd', (await run(sh, 'mkdir -p a/b && cd a/b && pwd')).out, '/root/a/b\n');
  eq('cd ~', (await run(sh, 'cd; pwd')).out, '/root\n');
  eq('ls', (await run(sh, 'ls')).out, 'a  f.txt\n');
  eq('glob', (await run(sh, 'touch x1.log x2.log; echo *.log')).out, 'x1.log x2.log\n');
  eq('rm', (await run(sh, 'rm x1.log; ls *.log')).out, 'x2.log\n');
  eq('not found', (await run(sh, 'nosuchcmd')).st, 127);
  eq('noos', (await run(sh, 'apk add git')).err.indexOf('OS') >= 0, true);
  eq('alias', (await run(sh, "alias hi='echo hi there'; hi you")).out, 'hi there you\n');
  eq('stderr redirect', (await run(sh, 'cat nope 2>/dev/null; echo $?')).out, '1\n');
  eq('2>&1', (await run(sh, 'cat nope 2>&1 | wc -l')).out.trim(), '1');
  eq('script', (await run(sh, 'printf "echo arg=\\$1\\n" > s.sh; sh s.sh foo')).out, 'arg=foo\n');
  eq('if refused', (await run(sh, 'if true; then echo y; fi')).st, 2);
  eq('test', (await run(sh, '[ -f f.txt ] && echo yes')).out, 'yes\n');
  eq('printf %s', (await run(sh, 'printf "%s-%s\\n" a b c d')).out, 'a-b\nc-d\n');
  eq('assign subst', (await run(sh, 'N=$(echo 3 4); echo "$N"')).out, '3 4\n');
  eq('mask', Z.mask('export API_TOKEN=abc123 ghp_abcdefghijklmnop'), 'export API_TOKEN=[z2term:masked] [z2term:masked]');
  eq('mask bare', Z.mask('echo TOKEN=abc123'), 'echo TOKEN=[z2term:masked]');
  eq('exit', (await run(sh.child(), 'exit 3; echo no')).st, 3);

  /* z2-action の読み取り */
  var prog = Z.action.parse('version=2\nhuman=on\nlaunch z2.mock.browser\nwait 600\ntap ui text=検索\ntype z2term\nenter\n');
  eq('action parse steps', prog.steps.length, 5);
  eq('action human', prog.head.human, 5);
  var p2 = Z.action.parse('version=2\nrepeat 3\n  if airplane\n    wait 10\n  else\n    repeat until ui text=OK\n      wait 5\n    end\n  end\nend\n');
  eq('action nested', p2.steps[0].body[0].els[0].op, 'repeat');
  var bad = null;
  try { Z.action.parse('version=1\nrepeat 2\nwait 1\nend\n'); } catch (e) { bad = e; }
  eq('action v2 needed', !!bad && bad.line === 2, true);
  try { bad = null; Z.action.parse('version=1\nfly away\n'); } catch (e) { bad = e; }
  eq('action unknown', !!bad && bad.line === 2, true);
  eq('action comment', Z.action.parse('version=1\nclick text=#5   # コメント\n').steps[0].sel.v, '#5');

  console.log('\n' + (n - fails) + ' / ' + n + ' ok');
  process.exit(fails ? 1 : 0);
})();
