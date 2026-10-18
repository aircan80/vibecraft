#!/usr/bin/env node
// Offline WDC adapter. The caller must supply a root-verified DB2 and pinned DBD.
'use strict';
const fs=require('fs'),path=require('path');
if(process.argv.length!==7)throw Error('Usage: node decode-native-table.cjs WOW_EXPORT TABLE DB2 DBD OUTPUT');
const [upstream,table,binary,definition,output]=process.argv.slice(2);
if(!/^[A-Za-z][A-Za-z0-9]+$/.test(table))throw Error('Invalid table');
const commit=require('child_process').execFileSync('git',['-C',upstream,'rev-parse','HEAD'],{encoding:'utf8'}).trim();
if(commit!=='c2fd7bde36a712be78a5da896c995b84fbfa2545')throw Error('Unreviewed wow.export version');
const crypto=require('crypto');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const js=path.resolve(upstream,'src/js');
const stub=(name,exports)=>{const file=require.resolve(path.join(js,name));require.cache[file]={id:file,filename:file,loaded:true,exports};};
stub('log.js',{write:()=>{}});
stub('casc/export-helper.js',{replaceExtension:(p,e='')=>p.replace(/\.[^.\/]+$/,'')+e});
const Module=require('module'), originalLoad=Module._load;
Module._load=function(name,...args){if(name==='webp-wasm')return {};return originalLoad.call(this,name,...args);};
global.nw={App:{dataPath:path.dirname(path.resolve(output)),manifest:{version:'0.2.19'}}};
const BufferWrapper=require(path.join(js,'buffer'));
stub('core.js',{view:{config:{},casc:{getBuildName:()=> '1.60.1.70450',
 getVirtualFileByName:async()=>new BufferWrapper(fs.readFileSync(binary)),
 cache:{getFile:async()=>new BufferWrapper(fs.readFileSync(definition))}}}});
const Reader=require(path.join(js,'db/WDCReader'));
(async()=>{const reader=new Reader('DBFilesClient/'+table+'.db2');await reader.parse();reader.preload();
const rows=[...reader.getAllRows()].map(([id,row])=>({ID:id,...row}));
fs.writeFileSync(output,JSON.stringify({product:'wow_classic_beta',build:'1.60.1.70450',table,sourceSha256:hash(binary),definitionSha256:hash(definition),decoderCommit:commit,rows},null,2)+'\n');console.log(JSON.stringify({table,rows:rows.length}));
})().catch(e=>{console.error(e);process.exitCode=1;});
