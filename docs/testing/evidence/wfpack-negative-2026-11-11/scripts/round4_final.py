from harness import *
s,t,t2=apis();key='mx_mapping_final';user='mx_missing_user_final';s.call('/system/user',{'username':user,'realName':'映射源用户','password':json.loads((BASE/'credentials.json').read_text())['password'],'status':'0'});make_flow(s,key,assignee=user);raw=export(s,[key],'MX_MAPPING');up=upload(t,raw);i=up['data']['id'];state['fixtures']['mappingImport']=i;save()
def blocked_case(id,name,fn=None):
 def run():
  if fn:fn()
  a=analyze(t,i);before=fingerprint('target');p=publish(t,i)
  return a['blocked'] and p['code']!=200 and before==fingerprint('target'),{'analysis':a,'publish':p,'unchangedConfig':before==fingerprint('target')}
 case(id,name,'分析阻断且服务端拒绝发布，配置无变化',run)
def mapping(target,enabled=True,typ='USER',source=user):return t.call('/config-migration/imports/'+i+'/mappings',{'mappings':[{'sourceType':typ,'sourceKey':source,'targetKey':target,'enabled':enabled}]},ok=False)
blocked_case('M01','源办理人在目标不存在')
blocked_case('M02','映射到不存在目标用户',lambda:mapping('mx_does_not_exist'))
blocked_case('M03','有效目标但禁用映射',lambda:mapping('admin',False))
blocked_case('M04','映射类型错误不能替代USER依赖',lambda:mapping('admin',True,'ROLE'))
def blank():
 before=sql('matrix_target','SELECT source_type,source_key,target_key,enabled FROM config_environment_mapping ORDER BY id');r=mapping('');after=sql('matrix_target','SELECT source_type,source_key,target_key,enabled FROM config_environment_mapping ORDER BY id')
 return r['code']!=200 and before==after,{'response':r,'mappingUnchanged':before==after}
case('M05','空目标映射','拒绝且不修改映射',blank)
def valid():
 r=mapping('admin');a=analyze(t,i);p=publish(t,i);cur=t.call('/process/key/'+key)
 return not a['blocked'] and p['code']==200 and 'mx_missing_user' not in cur['bpmnXml'] and 'admin' in cur['bpmnXml'],{'mapping':r,'analysis':a,'publish':p,'targetBpmn':cur['bpmnXml']}
case('M06','修正映射后同一批次重试发布','成功且办理人转换为目标admin',valid)
def terminalmapping():
 before=sql('matrix_target','SELECT source_type,source_key,target_key,enabled FROM config_environment_mapping ORDER BY id');r=mapping('mx_does_not_exist');after=sql('matrix_target','SELECT source_type,source_key,target_key,enabled FROM config_environment_mapping ORDER BY id');st=sql('matrix_target',"SELECT status FROM config_import_package WHERE id='"+i+"'")
 return r['code']!=200 and before==after and st=='PUBLISHED',{'response':r,'mappingUnchanged':before==after,'status':st}
case('M07','发布后修改映射不能重写批次历史','拒绝且保持PUBLISHED及原映射',terminalmapping)
