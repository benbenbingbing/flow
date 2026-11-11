from harness import *
import sys
s,t,u=apis();prefix=sys.argv[1] if len(sys.argv)>1 else 'r2'
def pname(a,key):return a.call('/process/key/'+key)
def init(suffix):
 key='mx_'+prefix+'_'+suffix;make_flow(s,key);raw=export(s,[key],key+'_v1');r=migrate(t,raw);assert r['publish']['code']==200,r
 return key,r

def stale():
 key,r=init('stale');update_flow(s,key,'源_v2');raw2=export(s,[key],key+'_v2');u2=upload(t,raw2);i2=u2['data']['id'];a2=analyze(t,i2)
 update_flow(s,key,'源_v3');raw3=export(s,[key],key+'_v3');r3=migrate(t,raw3);assert r3['publish']['code']==200,r3
 before=fingerprint('target');p=publish(t,i2);after=fingerprint('target');current=pname(t,key)
 return p['code']!=200 and before==after and current['processName']=='源_v3',{'analysis':a2,'publishOld':p,'currentName':current['processName'],'unchangedConfig':before==after,'oldImportId':i2,'newImportId':r3['upload']['data']['id']}
case('L01','已分析旧包晚于新版发布','拒绝旧包且保留v3配置',stale)

def local():
 key,r=init('local');update_flow(s,key,'源_v2');raw=export(s,[key],key+'_v2');up=upload(t,raw);i=up['data']['id'];an=analyze(t,i)
 update_flow(t,key,'目标本地修改');before=fingerprint('target');p=publish(t,i);current=pname(t,key)
 return p['code']!=200 and before==fingerprint('target') and current['processName']=='目标本地修改',{'publish':p,'currentName':current['processName'],'unchangedConfig':before==fingerprint('target'),'importId':i}
case('L02','分析后目标环境本地发布','拒绝过期分析且保护本地配置',local)

def reanalysis():
 key,r=init('reanalyze');update_flow(s,key,'源_v2');r2=migrate(t,export(s,[key],key+'_v2'));assert r2['publish']['code']==200,r2;i=r2['upload']['data']['id']
 h1=sql('matrix_target',"SELECT target_before_hash FROM config_import_item WHERE import_package_id='"+i+"'")
 a=t.call('/config-migration/imports/'+i+'/analyze',{},ok=False)
 h2=sql('matrix_target',"SELECT target_before_hash FROM config_import_item WHERE import_package_id='"+i+"'")
 status=sql('matrix_target',"SELECT status FROM config_import_package WHERE id='"+i+"'")
 p=publish(t,i);rb=rollback(t,i);current=pname(t,key)
 return h1==h2 and status=='PUBLISHED' and rb['code']==200 and current['processName']=='测试_'+key+'_v1',{'reanalyze':a,'beforeHashPreserved':h1==h2,'statusAfterReanalyze':status,'repeatPublish':p,'rollback':rb,'restoredName':current['processName'],'importId':i}
case('L03','已发布批次再次分析然后回滚','保护原发布历史，回滚恢复v1',reanalysis)

def rollback_late():
 key,r=init('rollback_late');update_flow(s,key,'源_v2');r2=migrate(t,export(s,[key],key+'_v2'));update_flow(s,key,'源_v3');r3=migrate(t,export(s,[key],key+'_v3'))
 assert r2['publish']['code']==r3['publish']['code']==200,(r2,r3)
 before=fingerprint('target');rb=rollback(t,r2['upload']['data']['id']);current=pname(t,key)
 return rb['code']!=200 and before==fingerprint('target') and current['processName']=='源_v3',{'rollbackOld':rb,'currentName':current['processName'],'unchangedConfig':before==fingerprint('target'),'oldImportId':r2['upload']['data']['id']}
case('L04','已有后续版本时回滚旧批次','拒绝回滚旧批次且保留v3',rollback_late)

