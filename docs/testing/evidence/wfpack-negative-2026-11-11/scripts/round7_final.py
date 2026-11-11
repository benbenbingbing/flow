from harness import *
s,t,u=apis();key='mx_partialfinal';code='mx_partial_entityfinal';fx=make_flow(s,key,code);eid=fx['entity']['id'];fid=fx['form']['id']
def pubform(a,fid):
 p=a.call('/entity-forms/'+fid+'/publish-preview',{});return a.call('/entity-forms/'+fid+'/publish',{'expectedDraftHash':p['draftHash'],'expectedActiveReleaseId':p['activeReleaseId'],'impactToken':p['impactToken']})
def entity(a,code):return {k:str(v) for k,v in json.loads(sql('matrix_'+a.name,"SELECT JSON_OBJECT('id',id) FROM entity_definition WHERE entity_code='"+code+"'")).items()}
def nodes(a,code,form):return sql('matrix_'+a.name,"SELECT n.node_key,n.props_document FROM entity_form_node n JOIN entity_form f ON f.id=n.form_id JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+code+"' AND f.form_key='"+form+"' ORDER BY n.id")
def data(a,code,label):
 f=a.call('/entity-form-resolve/new-data/'+code)
 return a.call('/entity-data',{'entityCode':code,'name':label,'data':{'note':label},'startProcess':True,'formId':f['id'],'formConfigHash':f['configHash'],'runtimeContext':f.get('runtimeContext',{})})
full=export(s,[key,code],'MX_PARTIAL7_FULL');r=migrate(t,full);assert r['publish']['code']==200,r
sel={code:{'full':False,'sections':['forms'],'formKeys':['main']}}
partial=export(s,[code],'MX_PARTIAL7_EMPTY',sel,['PROCESS:'+key])
def emptytarget():
 before=fingerprint('target2');up=upload(u,partial);i=up['data']['id'];an=analyze(u,i);p=publish(u,i)
 return p['code']!=200 and before==fingerprint('target2'),{'analysis':an,'publish':p,'unchangedConfig':before==fingerprint('target2')}
case('P01','仅表单包导入缺失所属实体的环境','拒绝发布且不创建半成品',emptytarget)
# 目标自行新增一个包外表单，并发布为自己的配置。
te=entity(t,code);lf=t.call('/entity-form',{'entityId':te['id'],'formKey':'local_only','formName':'目标独有表单','isDefault':False,'fields':[]});pubform(t,lf['id']);t.call('/entity/'+te['id']+'/publish',{'migrationTag':'LOCAL_ONLY'})
# 源仅调整 main 字段标签后再次发布，局部包只允许更新这个表单。
n=s.call('/entity-forms/'+fid+'/nodes')[0];props=json.loads(n['propsDocument']) if isinstance(n['propsDocument'],str) else n['propsDocument'];props['label']='源新版字段标签';s.call('/entity-forms/'+fid+'/nodes/'+n['id']+'/patch',{'expectedRevision':n['revision'],'props':props});pubform(s,fid);s.call('/entity/'+eid+'/publish',{'migrationTag':'PARTIAL_SOURCE'})
partial=export(s,[code],'MX_PARTIAL7_UPDATE',sel,['PROCESS:'+key]);state['fixtures']['partial7Data']=hashlib.sha256(partial).hexdigest();save()
def selected():
 localbefore=nodes(t,code,'local_only');r=migrate(t,partial);state['fixtures']['partial7Import']=r['upload']['data']['id'];save();main=nodes(t,code,'main');localafter=nodes(t,code,'local_only');names=sql('matrix_target',"SELECT f.form_key FROM entity_form f JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+code+"' ORDER BY f.form_key")
 return r['publish']['code']==200 and '源新版字段标签' in main and 'local_only' in names and localbefore==localafter,{'analysis':r['analysis'],'publish':r['publish'],'main':main,'formKeys':names,'localFormUnchanged':localbefore==localafter}
case('P02','仅选main表单更新，目标有独有表单','只更新main并保留local_only',selected)
def partialrb():
 rb=rollback(t,state['fixtures']['partial7Import']);main=nodes(t,code,'main');keys=sql('matrix_target',"SELECT f.form_key FROM entity_form f JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+code+"' ORDER BY f.form_key")
 return rb['code']==200 and '源新版字段标签' not in main and 'local_only' in keys,{'rollback':rb,'main':main,'formKeys':keys}
case('P03','回滚局部表单迁移','恢复main且保留目标独有表单',partialrb)
def missingselection():
 asset=next(a for a in s.call('/config-migration/assets') if a['businessKey']==code)
 r=s.call('/config-migration/packages/export',{'assetIds':[asset['id']],'migrationTag':'BAD_SELECTION','selections':{asset['id']:{'full':False,'sections':['forms'],'formKeys':['not_exists']}}},ok=False)
 return r['code']!=200,{'response':r}
case('P04','选择源不存在的表单导出','明确拒绝',missingselection)
def runtime(a,id):
 r=data(a,code,'局部回滚后运行验收');task=r.get('currentTaskId') or sql('matrix_'+a.name,"SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='"+r['processInstanceId']+"'")
 d=a.call('/process-task/detail/'+task);fc=d.get('formConfig') or {};res=a.call('/process-task/complete',{'taskId':task,'action':'APPROVE','comment':'wpack矩阵运行验收','entityCode':code,'recordId':r['id'],'formId':fc.get('entityFormId') or fc.get('formId'),'formConfigHash':fc.get('configHash'),'formData':{}},ok=False)
 remaining=int(sql('matrix_'+a.name,"SELECT COUNT(*) FROM ACT_RU_TASK WHERE PROC_INST_ID_='"+r['processInstanceId']+"'"));state['fixtures'][id+'Record']=r['id'];save()
 return res['code']==200 and remaining==0,{'recordId':r['id'],'taskId':task,'complete':res,'remainingTasks':remaining}
case('P05','局部迁移及回滚后实际提交审批','流程运行并审批完成',lambda:runtime(t,'partial7target'))
def thirdhop():
 raw=export(t,[key,code],'MX_THIRD_HOP7');r=migrate(u,raw);state['fixtures']['thirdHop7Import']=r['upload']['data']['id'];save();names=sql('matrix_target2',"SELECT f.form_key FROM entity_form f JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+code+"' ORDER BY f.form_key")
 return r['publish']['code']==200 and 'main' in names and 'local_only' in names,{'analysis':r['analysis'],'publish':r['publish'],'formKeys':names}
case('P06','目标环境再次导出到第三环境','再次迁移成功，两个表单均保留',thirdhop)
case('P07','第三环境实际提交审批','重新映射后的表单与流程可以运行',lambda:runtime(u,'partial7third'))
def keepdata():
 rid=state['fixtures']['partial7thirdRecord'];before=sql('matrix_target2',"SELECT id,note FROM biz_"+code+" WHERE id='"+rid+"'");rb=rollback(u,state['fixtures']['thirdHop7Import']);after=sql('matrix_target2',"SELECT id,note FROM biz_"+code+" WHERE id='"+rid+"'");st=sql('matrix_target2',"SELECT status FROM entity_definition WHERE entity_code='"+code+"'")
 return rb['code']==200 and before==after and bool(after) and st=='DISABLED',{'rollback':rb,'businessDataPreserved':before==after,'entityStatus':st}
case('P08','带业务数据的新资产回滚','停用配置且物理表及业务数据保留',keepdata)
