#!/usr/bin/env node
/* Offline adapter for wow.export c2fd7bde36a712be78a5da896c995b84fbfa2545.
 * Tool code MIT; see the pinned upstream LICENSE. No CASC/network fallback.
 */
'use strict';
const fs = require('fs'), path = require('path'), util = require('util'), Module = require('module'), crypto = require('crypto');
if (process.argv.length !== 5) throw Error('Usage: node export-m2.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR');
const [source, input, output] = process.argv.slice(2).map(x => path.resolve(x));
if (!source || !input || !output) throw Error('Usage: node export-m2.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR');
fs.mkdirSync(output, {recursive:true});
const js = path.join(source, 'src/js');
const config = {overwriteFiles:true, modelsExportTextures:true, modelsExportAlpha:true,
 pathFormat:'posix', enableSharedTextures:false, modelsExportAnimations:false,
 modelsExportWithBonePrefix:false, enableAbsoluteGLTFPaths:false};
const stub = (name, exports) => { const filename = require.resolve(path.join(js,name)); require.cache[filename] = {id:filename, filename, loaded:true, exports}; };
global.nw={App:{dataPath:output,manifest:{version:'0.2.19'}}};
stub('log.js', {write:(...args)=>console.log(util.format(...args))});
stub('casc/listfile.js', {getByID:()=>undefined, getByIDOrUnknown:(id,ext)=>`${id}${ext}`,formatUnknownFile:x=>x});
stub('casc/export-helper.js', {replaceExtension:(p,e)=>p.replace(/\.[^.\/]+$/, '')+e,win32ToPosix:p=>p.replace(/\\/g,'/')});
// PNG encoding is native wow.export; its optional WebP codec is never invoked.
const originalLoad = Module._load;
Module._load = function(name,...args) {if(name==='webp-wasm')return {};return originalLoad.call(this,name,...args);};
let BufferWrapper;
stub('core.js',{view:{config,casc:{getFile:async id=>{
 const f=path.join(input,`${id}.bin`);
 if(!fs.existsSync(f)) throw Error(`Missing exact native dependency ${id}`);
 return new BufferWrapper(fs.readFileSync(f));
}}}});
BufferWrapper=require(path.join(js,'buffer'));
const Exporter=require(path.join(js,'3D/exporters/M2Exporter'));
(async()=>{
 const exporter=new Exporter(new BufferWrapper(fs.readFileSync(path.join(input,'126061.bin'))),[126062,0,0],126061);
 await exporter.exportAsGLTF(path.join(output,'mechanical-squirrel.m2'),{isCancelled:()=>false,mark:()=>{}},'glb');
 const glb=fs.readFileSync(path.join(output,'mechanical-squirrel.glb'));
 const document=JSON.parse(glb.subarray(20,20+glb.readUInt32LE(12)).toString());
 if(document.images?.length!==1 || !document.meshes?.[0]?.primitives?.every(p=>p.material!==undefined)) throw Error('Missing embedded material/texture');
 const m=exporter.m2, skin=await m.getSkin(0);
 const inputHashes=[126061,126062,479933].map(id=>({fileDataId:id,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(input,`${id}.bin`))).digest('hex')}));
 if(m.materials.some(m=>m.blendingMode!==0) || skin.subMeshes.length!==1) throw Error('Unsupported appearance outside this opaque single-submesh control');
 fs.writeFileSync(path.join(output,'conversion.json'),JSON.stringify({exporterCommit:'c2fd7bde36a712be78a5da896c995b84fbfa2545',model:126061,texture:126062,skin:479933,inputHashes,vertices:m.vertices.length/3,triangles:skin.triangles.length/3,submeshes:skin.subMeshes.length,textureTypes:m.textureTypes,materials:m.materials,pose:'bind pose; animation intentionally disabled',missingDependencies:[]},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
