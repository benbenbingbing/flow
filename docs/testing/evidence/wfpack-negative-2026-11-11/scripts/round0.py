from harness import *
s,t,t2=apis()
f=make_flow(s,'mx_baseline','mx_entity');state['fixtures']['baseline']=f;save()
raw=export(s,['mx_baseline','mx_entity'],'MX_BASELINE')
for a in [t,t2]:
 r=migrate(a,raw);state['fixtures'][a.name+'Baseline']=r;save()
 case('B-'+a.name,'全新环境完整包发布','发布成功且生成目标本地 ID',lambda a=a,r=r:(r['publish']['code']==200 and r['publish']['data']['status']=='PUBLISHED' and a.call('/process/key/mx_baseline')['id']!=f['process']['id'],r))
print('fingerprint',fingerprint('target'))
