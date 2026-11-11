from harness import *
s,t,u=apis();parent='mx_a_parent10b';child='mx_z_child10b';fid=sql('matrix_source',"SELECT f.id FROM entity_form f JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+parent+"' AND f.form_key='main'");eid=sql('matrix_source',"SELECT id FROM entity_definition WHERE entity_code='"+parent+"'");n=s.call('/entity-forms/'+fid+'/nodes')[0];props=json.loads(n['propsDocument']);props['label']='子表单升级标签';s.call('/entity-forms/'+fid+'/nodes/'+n['id']+'/patch',{'expectedRevision':n['revision'],'props':props});p=s.call('/entity-forms/'+fid+'/publish-preview',{});s.call('/entity-forms/'+fid+'/publish',{'expectedDraftHash':p['draftHash'],'expectedActiveReleaseId':p['activeReleaseId'],'impactToken':p['impactToken']});s.call('/entity/'+eid+'/publish',{'migrationTag':'SUBFORM_UPGRADE'});raw=export(s,[parent],'MX_SUBFORM11_UPGRADE')
def upgrade_rb():
 r=migrate(u,raw);assert r['publish']['code']==200,r;i=r['upload']['data']['id'];rb=rollback(u,i);props=sql('matrix_target2',"SELECT n.props_document FROM entity_form_node n JOIN entity_form f ON f.id=n.form_id JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='"+parent+"'");rel=sql('matrix_target2',"SELECT relation_code,enabled FROM entity_relation WHERE parent_entity_code='"+parent+"'")
 return rb['code']==200 and '子表单升级标签' not in props and 'child_relation' in rel,{'publish':r['publish'],'rollback':rb,'restoredProps':props,'restoredRelation':rel}
case('F06','跨实体子表单升级后整包回滚','恢复旧表单并保留组成关系',upgrade_rb)
partial=export(s,[parent],'MX_SUBFORM11_PARTIAL',{parent:{'full':False,'sections':['forms'],'formKeys':['main']}},['ENTITY:'+child])
def partialapply():
 r=migrate(t,partial);state['fixtures']['subformPartialImport']=r['upload']['data']['id'];save();return r['publish']['code']==200,{'analysis':r['analysis'],'publish':r['publish']}
case('F07','只迁移关系子表单，复用目标实体关系','局部发布成功',partialapply)
def partialrb():
 before=sql('matrix_target',"SELECT id,note FROM biz_"+child+" ORDER BY id");rb=rollback(t,state['fixtures']['subformPartialImport']);after=sql('matrix_target',"SELECT id,note FROM biz_"+child+" ORDER BY id");rel=sql('matrix_target',"SELECT relation_code,enabled FROM entity_relation WHERE parent_entity_code='"+parent+"'")
 return rb['code']==200 and before==after and bool(after) and 'child_relation' in rel,{'rollback':rb,'childBusinessDataPreserved':before==after,'relation':rel}
case('F08','局部子表单回滚且已有父子业务记录','回滚成功，组成关系与子记录保留',partialrb)
