from pathlib import Path
import os,json,secrets,subprocess
BASE=Path('/tmp/flow-wfpack-matrix-20261111');ROOT=BASE/'checkout';MYSQL='/usr/local/mysql/bin/mysql'
def sql(db,statement):
 r=subprocess.run([MYSQL,'--no-defaults','--socket='+str(BASE/'mysql.sock'),'-uroot','--default-character-set=utf8mb4','-N','-B',db,'-e',statement],capture_output=True,text=True)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout.strip()
def start():
 creds=json.loads((BASE/'credentials.json').read_text()) if (BASE/'credentials.json').exists() else {'password':secrets.token_urlsafe(24)+'aA1','jwt':secrets.token_hex(48)}
 (BASE/'credentials.json').write_text(json.dumps(creds));os.chmod(BASE/'credentials.json',0o600)
 sql('',"CREATE USER IF NOT EXISTS 'qa'@'localhost' IDENTIFIED BY '"+creds['password']+"'; GRANT ALL PRIVILEGES ON *.* TO 'qa'@'localhost'")
 for name,port in [('source',18091),('target',18092),('target2',18093)]:
  db='matrix_'+name;sql('','CREATE DATABASE IF NOT EXISTS '+db+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
  jdbc='jdbc:mysql://127.0.0.1:13312/'+db+'?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8&connectionCollation=utf8mb4_unicode_ci&nullCatalogMeansCurrent=true&allowPublicKeyRetrieval=true&useSSL=false'
  env=os.environ.copy();env.update({'SPRING_FLYWAY_ENABLED':'true','DB_USERNAME':'qa','DB_PASSWORD':creds['password'],'SCHEMA_DB_USERNAME':'qa','SCHEMA_DB_PASSWORD':creds['password'],'SPRING_DATASOURCE_URL':jdbc,'SCHEMA_DATASOURCE_URL':jdbc,'JWT_SECRET':creds['jwt'],'WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD':creds['password'],'FILE_STORAGE_TYPE':'local','FILE_STORAGE_PATH':str(BASE/name/'uploads'),'WORKFLOW_SCHEDULING_ENABLED':'true','CONFIG_MIGRATION_ENVIRONMENT_NAME':'matrix-'+name,'MANAGEMENT_SERVER_PORT':str(port+100),'DB_POOL_MAX_SIZE':'6','DB_POOL_MIN_IDLE':'1'})
  (BASE/name).mkdir(exist_ok=True)
  with (BASE/(name+'.log')).open('w') as log:
   p=subprocess.Popen(['java','-Dfile.encoding=UTF-8','-Xmx640m','-jar',str(ROOT/'workflow-server/workflow-app/target/workflow-server-1.0.0.jar'),'--server.address=127.0.0.1','--server.port='+str(port)],env=env,cwd=BASE/name,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  (BASE/(name+'.pid')).write_text(str(p.pid));print(name,p.pid,port,flush=True)
if __name__=='__main__':start()
