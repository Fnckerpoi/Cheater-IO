"""Run bounded protocol/render/input regressions against the isolated port.

These host sanitizer tests do not establish the stock firmware ABI or device
behavior. No device, network, installer or original firmware file is touched.
"""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import subprocess


STAGE=Path(__file__).resolve().parent
SRC=STAGE/'src'
OUT=STAGE/'build/host-regressions'
SPECS={
 'music':('music-runtime-v1',['music.c','music_test.c']),
 'music-input':('music-runtime-v1',['music_input_test.c']),
 'reader':('weread-v1',['reader.c','reader_test.c']),
 'reader-input':('weread-v1',['reader_input_test.c']),
 'reader-view':('weread-v1',['reader.c','reader_view.c','reader_view_test.c']),
 'navigation':('navigation-runtime-v1',['nav_runtime.c','nav_visual.c','nav_test.c']),
 'navigation-view':('navigation-runtime-v1',['nav_runtime.c','nav_visual.c','nav_view.c','nav_view_test.c']),
 'dashboard':('dashboard-v1',['card.c','test_card.c']),
 'ride':('didi-v1',['ride.c','test_core.c']),
 'animation-still':('animation-runtime-v1',['animation.c','still_test.c']),
 'diagnostics':('diagnostics-v1',['diagnostics.c','diagnostics_test.c']),
 'diagnostics-command':('diagnostics-v1',['diagnostics.c','command.c','command_test.c']),
 'display':('display-runtime-v1',['display_runtime.c','display_client.c','display_runtime_test.c']),
 'display-native':('display-runtime-v1',['display_runtime.c','display_client.c','display_carrier.c','native_display_page.c','native_display_file.c','native_display_test.c','../image-upload-test/render_idle.c']),
 'english-session':('context-english-v1',['english_session.c','head_gate.c','english_session_test.c']),
 'head-lifetime':('context-english-v1',['head_lifetime.c','head_lifetime_test.c']),
 'english-flow':('context-english-v1',['english_session.c','head_gate.c','english_view.c','english_flow.c','english_schedule.c','english_flow_test.c']),
 'english-wire':('context-english-v1',['english_view.c','english_wire.c','english_transfer.c','english_carrier.c','english_wire_test.c']),
 'english-store':('context-english-v1',['english_view.c','english_wire.c','english_schedule.c','english_store.c','english_store_test.c']),
 'english-files':('context-english-v1',['english_view.c','english_wire.c','english_store.c','english_files.c','english_files_test.c']),
 'english-service':('context-english-v1',['english_session.c','head_gate.c','english_view.c','english_flow.c','english_wire.c','english_transfer.c','english_schedule.c','english_store.c','english_service.c','english_service_test.c']),
 'english-progress':('context-english-v1',['english_view.c','english_wire.c','english_schedule.c','english_store.c','english_progress.c','english_progress_test.c']),
 'dots-wire':('dots-v1',['dots_view.c','dots_carrier.c','dots_wire.c','dots_wire_test.c']),
 'dots-view':('dots-v1',['dots_wire.c','dots_view.c','dots_view_test.c']),
 'dots-service':('dots-v1',['dots_view.c','dots_wire.c','dots_service.c','dots_service_test.c']),
 'dots-sender':('dots-v1',['dots_view.c','dots_wire.c','dots_sender.c','dots_sender_test.c']),
}

def one(item):
    name,(directory,names)=item
    paths=[SRC/directory/n for n in names]
    flags=['-DTDP_NATIVE_HOST_TEST'] if name=='display-native' else []
    cmd=['xcrun','clang','-std=c11','-O1','-g','-Wall','-Wextra','-Werror',
         '-fsanitize=address,undefined',*flags,*map(str,paths),'-o',str(OUT/name)]
    r=subprocess.run(cmd,capture_output=True,text=True,timeout=120)
    log=r.stdout+r.stderr
    if r.returncode==0:
        r=subprocess.run([str(OUT/name)],capture_output=True,text=True,timeout=120)
        log+=r.stdout+r.stderr
    (OUT/(name+'.log')).write_text(log)
    return name,dict(passed=r.returncode==0,output=log,
        sources={str(p.relative_to(SRC)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths})

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        rows=dict(pool.map(one,SPECS.items()))
    report=dict(passed=all(r['passed'] for r in rows.values()),groups=len(rows),
                sanitizer='address,undefined',tests=rows,deviceIO=False,
                limits=['Host core tests, not a proof of firmware ABI or hardware timing'])
    (OUT/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    for name,r in rows.items():
        print(name+': '+('PASS' if r['passed'] else 'FAIL'))
        if not r['passed']:print(r['output'][-5000:])
    assert report['passed'],'Some host regressions failed; inspect the report'

if __name__=='__main__':main()
