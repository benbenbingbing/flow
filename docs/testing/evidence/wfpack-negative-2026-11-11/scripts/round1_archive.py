from harness import *
s,t,t2=apis();raw=(BASE/'MX_BASELINE.wfpack').read_bytes();base=entries(raw)
def reject_case(id,name,data,needle=None,target=t):
 def run():
  before=fingerprint(target.name);count=import_count(target.name);r=upload(target,data,True);after=fingerprint(target.name)
  same=before==after and count==import_count(target.name)
  if not (400<=r['http']<500) or not same:(BASE/(id+'.wfpack')).write_bytes(data)
  return 400<=r['http']<500 and same and (needle is None or needle in r['message']),{'response':r,'unchangedConfig':before==after,'unchangedImportCount':count==import_count(target.name)}
 case(id,name,'4xx明确拒绝，配置及导入批次无新增',run)
reject_case('A01','空文件',b'')
reject_case('A02','随机非ZIP',b'this is not a wfpack')
reject_case('A03','ZIP中途截断',raw[:len(raw)//2])
for i,path in enumerate(['manifest.json','checksums.json','signature.sig'],4):
 e=dict(base);e.pop(path);reject_case('A%02d'%i,'缺失 '+path,zipped(e))
for i,path in enumerate(['../escape','a/../../escape','..\\escape','/absolute','C:\\escape'],7):
 e=dict(base);e[path]=b'x';reject_case('A%02d'%i,'非法路径 '+path,zipped(e))
o=io.BytesIO()
with zipfile.ZipFile(o,'w',zipfile.ZIP_DEFLATED) as z:
 for n,v in base.items():z.writestr(n,v)
 z.writestr('manifest.json',base['manifest.json'])
reject_case('A12','重复ZIP条目',o.getvalue(),'重复路径')
e=dict(base);e['signature.sig']=b'Z'*64;reject_case('A13','签名非十六进制',zipped(e),'签名格式')
e=dict(base);e['signature.sig']=b'a'*63;reject_case('A14','签名长度不合法',zipped(e),'签名格式')
e=dict(base);e['extra.bin']=b'not checksummed';reject_case('A15','未列入校验清单的附加文件',zipped(e),'校验清单不一致')
e=dict(base);checks=json.loads(e['checksums.json']);checks.pop('manifest.json');e['checksums.json']=json.dumps(checks).encode();reject_case('A16','校验清单缺项',zipped(e),'校验清单不一致')
e=dict(base);checks=json.loads(e['checksums.json']);checks['ghost.json']='0'*64;e['checksums.json']=json.dumps(checks).encode();reject_case('A17','校验清单指向不存在文件',zipped(e),'校验清单不一致')
e=dict(base);m=json.loads(e['manifest.json']);m['migrationTag']='tampered';e['manifest.json']=json.dumps(m).encode();reject_case('A18','正文篡改但未更新校验和',zipped(e),'校验失败')
e=dict(base);checks=json.loads(e['checksums.json']);checks['manifest.json']=None;e['checksums.json']=json.dumps(checks).encode();reject_case('A19','校验和为空值',zipped(e))
e=dict(base);e['checksums.json']=b'[]';reject_case('A20','校验清单JSON类型错误',zipped(e))
e=dict(base);e['checksums.json']=b'{broken';reject_case('A21','校验清单JSON损坏',zipped(e))
for id,ver in [('A22',2),('A23',999),('A24','abc')]:
 e=dict(base);m=json.loads(e['manifest.json']);m['formatVersion']=ver;e['manifest.json']=json.dumps(m).encode();reject_case(id,'不支持或错误格式版本 '+str(ver),zipped(refresh(e)))
e=dict(base)
for n in range(501):e['padding/'+str(n)]=b'x'
reject_case('A25','文件数超过500',zipped(e),'数量超过限制')
e=dict(base);e['large.bin']=b'x'*(20*1024*1024+1);reject_case('A26','单文件解压后超过20MB',zipped(e),'超过 20MB')
e=dict(base)
for n in range(6):e['expanded/'+str(n)]=b'x'*(17*1024*1024)
reject_case('A27','总解压量超过100MB',zipped(e),'解压后超过 100MB')
# 在第二目标重复关键反例，确认不是单一批次或环境的偶然状态。
reject_case('A28','第二环境重测正文篡改',zipped({**base,'manifest.json':b'{}'}),target=t2)
reject_case('A29','第二环境重测重复ZIP条目',o.getvalue(),target=t2)
reject_case('A30','第二环境重测校验和空值',zipped({**base,'checksums.json':json.dumps({**json.loads(base['checksums.json']),'manifest.json':None}).encode()}),target=t2)
