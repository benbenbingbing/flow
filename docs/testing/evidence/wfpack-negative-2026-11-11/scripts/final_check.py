from harness import *
import subprocess
out={'environments':{},'sourceFiles':{},'gitHead':subprocess.check_output(['git','-c','core.fsmonitor=false','rev-parse','HEAD'],cwd='/Users/dawei/Documents/ddup/ai/flow',text=True).strip()}
for name in ['source','target','target2']:
 db='matrix_'+name
 out['environments'][name]={'flyway':sql(db,"SELECT COUNT(*),MAX(installed_rank),MAX(version),SUM(success=0) FROM flyway_schema_history"),'importStatuses':sql(db,'SELECT status,COUNT(*) FROM config_import_package GROUP BY status ORDER BY status'),'pendingPublishingItems':int(sql(db,"SELECT COUNT(*) FROM config_import_item WHERE publish_status='PUBLISHING'")),'processCount':int(sql(db,'SELECT COUNT(*) FROM process_definition_config')),'entityCount':int(sql(db,'SELECT COUNT(*) FROM entity_definition'))}
root=pathlib.Path('/Users/dawei/Documents/ddup/ai/flow')
paths=['ConfigMigrationAssetService.java','ConfigMigrationPackageService.java','ConfigMigrationImportApplyService.java','ConfigMigrationSubFormReferences.java','ConfigMigrationRelationImporter.java']
for name in paths:
 p=root/'workflow-server/workflow-migration/src/main/java/com/workflow/migration/application'/name;out['sourceFiles'][str(p.relative_to(root))]=hashlib.sha256(p.read_bytes()).hexdigest()
p=BASE/'checkout'/'workflow-server/workflow-app/target/workflow-server-1.0.0.jar';out['testedJarSha256']=hashlib.sha256(p.read_bytes()).hexdigest()
out['migrationDiff']=subprocess.check_output(['git','-c','core.fsmonitor=false','diff','--name-status','HEAD','--','workflow-server/**/db/migration/V*'],cwd=root,text=True).strip()
(BASE/'final-verification.json').write_text(json.dumps(out,ensure_ascii=False,indent=2));print(json.dumps(out['environments'],ensure_ascii=False,indent=2))
