const fs=require('node:fs');
const assert=require('node:assert/strict');
const {DatabaseSync}=require('node:sqlite');
const source=fs.readFileSync('app/src/main/java/com/gediadeepak/smsapp/data/SandeshDatabase.kt','utf8');
const queueSource=fs.readFileSync('app/src/main/java/com/gediadeepak/smsapp/scheduling/SendQueue.kt','utf8');
const metadataDDL=source.match(/db\.execSQL\("(CREATE TABLE thread_metadata[^"\n]+)"\)/)[1];
const queueDDL=queueSource.match(/db\.execSQL\("(CREATE TABLE queue[^"\n]+)"\)/)[1];
const projection=source.match(/query\("thread_metadata", arrayOf\(([^)]+)\)/)[1].match(/"([^"]+)"/g).map(x=>x.slice(1,-1));
let assertions=0;
for(const version of [1,2,3,4]) {
 const db=new DatabaseSync(':memory:');
 if(version===4) db.exec(metadataDDL);
 else {
  db.exec("CREATE TABLE thread_metadata(thread_id TEXT PRIMARY KEY,starred INTEGER NOT NULL DEFAULT 0,archived INTEGER NOT NULL DEFAULT 0,alias TEXT NOT NULL DEFAULT ''"+(version===3?",spam INTEGER NOT NULL DEFAULT 0":"")+")");
  for(const block of source.matchAll(/if \(oldVersion < (\d+)\) \{([^}]+)\}/g)) if(version<Number(block[1])) {
   for(const sql of block[2].matchAll(/db\.execSQL\("([^"]+)"\)/g)) if(sql[1].startsWith('ALTER TABLE thread_metadata')) db.exec(sql[1]);
  }
 }
 db.prepare('INSERT INTO thread_metadata(thread_id,starred,alias) VALUES (?,?,?)').run('42',1,'fixture');
 db.prepare('UPDATE thread_metadata SET pinned=1,vendor=? WHERE thread_id=?').run('Fixture business','42');
 const row=db.prepare('SELECT '+projection.join(',')+' FROM thread_metadata WHERE thread_id=?').get('42');
 assert.deepEqual(Object.values(row),['42',1,0,'fixture',0,1,'Fixture business']);assertions++;
 db.close();
}
const db=new DatabaseSync(':memory:');db.exec(queueDDL);
const claimWhere=queueSource.match(/fun claim[^\n]*"(id=\?[^"\n]+)"/)[1];
const cancelWhere=queueSource.match(/fun cancel\(id[^\n]*"(id=\?[^"\n]+)"/)[1];
const add=db.prepare("INSERT INTO queue(id,number,body,sim,due,state,detail,thread) VALUES (?,?,?,?,?,'WAITING','','42')");
const claim=db.prepare('UPDATE queue SET state=\'CLAIMED\' WHERE '+claimWhere);
const cancel=db.prepare('UPDATE queue SET state=\'CANCELLED\' WHERE '+cancelWhere);
add.run('cancel-first','+447700900000','fixture',1,1000);
assert.equal(cancel.run('cancel-first').changes,1);assert.equal(claim.run('cancel-first').changes,0);assertions++;
add.run('claim-first','+447700900000','fixture',2,1000);
assert.equal(claim.run('claim-first').changes,1);assert.equal(cancel.run('claim-first').changes,0);assert.equal(claim.run('claim-first').changes,0);assertions++;
assert.equal(db.prepare('SELECT sim,thread FROM queue WHERE id=?').get('claim-first').sim,2);assertions++;
assert.equal(db.prepare('SELECT thread FROM queue WHERE id=?').get('claim-first').thread,'42');assertions++;
assert.match(queueSource,/if\(item\.state=="CLAIMED"\)[^\n]*"REVIEW"/);assertions++;
const upgrade=queueSource.match(/db\.execSQL\("(ALTER TABLE queue[^"\n]+)"\)/)[1];
const old=new DatabaseSync(':memory:');old.exec("CREATE TABLE queue(id TEXT PRIMARY KEY,number TEXT NOT NULL,body TEXT NOT NULL,sim INTEGER NOT NULL,due INTEGER NOT NULL,state TEXT NOT NULL,detail TEXT NOT NULL)");
old.exec(upgrade);assert.equal(old.prepare('PRAGMA table_info(queue)').all().at(-1).name,'thread');assertions++;old.close();db.close();
console.log('PASS: '+assertions+' source-SQL checks: migrations 1–4, queue migration, cancellation orderings, durable handoff guard and saved SIM/thread identity. No carrier calls.');
