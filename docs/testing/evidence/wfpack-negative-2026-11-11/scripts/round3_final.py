from harness import *
s,t,t2=apis();base=entries((BASE/'MX_BASELINE.wfpack').read_bytes());m0=json.loads(base['manifest.json'])
def run(id,name,mutate,esmutate=None):
 e=dict(base);m=copy.deepcopy(m0);m['packageNo']='QA_SEMANTIC_FINAL_'+id;mutate(m)
 if esmutate:esmutate(e,m)
 e['manifest.json']=json.dumps(m,ensure_ascii=False).encode();data=zipped(refresh(e));(BASE/(id+'.wfpack')).write_bytes(data)
 def test():
  before=fingerprint('target2');up=upload(t2,data);a=p=None
  if up['code']==200:
   i=up['data']['id'];a=t2.call('/config-migration/imports/'+i+'/analyze',{},ok=False)
   # 即使分析已阻断，也直接请求发布，确认服务端拦截不依赖界面按钮。
   p=publish(t2,i)
  rejected=up['code']!=200 or (p is not None and p['code']!=200)
  same=before==fingerprint('target2')
  return rejected and same,{'upload':up,'analysis':a,'publish':p,'unchangedConfig':same}
 case(id,name,'在导入/分析/发布阶段拒绝且不写入配置',test)
run('S01','空资产清单',lambda m:m.update(assets=[]))
run('S02','缺失资产清单',lambda m:m.pop('assets'))
run('S03','资产清单类型错误',lambda m:m.update(assets={}))
for id,k,v in [('S04','assetType','UNKNOWN'),('S05','assetType',None),('S06','businessKey',''),('S07','businessKey','mx_wrong_identity'),('S08','sourceVersion',0),('S09','sourceVersion',-1),('S10','sourceVersion','invalid'),('S11','sourceHash','0'*64),('S12','path','assets/missing.json'),('S13','snapshotSchemaVersion',999)]:
 run(id,'资产字段 '+k+'='+str(v),lambda m,k=k,v=v:m['assets'][0].update({k:v}))
run('S14','重复同一资产',lambda m:m['assets'].append(copy.deepcopy(m['assets'][0])))
run('S15','缺失包编号',lambda m:m.pop('packageNo'))
run('S16','空包编号',lambda m:m.update(packageNo=''))
run('S17','选择不存在表单',lambda m:m['assets'][0].update(selection={'full':False,'sections':['forms'],'formKeys':['not_exists']}))
run('S18','选择未知分区',lambda m:m['assets'][0].update(selection={'full':False,'sections':['UNKNOWN']}))
run('S19','资产快照JSON损坏',lambda m:None,lambda e,m:e.update({m['assets'][0]['path']:b'{broken'}))
run('S20','资产快照JSON为数组',lambda m:None,lambda e,m:e.update({m['assets'][0]['path']:b'[]'}))
run('S21','资产路径为空',lambda m:m['assets'][0].update(path=''))
run('S22','资产路径指向manifest本身',lambda m:m['assets'][0].update(path='manifest.json'))
run('S23','校验合法但依赖清单JSON损坏',lambda m:None,lambda e,m:e.update({'dependencies.json':b'{broken'}))
