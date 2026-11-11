from harness import *
s,t,u=apis();parent='mx_a_parent10b';child='mx_z_child10b';raw=(BASE/'MX_SUBFORM10_AUTO.wfpack').read_bytes();es=entries(raw);m=json.loads(es['manifest.json']);sourceparent=json.loads(es[next(a['path'] for a in m['assets'] if a['businessKey']==parent)]);sourcechildid=re.search(r'"childEntityId":"([0-9]+)"',sourceparent['forms'][0]['nodes'][0]['propsDocument']).group(1)
def autodep():
 r=migrate(u,raw);props=sql('matrix_target2',"SELECT n.props_document FROM entity_form_node n JOIN entity_form f ON f.id=n.form_id JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+parent+"'");targetchild=sql('matrix_target2',"SELECT f.id FROM entity_form f JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+child+"' AND f.form_key='main'");targetentity=sql('matrix_target2',"SELECT id FROM entity_definition WHERE entity_code='"+child+"'");rel=sql('matrix_target2',"SELECT relation_code,data_key,child_entity_id,child_entity_code FROM entity_relation WHERE parent_entity_code='"+parent+"'")
 return r['publish']['code']==200 and targetchild in props and targetentity in props and sourcechildid not in props and targetentity in rel,{'analysis':r['analysis'],'publish':r['publish'],'targetProps':props,'targetRelation':rel,'sourceEntityIdAbsent':sourcechildid not in props}
case('F02','自动携带子表单并按依赖顺序发布','父子表单成功，引用改为目标ID，关系正确恢复',autodep)
# 从源重新发布形成干净快照，再验证新导出本身不携带源实体缓存 ID。
for code in [child,parent]:
 eid=sql('matrix_source',"SELECT id FROM entity_definition WHERE entity_code='"+code+"'");s.call('/entity/'+eid+'/publish',{'migrationTag':'SUBFORMS_FIXED'})
clean=export(s,[parent],'MX_SUBFORM10_FIXED')
def cleanexport():
 ex=entries(clean);m=json.loads(ex['manifest.json']);snap=json.loads(ex[next(a['path'] for a in m['assets'] if a['businessKey']==parent)]);props=json.dumps(snap['forms'],ensure_ascii=False)
 return sourcechildid not in props and 'childFormRef' in props,{'sourceEntityIdAbsent':sourcechildid not in props,'stableChildFormReferencePresent':'childFormRef' in props}
case('F03','重新导出子表单不含源实体缓存ID','仅携带稳定实体/表单引用',cleanexport)
def fresh():
 r=migrate(t,clean);return r['publish']['code']==200,{'analysis':r['analysis'],'publish':r['publish']}
case('F04','干净子表单包导入另一个新环境','完整成功',fresh)
def runtime():
 f=t.call('/entity-form-resolve/new-data/'+parent);r=t.call('/entity-data',{'entityCode':parent,'name':'父子表单实际保存','data':{'child_data':{'name':'子记录实际保存'}},'startProcess':False,'formId':f['id'],'formConfigHash':f['configHash'],'runtimeContext':f.get('runtimeContext',{})},ok=False)
 rid=(r.get('data') or {}).get('id');rows=sql('matrix_target',"SELECT id,note FROM biz_"+child+" WHERE note='"+str(rid)+"'") if rid else ''
 return r['code']==200 and bool(rows),{'save':r,'childRowsLinkedToParent':bool(rows),'childRows':rows}
case('F05','跨环境子表单实际创建父子业务数据','父子记录成功保存且子记录外键指向父记录',runtime)
