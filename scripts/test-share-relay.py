#!/usr/bin/env python3
"""Opt-in integration check: the phone publishes only generated test fixtures."""
import argparse, tempfile
import hashlib, html.parser, json, pathlib, re, shlex, subprocess, sys, time, urllib.error, urllib.parse, urllib.request
from share_browser import BrowserReceiver

parser = argparse.ArgumentParser(description='Publish generated Android fixtures over cellular and receive them on this PC. Install the debug and androidTest APKs first.')
parser.add_argument('--profile', required=True, help='Saved SSH profile ID in the debug app; verify its host key first')
parser.add_argument('--origin', required=True, help='Your HTTPS reverse proxy origin, without a path')
parser.add_argument('--remote-port', type=int, default=8080, help='Loopback forwarding port on your SSH server')
parser.add_argument('--serial', required=True, help='ADB device serial; keep its default network on cellular')
parser.add_argument('--output-dir', type=pathlib.Path, help='Directory for test logs and generated downloads')
parser.add_argument('--browser-bin', help='Browser binary (defaults to an installed Chromium-compatible browser)')
parser.add_argument('--driver', default='chromedriver', help='WebDriver binary')
args = parser.parse_args()
parsed = urllib.parse.urlsplit(args.origin)
if (parsed.scheme != 'https' or not parsed.hostname or parsed.path not in ('', '/') or
    parsed.username or parsed.password or parsed.query or parsed.fragment or
    not 1024 <= args.remote_port <= 65535 or not re.fullmatch(r'[A-Za-z0-9_-]+', args.profile)):
    parser.error('Use a saved profile ID, an HTTPS origin without a path and a port from 1024 to 65535')
args.origin = args.origin.rstrip('/')

SERIAL = args.serial
ROOT = args.output_dir or pathlib.Path(tempfile.mkdtemp(prefix='z2term-relay-test-'))
ROOT.mkdir(parents=True, exist_ok=True)
print('Test artifacts: ' + str(ROOT.resolve()), flush=True)
EXPECTED = bytes(i % 251 for i in range(1024 * 1024))
HASH = hashlib.sha256(EXPECTED).hexdigest()
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, hdrs, newurl): return None
opener = urllib.request.build_opener(NoRedirect())
def request(url, method='GET', headers=None):
    req = urllib.request.Request(url, data=b'' if method == 'POST' else None, method=method,
        headers={'Accept-Language':'en', **(headers or {})})
    try: response = opener.open(req, timeout=40)
    except urllib.error.HTTPError as e: response = e
    with response: return response.status, response.headers, response.read()
class Links(html.parser.HTMLParser):
    def __init__(self): super().__init__(); self.links = {}; self.href = None; self.label = ''
    def handle_starttag(self, tag, attrs):
        if tag == 'a': self.href = dict(attrs).get('href'); self.label = ''
    def handle_data(self, data):
        if self.href: self.label += data
    def handle_endtag(self, tag):
        if tag == 'a' and self.href: self.links[self.label] = self.href; self.href = None

def links(url):
    code, headers, body = request(url)
    assert code == 200, (code, body[:160])
    parser = Links(); parser.feed(body.decode())
    return {label:urllib.parse.urljoin(url, href) for label,href in parser.links.items()}
def select(items, name):
    matches = [u for label,u in items.items() if label.rstrip('/') == name or label.startswith(name + ' ')]
    assert len(matches) == 1, (name, items)
    return matches[0]
