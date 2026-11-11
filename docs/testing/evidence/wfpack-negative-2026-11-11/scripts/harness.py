from client import *
import zipfile,io,time,traceback,threading,concurrent.futures,copy,re,warnings,fcntl
warnings.filterwarnings('ignore',category=UserWarning)
STATE=BASE/'matrix-state.json'
state=json.loads(STATE.read_text()) if STATE.exists() else {'cases':[],'fixtures':{}}
def save():STATE.write_text(json.dumps(state,ensure_ascii=False,indent=2))
def case(id,name,expected,fn):
 try:
  passed,observed=fn();entry={'id':id,'name':name,'expected':expected,'passed':bool(passed),'observed':observed}
 except Exception as exc:entry={'id':id,'name':name,'expected':expected,'passed':False,'harnessError':str(exc),'trace':traceback.format_exc()}
 with (BASE/'state.lock').open('w') as lock:
  fcntl.flock(lock,fcntl.LOCK_EX);latest=json.loads(STATE.read_text());latest['cases']=[c for c in latest['cases'] if c['id']!=id]+[entry];STATE.write_text(json.dumps(latest,ensure_ascii=False,indent=2));state.update(latest)
 print(('PASS' if entry['passed'] else 'FAIL'),id,name,flush=True)
 return entry
def apis():
 a=[API(n) for n in ['source','target','target2']]
 for x in a:x.login()
 return a

def fingerprint(name):
 db='matrix_'+name
 queries={'process':'SELECT id,process_key,process_name,status,version,draft_revision,bpmn_xml FROM process_definition_config ORDER BY id','entity':'SELECT id,entity_code,entity_name,status,process_definition_id FROM entity_definition ORDER BY id','forms':'SELECT id,entity_id,form_key,form_name FROM entity_form ORDER BY id','nodes':'SELECT id,form_id,node_key,props_document FROM entity_form_node ORDER BY id','assets':'SELECT asset_type,business_key,source_version,content_hash FROM config_migration_asset ORDER BY id','tables':"SHOW TABLES LIKE 'biz%'"}
 return {k:hashlib.sha256(sql(db,q).encode()).hexdigest() for k,q in queries.items()}
def import_count(name):return int(sql('matrix_'+name,'SELECT COUNT(*) FROM config_import_package'))
def upload(a,data,confirm=True):return a.upload(data,confirm)
def analyze(a,i):return a.call('/config-migration/imports/'+i+'/analyze',{})
def publish(a,i):return a.call('/config-migration/imports/'+i+'/publish',{},ok=False)
def rollback(a,i):return a.call('/config-migration/imports/'+i+'/rollback',{},ok=False)
def migrate(a,data):
 u=upload(a,data);assert u['code']==200,u
 i=u['data']['id'];an=analyze(a,i);p=publish(a,i)
 return {'upload':u,'analysis':an,'publish':p}
def make_flow(s,key,code=None,assignee='admin'):
 p=s.call('/process',{'processKey':key,'processName':'测试_'+key+'_v1','bpmnXml':bpmn(key,chain('<bpmn:userTask id="review" name="审批" flowable:assignee="'+assignee+'"/>'))})
 if code:
  e=s.call('/entity',{'entityCode':code,'entityName':'测试_'+code,'fields':[{'fieldCode':'note','fieldName':'备注','fieldType':'STRING','fieldLength':100}]})
  s.call('/entity/'+e['id']+'/workflow-binding/update',{'processDefinitionId':p['id']});s.call('/entity/'+e['id']+'/publish',{'migrationTag':'MATRIX'})
  f=s.call('/entity-form',{'entityId':e['id'],'formKey':'main','formName':'测试表单','isDefault':True,'fields':[]})
  s.call('/entity-forms/'+f['id']+'/nodes',{'nodeKey':'note','nodeType':'FIELD','bindingType':'ENTITY_FIELD','bindingRef':'note','props':{'fieldCode':'note','fieldType':'STRING','componentType':'input','label':'备注'}})
  fp=s.call('/entity-forms/'+f['id']+'/publish-preview',{});s.call('/entity-forms/'+f['id']+'/publish',{'expectedDraftHash':fp['draftHash'],'expectedActiveReleaseId':fp['activeReleaseId'],'impactToken':fp['impactToken']})
  props='<bpmn:extensionElements><flowable:properties><flowable:property name="entityFormId" value="'+f['id']+'"/><flowable:property name="entityFormBindingMode" value="CUSTOM"/></flowable:properties></bpmn:extensionElements>'
  xml=bpmn(key,chain('<bpmn:userTask id="review" name="审批" flowable:assignee="'+assignee+'">'+props+'</bpmn:userTask>').replace('<bpmn:startEvent id="start"/>','<bpmn:startEvent id="start">'+props+'</bpmn:startEvent>'))
  p=s.call('/process/'+p['id']+'/update',{'processKey':key,'processName':p['processName'],'bpmnXml':xml,'expectedRevision':p['revision']})
 else:e=f=None
 pub=s.publish_process(p['id']);assert pub.get('version')==1,pub
 return {'process':p,'entity':e,'form':f}
def update_flow(s,key,label):
 p=s.call('/process/key/'+key)
 s.call('/process/'+p['id']+'/update',{'processKey':key,'processName':label,'bpmnXml':p['bpmnXml'],'expectedRevision':p['revision']})
 return s.publish_process(p['id'])
def export(s,keys,tag,selections=None,validate_only=None):
 latest={}
 for a in s.call('/config-migration/assets'):
  if a['businessKey'] in keys and a['markForExport']:
   k=(a['assetType'],a['businessKey'])
   if k not in latest or a['sourceVersion']>latest[k]['sourceVersion']:latest[k]=a
 ex=s.call('/config-migration/packages/export',{'assetIds':[a['id'] for a in latest.values()],'migrationTag':tag,'selections':{a['id']:selections[a['businessKey']] for a in latest.values() if selections and a['businessKey'] in selections},'validateOnlyDependencies':validate_only or []})
 _,raw,_=s.raw('/config-migration/packages/'+ex['id']+'/download');(BASE/(tag+'.wfpack')).write_bytes(raw)
 return raw

def entries(raw):
 with zipfile.ZipFile(io.BytesIO(raw)) as z:return {n:z.read(n) for n in z.namelist()}
def zipped(es):
 o=io.BytesIO()
 with zipfile.ZipFile(o,'w',zipfile.ZIP_DEFLATED) as z:
  for n,b in es.items():z.writestr(n,b)
 return o.getvalue()
def refresh(es):
 es['checksums.json']=json.dumps({n:hashlib.sha256(b).hexdigest() for n,b in es.items() if n not in ['checksums.json','signature.sig']},ensure_ascii=False,separators=(',',':')).encode()
 return es
