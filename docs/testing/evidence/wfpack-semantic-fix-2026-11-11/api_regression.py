"""在独立临时实例重放七个原始反例；凭据仅从 --base 私有文件读取，不输出令牌。"""
from pathlib import Path
import argparse, hashlib, io, json, subprocess, urllib.error, urllib.request, uuid, zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--base',type=Path,required=True)
parser.add_argument('--fixtures',type=Path,required=True)
parser.add_argument('--url',default='http://127.0.0.1:18094')
args=parser.parse_args()
result={'negativeRequests':[],'positiveCases':[]}
token=None

def sql(statement):
    p=subprocess.run(['/usr/local/mysql/bin/mysql','--no-defaults','--socket='+str(args.base/'mysql.sock'),'-uroot','--default-character-set=utf8mb4','-N','-B','semantic_target'],input=statement,capture_output=True,text=True)
    if p.returncode:raise RuntimeError(p.stderr)
    return p.stdout.strip()

def state():
    queries={
        'imports':'SELECT id,package_no,source_environment,checksum,status,published_by,published_at,error_message,SHA2(package_data,256) FROM config_import_package ORDER BY id',
        'items':'SELECT * FROM config_import_item ORDER BY id',
        'baselines':'SELECT * FROM config_asset_baseline ORDER BY id',
        'process':'SELECT id,process_key,process_name,status,version,draft_revision,bpmn_xml FROM process_definition_config ORDER BY id',
        'entity':'SELECT id,entity_code,entity_name,status,process_definition_id FROM entity_definition ORDER BY id',
        'forms':'SELECT id,entity_id,form_key,form_name FROM entity_form ORDER BY id',
        'nodes':'SELECT id,form_id,node_key,props_document FROM entity_form_node ORDER BY id',
        'assets':'SELECT asset_type,business_key,source_version,content_hash FROM config_migration_asset ORDER BY id',
        'tables':"SHOW TABLES LIKE 'biz%'"}
    return {key:hashlib.sha256(sql(query).encode()).hexdigest() for key,query in queries.items()}

