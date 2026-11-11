from harness import *
s,t,u=apis();raw=(BASE/'MX_BASELINE.wfpack').read_bytes()
def trust():
 before=import_count('target2');up=upload(u,raw,False);after=import_count('target2')
 # 同一内容已导入过，先用新包编号触发新校验，避免去重结果掩盖来源确认。
 return up['code']==200 and before==after,{'response':up,'unchangedImportCount':before==after}
def newraw():
 return export(s,['mx_baseline','mx_entity'],'MX_AUTH_ROUND8')
raw=newraw()
def no_confirm():
 before=import_count('target2');r=upload(u,raw,False)
 return r['code']==200 and r['data'].get('confirmationRequired') is True and before==import_count('target2'),{'response':r,'unchangedImportCount':before==import_count('target2')}
case('X01','跨环境不同签名密钥未确认来源','返回确认提示且不创建导入批次',no_confirm)
def bad_confirm():
 boundary=uuid.uuid4().hex;payload=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="bad.wfpack"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+raw+b'\r\n'+('--'+boundary+'\r\nContent-Disposition: form-data; name="confirmedChecksum"\r\n\r\n'+'0'*64+'\r\n--'+boundary+'--\r\n').encode()
 before=import_count('target2');status,body,_=u.raw('/config-migration/imports',payload,headers={'Content-Type':'multipart/form-data; boundary='+boundary});r=json.loads(body)
 return before==import_count('target2') and (r['code']!=200 or r['data'].get('confirmationRequired') is True),{'http':status,'response':r,'unchangedImportCount':before==import_count('target2')}
case('X02','确认值与实际文件SHA256不一致','不能完成导入且不创建批次',bad_confirm)
base=entries(raw);m0=json.loads(base['manifest.json']);pa=next(a for a in m0['assets'] if a['assetType']=='PROCESS')
def xmlcase(id,name,xml):
 es=dict(base);m=copy.deepcopy(m0);m['packageNo']='QA_XML_'+id;m['assets']=[copy.deepcopy(pa)];a=m['assets'][0];sp=json.loads(es[a['path']]);sp['bpmnXml']=xml;pure=dict(sp);pure.pop('_selection',None);a['sourceHash']=hashlib.sha256(json.dumps(pure,ensure_ascii=False,separators=(',',':')).encode()).hexdigest();es[a['path']]=json.dumps(sp,ensure_ascii=False,separators=(',',':')).encode();es['manifest.json']=json.dumps(m,ensure_ascii=False).encode();data=zipped(refresh(es));(BASE/(id+'.wfpack')).write_bytes(data)
 def run():
  before=fingerprint('target2');up=upload(u,data);assert up['code']==200,up;i=up['data']['id'];a=analyze(u,i);p=publish(u,i)
  return a['blocked'] and p['code']!=200 and before==fingerprint('target2'),{'analysis':a,'publish':p,'unchangedConfig':before==fingerprint('target2')}
 case(id,name,'分析阻断，直接发布拒绝且无配置变化',run)
xmlcase('X03','BPMN XML语法损坏','<definitions><process>')
xmlcase('X04','BPMN携带DOCTYPE外部实体','<?xml version="1.0"?><!DOCTYPE definitions [<!ENTITY qa SYSTEM "file:///tmp/wfpack-qa-nonexistent-sentinel">]><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"><bpmn:process id="mx_baseline" name="&qa;"/></bpmn:definitions>')
