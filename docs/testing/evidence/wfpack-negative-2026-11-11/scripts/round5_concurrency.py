from harness import *
import sys
s,t,u=apis();prefix=sys.argv[1] if len(sys.argv)>1 else 'cc'
def parallel(fn,n=4):
 with concurrent.futures.ThreadPoolExecutor(max_workers=n) as pool:return list(pool.map(fn,range(n)))
def setup(suffix):
 key='mx_'+prefix+'_'+suffix;make_flow(s,key);data=export(s,[key],key);return key,data
for n in range(3):
 def uploads(n=n):
  key,data=setup('upload'+str(n));before=import_count('target2');rs=parallel(lambda _:upload(u,data),6);ids={r.get('data',{}).get('id') for r in rs if r.get('code')==200};after=import_count('target2')
  state['fixtures'][key]=next(iter(ids)) if ids else None
  return len(ids)==1 and after-before==1 and all(r['code']==200 or r['http']==400 for r in rs),{'responses':rs,'uniqueImportIds':len(ids),'createdBatches':after-before}
 case('C%02d'%(n+1),'并发6次上传同一文件（第%d轮）'%(n+1),'只有一个批次，重复请求返回相同ID或明确冲突',uploads)
for n in range(3):
 def publishes(n=n):
  key,data=setup('publish'+str(n));i=upload(u,data)['data']['id'];analyze(u,i);rs=parallel(lambda _:publish(u,i),4);current=u.call('/process/key/'+key);count=int(sql('matrix_target2',"SELECT COUNT(*) FROM process_definition_config WHERE process_key='"+key+"'"))
  return all(r['code']==200 for r in rs) and count==1 and current['version']==1,{'responses':rs,'processCount':count,'version':current['version']}
 case('C%02d'%(n+4),'并发4次发布同一批次（第%d轮）'%(n+1),'幂等成功，流程只有一份且只发布v1',publishes)
def analyze_publish():
 key,data=setup('anpub');i=upload(u,data)['data']['id'];analyze(u,i)
 rs=parallel(lambda n:publish(u,i) if n%2 else u.call('/config-migration/imports/'+i+'/analyze',{},ok=False),8)
 st=sql('matrix_target2',"SELECT status FROM config_import_package WHERE id='"+i+"'");cur=u.call('/process/key/'+key)
 return st=='PUBLISHED' and cur['version']==1,{'responses':rs,'status':st,'version':cur['version']}
case('C07','分析与发布8请求交错','最终PUBLISHED，发布历史不被分析回退',analyze_publish)
def multirb():
 key,data=setup('multirb');r=migrate(u,data);i=r['upload']['data']['id'];rs=parallel(lambda _:rollback(u,i),4);cur=u.call('/process/key/'+key)
 return all(x['code']==200 for x in rs) and cur['status']=='DISABLED',{'responses':rs,'status':cur['status']}
case('C08','并发4次回滚同一批次','回滚幂等且只停用新流程',multirb)
def twopack():
 key,data=setup('two');data2=export(s,[key],key+'_second');i1=upload(u,data)['data']['id'];i2=upload(u,data2)['data']['id'];analyze(u,i1);analyze(u,i2)
 rs=parallel(lambda n:publish(u,[i1,i2][n]),2);cur=u.call('/process/key/'+key);count=int(sql('matrix_target2',"SELECT COUNT(*) FROM process_definition_config WHERE process_key='"+key+"'"))
 return any(r['code']==200 for r in rs) and count==1 and cur['version']==1,{'responses':rs,'count':count,'version':cur['version']}
case('C09','两个新包并发创建同名流程','一份流程且不重复发布',twopack)
