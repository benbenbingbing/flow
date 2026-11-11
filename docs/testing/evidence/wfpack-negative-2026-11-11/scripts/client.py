import json,urllib.request,urllib.error,pathlib,hashlib,uuid,sys
from env import BASE,sql
class API:
 def __init__(self,name): self.name=name; self.url='http://127.0.0.1:'+str({'source':18091,'target':18092,'target2':18093}[name]);self.token=None
 def raw(self,path,data=None,method=None,headers=None):
  hs={'Content-Type':'application/json'};hs.update(headers or {})
  if self.token:hs['Authorization']='Bearer '+self.token
  if isinstance(data,(dict,list)):data=json.dumps(data).encode()
  try:
   with urllib.request.urlopen(urllib.request.Request(self.url+'/api'+path,data=data,headers=hs,method=method or ('POST' if data is not None else 'GET')),timeout=90) as r: return r.status,r.read(),dict(r.headers)
  except urllib.error.HTTPError as e:return e.code,e.read(),dict(e.headers)
 def call(self,path,data=None,ok=True):
  status,raw,_=self.raw(path,data)
  try:r=json.loads(raw)
  except: raise RuntimeError((status,raw[:300]))
  if ok and (status!=200 or r.get('code') not in (0,200,'200')):raise RuntimeError(f'{self.name} {path} HTTP {status} '+json.dumps(r,ensure_ascii=False))
  return r.get('data') if ok else {'http':status,**r}
 def login(self):
  r=self.call('/auth/login',{'username':'admin','password':json.loads((BASE/'credentials.json').read_text())['password']});self.token=r.get('token') or r.get('accessToken');assert self.token
  print(self.name,'login OK',flush=True)
 def upload(self,data,confirm=False):
  boundary=uuid.uuid4().hex
  payload=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="test.wfpack"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+data+b'\r\n'
  if confirm:payload+=('--'+boundary+'\r\nContent-Disposition: form-data; name="confirmedChecksum"\r\n\r\n'+hashlib.sha256(data).hexdigest()+'\r\n').encode()
  payload+=('--'+boundary+'--\r\n').encode()
  status,raw,_=self.raw('/config-migration/imports',payload,headers={'Content-Type':'multipart/form-data; boundary='+boundary})
  return {'http':status,**json.loads(raw)}
 def publish_process(self,pid):
  p=self.call('/process/'+pid+'/publish-preview',{})
  (BASE/(self.name+'-'+pid+'-preview.json')).write_text(json.dumps(p,ensure_ascii=False,indent=2))
  if not p['publishable']:return {'preview':p}
  return self.call('/process/'+pid+'/publish',{'expectedRevision':p['revision'],'expectedDraftHash':p['draftHash'],'previewToken':p['previewToken'],'migrationTag':'WFPACK_QA_20261111','versionDescription':'隔离跨环境验收','markForExport':True})
def bpmn(key,body):
 return '<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" targetNamespace="wfpack-qa"><bpmn:process id="'+key+'" name="'+key+'" isExecutable="true">'+body+'</bpmn:process></bpmn:definitions>'
def chain(task='<bpmn:userTask id="review" name="审批" flowable:assignee="admin"/>'):
 return '<bpmn:startEvent id="start"/>'+task+'<bpmn:endEvent id="end"/><bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="review"/><bpmn:sequenceFlow id="f2" sourceRef="review" targetRef="end"/>'
if __name__=='__main__':
 for name in ['source','target']:
  a=API(name);a.login();print(name,'processes',a.call('/process'))
