from harness import *
s,t,u=apis()
def pubform(fid):
 p=s.call('/entity-forms/'+fid+'/publish-preview',{});assert p['canPublish'],p;return s.call('/entity-forms/'+fid+'/publish',{'expectedDraftHash':p['draftHash'],'expectedActiveReleaseId':p['activeReleaseId'],'impactToken':p['impactToken']})
def entity(code):
 e=s.call('/entity',{'entityCode':code,'entityName':'子表单验收_'+code,'fields':[{'fieldCode':'note','fieldName':'备注','fieldType':'STRING','fieldLength':100}]});s.call('/entity/'+e['id']+'/publish',{'migrationTag':'SUBFORMS'});f=s.call('/entity-form',{'entityId':e['id'],'formKey':'main','formName':'子表单验收','isDefault':True,'fields':[]});return e,f
ce,cf=entity('mx_z_child10b');s.call('/entity-forms/'+cf['id']+'/nodes',{'nodeKey':'note','nodeType':'FIELD','bindingType':'ENTITY_FIELD','bindingRef':'note','props':{'fieldCode':'note','fieldType':'STRING','componentType':'input','label':'子备注'}});pubform(cf['id']);s.call('/entity/'+ce['id']+'/publish',{'migrationTag':'SUBFORMS'})
pe,pf=entity('mx_a_parent10b');rel=s.call('/entity/'+pe['id']+'/relations',{'relationCode':'child_relation','relationName':'子表单组成关系','dataKey':'child_data','childEntityId':ce['id'],'childRefFieldCode':'note','relationType':'ONE_TO_ONE','ownershipType':'COMPOSITION','required':False,'enabled':True});n=s.call('/entity-forms/'+pf['id']+'/nodes',{'nodeKey':'child','nodeType':'SUB_FORM','bindingType':'RELATION','bindingRef':'child_relation','childFormId':cf['id'],'props':{'fieldType':'SUB_FORM','componentType':'sub_form','label':'子表单','componentProps':{'subFormConfig':{'childFormId':cf['id'],'repeatable':False,'layout':'form'}}}});pubform(pf['id']);s.call('/entity/'+pe['id']+'/publish',{'migrationTag':'SUBFORMS'})
missing=export(s,['mx_a_parent10b'],'MX_SUBFORM10_MISSING',validate_only=['ENTITY:mx_z_child10b'])
def missingdep():
 before=fingerprint('target2');up=upload(u,missing);i=up['data']['id'];an=analyze(u,i);p=publish(u,i)
 return an['blocked'] and p['code']!=200 and before==fingerprint('target2'),{'analysis':an,'publish':p,'unchangedConfig':before==fingerprint('target2')}
case('F01','子表单依赖仅校验而目标不存在','分析阻断且发布不写入配置',missingdep)
raw=export(s,['mx_a_parent10b'],'MX_SUBFORM10_AUTO')
def autodep():
 es=entries(raw);m=json.loads(es['manifest.json']);keys=[a['businessKey'] for a in m['assets']];r=migrate(u,raw);props=sql('matrix_target2',"SELECT n.props_document FROM entity_form_node n JOIN entity_form f ON f.id=n.form_id JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='mx_a_parent10b'");targetchild=sql('matrix_target2',"SELECT f.id FROM entity_form f JOIN entity_definition e ON e.id=f.entity_id WHERE e.entity_code='mx_z_child10b' AND f.form_key='main'")
 return 'mx_z_child10b' in keys and r['publish']['code']==200 and targetchild in props and cf['id'] not in props,{'includedAssetKeys':keys,'analysis':r['analysis'],'publish':r['publish'],'targetChildFormId':targetchild,'targetProps':props,'sourceChildIdAbsent':cf['id'] not in props}
case('F02','自动携带子表单并按依赖顺序发布','父子表单成功，引用改为目标ID',autodep)
