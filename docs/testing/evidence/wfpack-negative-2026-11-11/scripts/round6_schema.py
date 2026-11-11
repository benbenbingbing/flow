from harness import *
s,t,u=apis();base=entries((BASE/'MX_BASELINE.wfpack').read_bytes());original=json.loads(base['manifest.json']);asset=next(a for a in original['assets'] if a['assetType']=='ENTITY');snap=json.loads(base[asset['path']]);pure=dict(snap);pure.pop('_selection',None);assert hashlib.sha256(json.dumps(pure,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()==asset['sourceHash'],'harness hash must match codec'
def variant(id,name,mutator,code):
 e=dict(base);m=copy.deepcopy(original);m['packageNo']='QA_SCHEMA_'+id;m['assets']=[copy.deepcopy(asset)];a=m['assets'][0];a['sourceVersion']=50;ss=copy.deepcopy(snap);mutator(ss);p=dict(ss);p.pop('_selection',None);a['sourceHash']=hashlib.sha256(json.dumps(p,ensure_ascii=False,separators=(',',':')).encode()).hexdigest();e[a['path']]=json.dumps(ss,ensure_ascii=False,separators=(',',':')).encode();e['manifest.json']=json.dumps(m,ensure_ascii=False).encode();data=zipped(refresh(e));(BASE/(id+'.wfpack')).write_bytes(data)
 def run():
  before=fingerprint('target');ddl=sql('matrix_target','SHOW CREATE TABLE biz_mx_entity');up=upload(t,data);assert up['code']==200,up;i=up['data']['id'];an=analyze(t,i);pub=publish(t,i);same=before==fingerprint('target') and ddl==sql('matrix_target','SHOW CREATE TABLE biz_mx_entity')
  return an['blocked'] and code in json.dumps(an) and pub['code']!=200 and same,{'analysis':an,'publish':pub,'unchangedConfigAndDDL':same}
 case(id,name,'分析发现 '+code+'，直接发布被拒绝，无DDL或配置变化',run)
def change(s,**values):next(f for f in s['fields'] if f['fieldCode']=='note').update(values)
variant('D01','删除已有业务字段',lambda s:s.update(fields=[f for f in s['fields'] if f['fieldCode']!='note']),'FIELD_REMOVED')
variant('D02','修改已有字段类型',lambda s:change(s,fieldType='INTEGER'),'FIELD_TYPE_CHANGED')
variant('D03','收窄已有字段长度',lambda s:change(s,fieldLength=50),'FIELD_LENGTH_NARROWED')
variant('D04','已有字段改为必填',lambda s:change(s,isRequired=True),'FIELD_REQUIRED')
variant('D05','已有字段新增唯一约束',lambda s:change(s,isUnique=True),'FIELD_UNIQUE')
variant('D06','新增无默认值的必填字段',lambda s:s['fields'].append({'fieldCode':'new_required','fieldName':'新增必填','fieldType':'STRING','fieldLength':100,'isRequired':True}),'NEW_REQUIRED_WITHOUT_DEFAULT')
variant('D07','编码规则引用未安装生成器',lambda s:s.update(codeRule={'generationMode':'CUSTOM','generatorCode':'QA_NOT_INSTALLED'}),'CODE_GENERATOR_UNAVAILABLE')