def beforepublish():
 key='mx_'+prefix+'_unpublished';make_flow(s,key);up=upload(t,export(s,[key],key+'_v1'));i=up['data']['id'];an=analyze(t,i);before=fingerprint('target');rb=rollback(t,i)
 return rb['code']!=200 and before==fingerprint('target'),{'rollback':rb,'unchangedConfig':before==fingerprint('target')}
case('L05','未发布批次直接回滚','拒绝且无配置变化',beforepublish)

def normalrollback():
 key,r=init('normalrb');update_flow(s,key,'源_v2');r2=migrate(t,export(s,[key],key+'_v2'));i=r2['upload']['data']['id'];rb=rollback(t,i);cur=pname(t,key);before=fingerprint('target');rb2=rollback(t,i)
 return rb['code']==200 and cur['processName']=='测试_'+key+'_v1' and rb2['code']==200 and before==fingerprint('target'),{'rollback':rb,'restoredName':cur['processName'],'repeatRollback':rb2,'repeatNoChange':before==fingerprint('target')}
case('L06','正常升级回滚及重复回滚','恢复v1；重复请求无新增变更',normalrollback)

def repeatpub():
 key,r=init('repeatpub');i=r['upload']['data']['id'];before=fingerprint('target');p=publish(t,i)
 return p['code']==200 and before==fingerprint('target'),{'publish':p,'unchangedConfig':before==fingerprint('target')}
case('L07','重复发布同一已发布批次','幂等返回且不增加版本',repeatpub)

def newrollback():
 key,r=init('newrb');rb=rollback(t,r['upload']['data']['id']);cur=pname(t,key)
 return rb['code']==200 and cur['status']=='DISABLED',{'rollback':rb,'status':cur['status']}
case('L08','回滚全新导入资产','新流程被停用',newrollback)

def draftchange():
 key,r=init('draft');update_flow(s,key,'源_v2');up=upload(t,export(s,[key],key+'_v2'));i=up['data']['id'];analyze(t,i)
 pr=pname(t,key);t.call('/process/'+pr['id']+'/update',{'processKey':key,'processName':'本地未发布草稿','bpmnXml':pr['bpmnXml'],'expectedRevision':pr['revision']})
 before=fingerprint('target');p=publish(t,i);cur=pname(t,key)
 return p['code']!=200 and before==fingerprint('target') and cur['processName']=='本地未发布草稿',{'publish':p,'currentName':cur['processName'],'unchangedConfig':before==fingerprint('target'),'importId':i}
case('L09','分析后目标修改未发布草稿','拒绝覆盖本地未发布草稿',draftchange)

def downgrade():
 key,r=init('downgrade');update_flow(s,key,'源_v2');old=export(s,[key],key+'_v2');update_flow(s,key,'源_v3');r3=migrate(t,export(s,[key],key+'_v3'));assert r3['publish']['code']==200,r3
 up=upload(t,old);i=up['data']['id'];an=analyze(t,i);before=fingerprint('target');p=publish(t,i);cur=pname(t,key)
 return an['blocked'] and p['code']!=200 and before==fingerprint('target') and cur['processName']=='源_v3',{'analysis':an,'publish':p,'currentName':cur['processName'],'unchangedConfig':before==fingerprint('target'),'importId':i}
case('L10','新版已生效后才导入旧版本','分析阻断降级且保留新版',downgrade)

def consistentrollback():
 key,r=init('consistent');r2=migrate(t,export(s,[key],key+'_same'));i=r2['upload']['data']['id'];rb=rollback(t,i);cur=pname(t,key)
 return r2['analysis']['items'][0]['comparisonStatus']=='CONSISTENT' and rb['code']==200 and cur['status']=='PUBLISHED' and cur['processName']=='测试_'+key+'_v1',{'analysis':r2['analysis'],'rollback':rb,'name':cur['processName'],'status':cur['status']}
case('L11','一致复用批次回滚','原有配置仍可用',consistentrollback)
