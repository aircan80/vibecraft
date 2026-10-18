#!/usr/bin/env node
/* LAB ONLY: incomplete body reconstruction, never a production portrait.
 * Offline adapter for wow.export c2fd7bde36a712be78a5da896c995b84fbfa2545.
 * Tool code MIT; see the pinned upstream LICENSE. No CASC/network fallback.
 */
'use strict';
const fs = require('fs'), path = require('path'), util = require('util'), Module = require('module'), crypto = require('crypto');
if (process.argv.length !== 5) throw Error('Usage: node portrait-prototype.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR');
const [source, input, output] = process.argv.slice(2).map(x => path.resolve(x));
if (!source || !input || !output) throw Error('Usage: node portrait-prototype.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR');
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
 const model=900914;
 const exporter=new Exporter(new BufferWrapper(fs.readFileSync(path.join(input,`${model}.bin`))),[0,0,0],model);
 await exporter.m2.load();
 const skin=await exporter.m2.getSkin(0);
 fs.writeFileSync(path.join(output,'model-inspection.json'),JSON.stringify({model,skeletonFileID:exporter.m2.skeletonFileID,attachments:exporter.m2.attachments,attachmentLookup:exporter.m2.attachmentLookup,bones:exporter.m2.bones,materials:exporter.m2.materials,textureTypes:exporter.m2.textureTypes,textures:exporter.m2.textures,textureCombos:exporter.m2.textureCombos,textureUnits:skin.textureUnits,subMeshes:skin.subMeshes},null,2));
 if(process.env.INSPECT_ONLY) return;
 const recipe=JSON.parse(fs.readFileSync(process.env.PROTOTYPE_RECIPE));
 if(recipe.product!=='wow_classic_beta'||recipe.build!=='1.60.1.70450'||recipe.extraId!==10766||recipe.allowIncompletePrototype!==true)throw Error('Wrong prototype recipe identity');
 const mask=recipe.customizationGeosetMask;
 if(!Array.isArray(mask)||mask.length!==skin.subMeshes.length)throw Error('Exact model mask required');
 const geosets=new Set(mask.filter(m=>m.checked).map(m=>m.geosetID??m.id));
 const hasHair=fs.existsSync(path.join(input,'917856.bin'));
 const hasEye=fs.existsSync(path.join(input,'3484614.bin'));
 if(hasEye){const eyeUnit=skin.textureUnits.find(u=>skin.subMeshes[u.skinSectionIndex].submeshID===3301);const mapper=require(path.join(js,'3D/ShaderMapper')); if(!eyeUnit||mapper.getPixelShader(eyeUnit.textureCount,eyeUnit.shaderID)!=='Combiners_Mod_Add'||mapper.getVertexShader(eyeUnit.textureCount,eyeUnit.shaderID)!=='Diffuse_T1_T2')throw Error('Unsupported actual native eye shader');}
 const supportedTypes=new Set([1,...(hasHair?[6]:[]),...(hasEye?[19]:[])]);
 const omitted=[];
 exporter.setGeosetMask(skin.subMeshes.map((s,i)=>{const unit=skin.textureUnits.find(t=>t.skinSectionIndex===i); const type=unit ? exporter.m2.textureTypes[exporter.m2.textureCombos[unit.textureComboIndex]] : null; const selected=geosets.has(s.submeshID); if(selected&&!supportedTypes.has(type))omitted.push({geoset:s.submeshID,textureType:type}); return {checked:selected&&supportedTypes.has(type)};}));
 const BLP=require(path.join(js,'casc/blp'));
 const png=new BLP(new BufferWrapper(fs.readFileSync(path.join(input,'928687.bin')))).toPNG();
 await exporter.addURITexture(1,'data:image/png;base64,'+png.raw.toString('base64'));
 if(hasHair){const hair=new BLP(new BufferWrapper(fs.readFileSync(path.join(input,'917856.bin')))).toPNG(); await exporter.addURITexture(6,'data:image/png;base64,'+hair.raw.toString('base64'));}
 if(hasEye){const eye=new BLP(new BufferWrapper(fs.readFileSync(path.join(input,'3484614.bin')))).toPNG(); await exporter.addURITexture(19,'data:image/png;base64,'+eye.raw.toString('base64'));}
 await exporter.exportAsGLTF(path.join(output,'partial-humanoid.m2'),{isCancelled:()=>false,mark:()=>{}},'glb');
 fs.writeFileSync(path.join(output,'prototype-limits.json'),JSON.stringify({status:'LAB_PARTIAL_BODY_ONLY_NOT_AUTHORIZED',npc:15698,display:15660,extra:10766,model,texture:928687,hair:hasHair?917856:null,eye:hasEye?3484614:null,inputHashes:[900914,900966,928687,...(hasHair?[917856]:[]),...(hasEye?[3484614]:[])].map(id=>({fileDataId:id,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(input,`${id}.bin`))).digest('hex')})),recipeSha256:crypto.createHash('sha256').update(fs.readFileSync(process.env.PROTOTYPE_RECIPE)).digest('hex'),recipe,omittedMaterialUnresolvedMeshes:omitted,missing:['equipment attachments','equipment visibility overrides','customization bone and material conditions beyond baked atlas'],unusedFixedTexturesNotAcquired:[3550825,4531003,5210065],pose:'bind pose'},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
