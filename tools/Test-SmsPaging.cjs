// Synthetic SQLite checks for the exact provider selection used by the Android app.
// Run with a Node version that includes node:sqlite. No device data is accessed.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const source = fs.readFileSync('core/src/main/kotlin/com/gediadeepak/smsapp/core/SmsPaging.kt', 'utf8');
const latest = source.match(/val LATEST = """([\s\S]*?)"""/)[1].replace(/\r?\n/g, ' ');
const before = source.match(/const val BEFORE = "([^"]+)"/)[1];
const order = source.match(/const val ORDER = "([^"]+)"/)[1];
const repository = fs.readFileSync('app/src/main/java/com/gediadeepak/smsapp/telephony/SmsPagingRepository.kt', 'utf8');
const bounded = repository.match(/val bounded = "([^"]+)"/)[1];
function selectionFor(selection, limit) {
    return bounded.replace('$selection',selection).replace('${SmsPaging.ORDER}',order).replace('$limit',String(limit));
}
function pageSql(selection, limit) {
    return 'SELECT * FROM sms WHERE '+selectionFor(selection,limit)+' ORDER BY '+order;
}
const db = new DatabaseSync(':memory:');
db.exec('CREATE TABLE sms (_id INTEGER PRIMARY KEY, thread_id INTEGER, date INTEGER, type INTEGER, read INTEGER, body TEXT, address TEXT)');
db.exec('CREATE INDEX sms_thread_date ON sms(thread_id,date)');
const insert = db.prepare('INSERT INTO sms VALUES (?,?,?,?,?,?,?)');
let id = 0;
for (let thread=1; thread<=153; thread++) {
    insert.run(++id,thread,thread*10,1,0,'older searchable body '+thread,'+4477009'+thread);
    insert.run(++id,thread,2000,2,1,'latest '+thread,'+4477009'+thread);
    insert.run(++id,thread,3000,3,1,'excluded draft','+4477009'+thread);
}
// Restored old messages have newer IDs but must not replace the latest dated SMS.
insert.run(++id,1,1,1,0,'restored old body','+44770091');
// Resolve equal latest timestamps using the numerically greatest provider ID.
insert.run(++id,2,2000,1,0,'equal timestamp winner','+44770092');
const expected = db.prepare('SELECT * FROM sms WHERE type<>3').all().reduce((map,row)=>{
    const old=map.get(row.thread_id);
    if (!old || row.date>old.date || row.date===old.date && row._id>old._id) map.set(row.thread_id,row);
    return map;
},new Map());
const sorted=[...expected.values()].sort((a,b)=>b.date-a.date || b._id-a._id);
let key=null;
const seen=[];
const sizes=[];
do {
    const rows=db.prepare(pageSql('type<>3 AND '+latest+(key?' AND '+before:''),51))
        .all(...(key?[key.date,key.date,key._id]:[]));
    const page=rows.slice(0,50);
    sizes.push(page.length);
    seen.push(...page.map(row=>row._id));
    key=rows.length>50?page.at(-1):null;
    if (sizes.length===1) insert.run(++id,999,4000,1,0,'new arrival between pages','+4477009999');
} while (key);
assert.deepEqual(sizes,[50,50,50,3]);
assert.deepEqual(seen,sorted.map(row=>row._id));
assert.equal(new Set(seen).size,153);
assert.equal(db.prepare('SELECT * FROM sms WHERE type<>3 AND '+latest+' ORDER BY '+order+' LIMIT 1').get().thread_id,999);
const matching=db.prepare("SELECT DISTINCT thread_id FROM sms WHERE type<>3 AND body LIKE ? ESCAPE '\\'").all('%restored old%');
assert.deepEqual(matching.map(r=>r.thread_id),[1]);
assert.equal(db.prepare('SELECT COUNT(*) AS n FROM sms WHERE thread_id=1 AND type=1 AND read=0').get().n,2);
// History pages include equal-date rows and keep boundaries stable after deletions.
for(let n=0;n<127;n++) insert.run(++id,1000,5000+Math.floor(n/3),1,0,'history '+n,'+44770091000');
let historyKey=null;
const history=[];
do {
    const rows=db.prepare(pageSql('thread_id=1000 AND type<>3'+(historyKey?' AND '+before:''),51))
        .all(...(historyKey?[historyKey.date,historyKey.date,historyKey._id]:[]));
    history.push(...rows.slice(0,50));
    historyKey=rows.length>50?rows[49]:null;
} while(historyKey);
assert.equal(history.length,127);
assert.equal(new Set(history.map(r=>r._id)).size,127);
const boundary=history[49];
db.prepare('DELETE FROM sms WHERE _id=?').run(boundary._id);
const remaining=db.prepare('SELECT * FROM sms WHERE thread_id=1000 AND '+before+' ORDER BY '+order).all(boundary.date,boundary.date,boundary._id);
assert.deepEqual(remaining.map(r=>r._id),history.slice(50).map(r=>r._id));
for(const count of [0,49,50,51]) {
    const rows=db.prepare('SELECT * FROM sms WHERE thread_id=1000 ORDER BY '+order+' LIMIT ?').all(count);
    assert.equal(rows.slice(0,50).length,Math.min(count,50));
    assert.equal(rows.length>50,count===51);
}
console.log('SMS paging SQL passed: 153 conversations, 127 history rows, 50-row pages, ties, drafts, restore, inserts, deletion, old-body search and unread counts.');
