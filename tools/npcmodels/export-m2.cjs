#!/usr/bin/env node
/* Offline adapter for wow.export c2fd7bde36a712be78a5da896c995b84fbfa2545.
 * Tool code MIT; see the pinned upstream LICENSE. No CASC/network fallback.
 */
'use strict';
const fs = require('fs'), path = require('path'), util = require('util'), Module = require('module'), crypto = require('crypto');
if (![5,6].includes(process.argv.length)) throw Error('Usage: node export-m2.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR');
const [source, input, output] = process.argv.slice(2,5).map(x => path.resolve(x));
if (!source || !input || !output) throw Error('Usage: node export-m2.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR');
fs.mkdirSync(output, {recursive:true});
const request = process.argv[5] ? JSON.parse(fs.readFileSync(process.argv[5], 'utf8')) : null;
const modelID = request ? request.modelFileDataId : 126061;
const textures = request ? request.textures : [126062,0,0];
const stem = request ? 'preview' : 'mechanical-squirrel';
if (!Number.isSafeInteger(modelID) || modelID <= 0 || !Array.isArray(textures) || textures.some(x=>!Number.isSafeInteger(x)||x<0)) throw Error('Invalid appearance request');
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
const missing = new Set();
stub('core.js',{view:{config,casc:{getFile:async id=>{
 const f=path.join(input,`${id}.bin`);
 if(!fs.existsSync(f)) { missing.add(id); throw Error(`Missing exact native dependency ${id}`); }
 return new BufferWrapper(fs.readFileSync(f));
}}}});
BufferWrapper=require(path.join(js,'buffer'));
const Exporter=require(path.join(js,'3D/exporters/M2Exporter'));
(async()=>{
 const exporter=new Exporter(new BufferWrapper(fs.readFileSync(path.join(input,`${modelID}.bin`))),textures,modelID);
 await exporter.exportAsGLTF(path.join(output,stem+'.m2'),{isCancelled:()=>false,mark:()=>{}},'glb');
 const glb=fs.readFileSync(path.join(output,stem+'.glb'));
 const document=JSON.parse(glb.subarray(20,20+glb.readUInt32LE(12)).toString());
 if(missing.size) throw Error('Missing exact native dependencies: '+[...missing].join(','));
 if(!document.images?.length || !document.meshes?.every(mesh=>mesh.primitives.every(p=>p.material!==undefined && document.materials[p.material]?.pbrMetallicRoughness?.baseColorTexture))) throw Error('Missing embedded material/texture');
 const m=exporter.m2, skin=await m.getSkin(0);
 const inputHashes=fs.readdirSync(input).filter(x=>/^\d+\.bin$/.test(x)).map(x=>Number(x.slice(0,-4))).sort((a,b)=>a-b).map(id=>({fileDataId:id,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(input,`${id}.bin`))).digest('hex')}));
 if(m.materials.some(m=>![0,1].includes(m.blendingMode))) throw Error('Unsupported appearance: translucent or additive materials need separate validation');
 if(request){
  const {nativeMaterials,writeGlb}=require('./materials.cjs');
  const sections=skin.subMeshes.map((_,i)=>skin.textureUnits.filter(t=>t.skinSectionIndex===i));
  nativeMaterials(document,m.materials,sections);
  fs.writeFileSync(path.join(output,stem+'.glb'),writeGlb(glb,document));
 }
 if(m.textureTypes.some(t=>![0,2,11,12,13].includes(t))) throw Error('Unsupported appearance: composite or special texture type');
 if(!request && skin.subMeshes.length!==1) throw Error('Unsupported control submeshes');
 fs.writeFileSync(path.join(output,'conversion.json'),JSON.stringify({exporterCommit:'c2fd7bde36a712be78a5da896c995b84fbfa2545',model:modelID,textures,inputHashes,vertices:m.vertices.length/3,triangles:skin.triangles.length/3,submeshes:skin.subMeshes.length,textureTypes:m.textureTypes,materials:m.materials,pose:'bind pose; animation intentionally disabled',missingDependencies:[]},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