def save():
    (args.base/'api-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))

def request(path,data=None,content_type='application/json',binary=False):
    headers={'Content-Type':content_type}
    if token:headers['Authorization']='Bearer '+token
    if isinstance(data,(dict,list)):data=json.dumps(data).encode()
    req=urllib.request.Request(args.url+'/api'+path,data=data,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=90) as response:status=response.status;body=response.read()
    except urllib.error.HTTPError as error:status=error.code;body=error.read()
    if binary:
        assert status==200,(path,status)
        return body
    return {'http':status,**json.loads(body)}

def call(path,data=None):
    response=request(path,data)
    assert response['http']==200 and response['code']==200,(path,response)
    return response['data']

def upload(raw,confirmed):
    boundary=uuid.uuid4().hex
    payload=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="test.wfpack"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+raw+b'\r\n'
    if confirmed:payload+=('--'+boundary+'\r\nContent-Disposition: form-data; name="confirmedChecksum"\r\n\r\n'+hashlib.sha256(raw).hexdigest()+'\r\n').encode()
    payload+=('--'+boundary+'--\r\n').encode()
    return request('/config-migration/imports',payload,'multipart/form-data; boundary='+boundary)

def negatives(phase):
    for case,fragment in [('S08','sourceVersion'),('S09','sourceVersion'),('S13','快照版本'),('S15','packageNo'),('S16','packageNo'),('S18','selection.sections'),('S23','dependencies.json')]:
        raw=(args.fixtures/(case+'.wfpack')).read_bytes()
        for confirmed in [False,True]:
            before=state();response=upload(raw,confirmed);after=state()
            passed=response['http']==400 and response['code']==400 and fragment in response['message'] and before==after
            result['negativeRequests'].append({'phase':phase,'case':case,'confirmed':confirmed,'response':response,'unchangedMigrationAndBusinessState':before==after,'passed':passed})
            save();assert passed,result['negativeRequests'][-1]
    print(phase,'14 次异常上传全部拒绝，迁移元数据及业务配置不变',flush=True)

def migrate(raw,name):
    up=upload(raw,True);assert up['http']==200 and up['code']==200,up
    batch=up['data']['id']
    analysis=call('/config-migration/imports/'+str(batch)+'/analyze',{})
    assert not analysis['blocked'],analysis
    published=call('/config-migration/imports/'+str(batch)+'/publish',{})
    assert published['status']=='PUBLISHED',published
    result['positiveCases'].append({'name':name,'passed':True,'analysis':analysis,'publish':published})
    save();print(name,'发布成功',flush=True)

def entries(raw):
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:return {name:archive.read(name) for name in archive.namelist()}

def dump(value):return json.dumps(value,ensure_ascii=False,separators=(',',':')).encode()

def earlier_package(raw):
    files=entries(raw);manifest=json.loads(files['manifest.json']);manifest['packageNo']='WFP-SEMANTIC-EARLIER'
    entity=next(asset for asset in manifest['assets'] if asset['assetType']=='ENTITY')
    snapshot=json.loads(files[entity['path']]);snapshot['definition']['entityName']='语义回归_较早实体版本'
    entity['sourceVersion']=1;entity['sourceHash']=hashlib.sha256(dump({key:value for key,value in snapshot.items() if key!='_selection'})).hexdigest()
    files[entity['path']]=dump(snapshot);files['manifest.json']=dump(manifest)
    files['checksums.json']=dump({name:hashlib.sha256(data).hexdigest() for name,data in files.items() if name not in ['checksums.json','signature.sig']})
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
        for name,data in files.items():archive.writestr(name,data)
    return output.getvalue()

credentials=json.loads((args.base/'credentials.json').read_text())
login=call('/auth/login',{'username':'admin','password':credentials['password']})
token=login.get('token') or login.get('accessToken');assert token
result['initialImportCount']=int(sql('SELECT COUNT(*) FROM config_import_package'))
assert result['initialImportCount']==0
negatives('EMPTY')
normal=(args.fixtures/'MX_BASELINE.wfpack').read_bytes()
before=state();confirmation=upload(normal,False)
assert confirmation['data']['confirmationRequired'] and before==state(),confirmation
result['validCrossEnvironmentConfirmation']={'passed':True,'createdNoImportState':True}
migrate(earlier_package(normal),'合法历史格式整包首次导入')
assert sql("SELECT entity_name FROM entity_definition WHERE entity_code='mx_entity'")=='语义回归_较早实体版本'
negatives('UPGRADE_PENDING')
migrate(normal,'合法历史格式整包升级')
assert sql("SELECT entity_name FROM entity_definition WHERE entity_code='mx_entity'")!='语义回归_较早实体版本'
negatives('CONSISTENT')

# 从当前应用实际导出格式 4，验证新增白名单没有破坏完整包及精确表单局部包。
assets=call('/config-migration/assets')
latest={}
for asset in assets:
    if asset['businessKey'] not in ['mx_entity','mx_baseline']:continue
    key=(asset['assetType'],asset['businessKey'])
    if key not in latest or asset['sourceVersion']>latest[key]['sourceVersion']:latest[key]=asset
for asset in latest.values():call('/config-migration/assets/'+str(asset['id'])+'/mark',{'markForExport':True})
for name,partial in [('当前格式完整包导出并导入',False),('当前格式 main 表单局部包导出并导入',True)]:
    chosen=[latest[('ENTITY','mx_entity')]] if partial else list(latest.values())
    selection={str(chosen[0]['id']):{'full':False,'sections':['forms'],'formKeys':['main']}} if partial else {}
    exported=call('/config-migration/packages/export',{'assetIds':[asset['id'] for asset in chosen],'migrationTag':'SEMANTIC-PARTIAL' if partial else 'SEMANTIC-FULL','selections':selection})
    raw=request('/config-migration/packages/'+str(exported['id'])+'/download',binary=True)
    manifest=json.loads(entries(raw)['manifest.json']);assert manifest['formatVersion']==4
    if partial:
        assert len(manifest['assets'])==1
        assert manifest['assets'][0]['selection']['formKeys']==['main']
    (args.base/('current-partial.wfpack' if partial else 'current-full.wfpack')).write_bytes(raw)
    migrate(raw,name)

result['flyway']={'migrations':int(sql('SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL')),'failed':int(sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0'))}
result['publishingItems']=int(sql("SELECT COUNT(*) FROM config_import_item WHERE publish_status='PUBLISHING'"))
assert result['flyway']['failed']==0 and result['publishingItems']==0
result['summary']={'negativeRequests':len(result['negativeRequests']),'negativePassed':sum(row['passed'] for row in result['negativeRequests']),'positiveCases':len(result['positiveCases']),'allPassed':True}
save();print(json.dumps(result['summary'],ensure_ascii=False),flush=True)
