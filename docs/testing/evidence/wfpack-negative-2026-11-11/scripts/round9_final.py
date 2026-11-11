from harness import *
s,t,u=apis();fx=make_flow(s,'mx_atomicfinal','mx_atomic_entityfinal');good=export(s,['mx_atomicfinal','mx_atomic_entityfinal'],'MX_ATOMIC9_GOOD');es=entries(good);m=json.loads(es['manifest.json']);m['packageNo']='QA_ATOMIC9_BAD';a=next(a for a in m['assets'] if a['assetType']=='ENTITY');sp=json.loads(es[a['path']]);node=sp['forms'][0]['nodes'][0];props=json.loads(node['propsDocument']);props['componentType']='qa_unsupported_component';node['propsDocument']=json.dumps(props,ensure_ascii=False,separators=(',',':'));sp['forms'][0]['fields'][0]['propsDocument']=node['propsDocument'];pure=dict(sp);pure.pop('_selection',None);a['sourceHash']=hashlib.sha256(json.dumps(pure,ensure_ascii=False,separators=(',',':')).encode()).hexdigest();es[a['path']]=json.dumps(sp,ensure_ascii=False,separators=(',',':')).encode();es['manifest.json']=json.dumps(m,ensure_ascii=False).encode();bad=zipped(refresh(es));(BASE/'T01.wfpack').write_bytes(bad)
def failedapply():
 before=fingerprint('target2');up=upload(u,bad);assert up['code']==200,up;i=up['data']['id'];an=analyze(u,i);p=publish(u,i);st=sql('matrix_target2',"SELECT status FROM config_import_package WHERE id='"+i+"'");ps=sql('matrix_target2',"SELECT publish_status FROM config_import_item WHERE import_package_id='"+i+"' ORDER BY id")
 return p['code']!=200 and before==fingerprint('target2') and 'PUBLISHING' not in ps,{'analysis':an,'publish':p,'unchangedConfigAndTables':before==fingerprint('target2'),'batchStatus':st,'itemStatuses':ps}
case('T01','应用阶段遇到不支持的表单组件','整批失败且配置、资产、物理表无残留',failedapply)
def retry():
 r=migrate(u,good);return r['publish']['code']==200,{'analysis':r['analysis'],'publish':r['publish']}
case('T02','失败后修正包重新导入相同业务编码','正常成功，无半成品冲突',retry)