def receive(data):
    began = time.monotonic()
    url, kind = data['relay_test_url'], data['relay_test_kind']
    assert data['relay_test_sha256'] == HASH
    assert re.fullmatch(re.escape(args.origin) + r'/share/[A-Za-z0-9_-]+/', url), url
    origin = urllib.parse.urlsplit(url); origin = origin.scheme + '://' + origin.netloc
    root_links = links(url)
    if kind == 'folder':
        empty = select(root_links,'empty')
        assert b'This folder is empty.' in request(empty)[2]
        folder = select(root_links,'nested')
        file_url = select(links(folder), 'payload.bin')
        readme = select(root_links,'readme.txt')
    else: file_url = url; readme = None
    code,_,body = request(file_url)
    assert code == 200 and b'Receive and save' in body
    assert request(file_url+'file')[0] == 403
    assert request(file_url+'accept','POST',{'Origin':'https://unrelated.example'})[0] == 403
    code, headers, _ = request(file_url+'accept', 'POST', {'Origin':origin, 'Sec-Fetch-Site':'same-origin'})
    assert code == 303, code
    assert '; Secure' in headers['Set-Cookie']
    cookie = headers['Set-Cookie'].split(';')[0]
    target = urllib.parse.urljoin(url, headers['Location'])
    code, headers, payload = request(target, headers={'Cookie':cookie})
    assert code == 200 and payload == EXPECTED
    assert hashlib.sha256(payload).hexdigest() == HASH
    (ROOT / ('received-' + kind + '.bin')).write_bytes(payload)
    code,headers,part = request(target, headers={'Cookie':cookie,'Range':'bytes=1024-2047'})
    assert code == 206 and part == EXPECTED[1024:2048]
    if readme:
        assert request(readme+'file', headers={'Cookie':cookie})[0] == 403
        code,headers,_ = request(readme+'accept','POST', {'Origin':origin})
        assert code == 303
        code,_,text = request(readme+'file',headers={'Cookie':headers['Set-Cookie'].split(';')[0]})
        assert code == 200 and text == b'Synthetic relay integration fixture\n'
    with BrowserReceiver(ROOT / ('browser-' + kind + '-' + str(time.time_ns())), args.browser_bin, args.driver) as browser:
        browser.open(url)
        if kind == 'folder':
            browser.click('a[href="' + urllib.parse.urlsplit(folder).path + '"]')
            browser.click('a[href="' + urllib.parse.urlsplit(file_url).path + '"]')
        received = browser.receive(EXPECTED)
        assert received['sha256'] == HASH
    result = {'kind':kind,'bytes':len(payload),'sha256':HASH,'browser_download':True,'seconds':round(time.monotonic()-began,2)}
    print('PC_RECEIVED ' + json.dumps(result), flush=True)
    results.append(result)
    completion = data['relay_test_completion']
    assert re.fullmatch(r'/storage/emulated/0/Android/data/com\.zerotoship\.z2term\.debug2/files/relay-test-[a-f0-9-]+\.done',completion)
    subprocess.run(['adb','-s',SERIAL,'shell',"printf '%s' " + shlex.quote(HASH) + ' > ' + shlex.quote(completion + '.tmp') + ' && mv ' + shlex.quote(completion + '.tmp') + ' ' + shlex.quote(completion)],check=True)
def stopped(url):
    try: code,_,body = request(url+'health')
    except (urllib.error.URLError, TimeoutError):
        print('PC_STOP_CONFIRMED unreachable', flush=True)
        return
    assert code != 200, (code, body[:160])
    print('PC_STOP_CONFIRMED ' + str(code), flush=True)

command = ['adb','-s',SERIAL,'shell','am','instrument','-w','-r','-e','class',
 'com.zerotoship.z2term.share.ShareRelayDeviceTest','-e','relaySelfHosted','true','-e','relayProfile',args.profile,
 '-e','relayOrigin',shlex.quote(args.origin),'-e','relayPort',str(args.remote_port),
 'com.zerotoship.z2term.debug2.test/androidx.test.runner.AndroidJUnitRunner']
results=[]; lines=[]; status={}; receiver_failure=None
with (ROOT/'device-instrumentation.log').open('w') as log:
    p = subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    try:
        for line in p.stdout:
            log.write(line); log.flush(); lines.append(line)
            print(line.rstrip(),flush=True)
            if line.startswith('INSTRUMENTATION_STATUS: '):
                key,sep,val = line.removeprefix('INSTRUMENTATION_STATUS: ').rstrip('\n').partition('=')
                if sep: status[key]=val
            elif line.startswith('INSTRUMENTATION_STATUS_CODE:'):
                if 'relay_test_url' in status:
                    try: receive(status)
                    except Exception as error:
                        receiver_failure = error
                        print('PC_RECEIVER_FAILED ' + repr(error), flush=True)
                        completion = status.get('relay_test_completion', '')
                        if re.fullmatch(r'/storage/emulated/0/Android/data/com\.zerotoship\.z2term\.debug2/files/relay-test-[a-f0-9-]+\.done', completion):
                            subprocess.run(['adb','-s',SERIAL,'shell',"printf FAILED > " + shlex.quote(completion + '.tmp') + ' && mv ' + shlex.quote(completion + '.tmp') + ' ' + shlex.quote(completion)], check=True)
                if 'relay_test_stopped' in status: stopped(status['relay_test_stopped'])
                status={}
        assert p.wait() == 0
        assert receiver_failure is None, receiver_failure
        assert len(results) == 2 and any('OK (1 test)' in line for line in lines), ''.join(lines)
    finally:
        if p.poll() is None: p.terminate(); p.wait()
(ROOT/'device-result.json').write_text(json.dumps(results,indent=2)+'\n')
print('SELF_HOSTED_CELLULAR_PHONE_TO_PC_FILE_AND_FOLDER_PASS',flush=True)
