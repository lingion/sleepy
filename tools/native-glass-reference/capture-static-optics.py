import argparse,glob,json,pathlib,re,subprocess,time
parser=argparse.ArgumentParser()
parser.add_argument('--simulator',required=True)
parser.add_argument('--output',required=True)
args=parser.parse_args()
output=pathlib.Path(args.output).resolve(); output.mkdir(parents=True,exist_ok=True)
started=time.time(); seen=set()
containers=pathlib.Path.home()/'Library/Developer/CoreSimulator/Devices'/args.simulator/'data/Containers/Data/Application'
pattern=str(containers/'*/Documents/optics-capture-signals.jsonl')

def wait_for_actual_down(signal):
    run=re.sub(r'[^a-zA-Z0-9_.-]','_',signal['run'])
    deadline=time.time()+12
    while time.time()<deadline:
        for candidate in glob.glob(str(containers/'*'/'Documents'/'reference'/run/'trace.jsonl')):
            trace=pathlib.Path(candidate)
            try: origin=json.loads((trace.parent/'session.json').read_text())['originUnixSeconds']
            except (OSError,json.JSONDecodeError): continue
            for line in trace.read_text().splitlines():
                try: row=json.loads(line)
                except json.JSONDecodeError: continue
                if row.get('event')=='touch' and row.get('source')=='delivery' and row.get('phase')==0:
                    actual=origin+row['deliveredT']
                    if actual>=signal['requestedAtUnixSeconds']:
                        return actual
        time.sleep(.015)
    return None
with (output/'capture-events.jsonl').open('a',buffering=1) as events:
    while True:
        for file in glob.glob(pattern):
            for line in pathlib.Path(file).read_text().splitlines():
                try: signal=json.loads(line)
                except json.JSONDecodeError: continue
                key=(signal['run'],signal['name'],signal['requestedAtUnixSeconds'])
                if key in seen: continue
                seen.add(key)
                if signal['requestedAtUnixSeconds']<started: continue
                down=wait_for_actual_down(signal)
                delay=(down+.65 if down is not None else signal['requestedAtUnixSeconds']+.95)-time.time()
                if delay>0: time.sleep(delay)
                safe=re.sub(r'[^a-zA-Z0-9_.-]+','_',signal['name'])
                run=re.sub(r'[^a-zA-Z0-9_.-]+','_',signal['run'])
                directory=output/run;directory.mkdir(exist_ok=True)
                path=directory/(safe+'.png')
                begin=time.time()
                capture=subprocess.run(['xcrun','simctl','io',args.simulator,'screenshot',str(path)],capture_output=True,text=True)
                row={**signal,'observedDownUnixSeconds':down,
                     'screenshotBeginUnixSeconds':begin,'screenshotEndUnixSeconds':time.time(),
                     'path':str(path),'exitCode':capture.returncode,'output':capture.stdout+capture.stderr,
                     'notice':'Held status unverified until timestamp bracket is joined to actual delivered DOWN/UP.'}
                events.write(json.dumps(row)+'\n')
                print(json.dumps({'name':signal['name'],'exitCode':capture.returncode}),flush=True)
        time.sleep(.05)
