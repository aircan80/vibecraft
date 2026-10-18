'use strict';
// Native opaque/alpha-key modes only; no approximation of additive/translucent passes.
function nativeMaterials(document, materials, sections) {
 if (!Array.isArray(document.meshes) || document.meshes.length !== sections.length) throw Error('Unsupported mesh/skin section mapping');
 document.meshes.forEach((mesh,index)=>{
  const passes=sections[index];
  if (passes.length!==1) throw Error('Unsupported appearance: multiple native material passes');
  const native=materials[passes[0].materialIndex];
  if(!native || ![0,1].includes(native.blendingMode)) throw Error('Unsupported appearance: translucent or additive native material');
  for(const primitive of mesh.primitives){
   const original=document.materials[primitive.material];
   if(!original?.pbrMetallicRoughness?.baseColorTexture)throw Error('Missing native texture');
   const material=JSON.parse(JSON.stringify(original));
   material.alphaMode=native.blendingMode===1?'MASK':'OPAQUE';
   if(native.blendingMode===1)material.alphaCutoff=128/255;
   material.doubleSided=Boolean(native.flags&4);
   primitive.material=document.materials.length;document.materials.push(material);
  }
 });
 return document;
}
function writeGlb(original,document){
 const json=Buffer.from(JSON.stringify(document));
 const padded=Buffer.alloc(Math.ceil(json.length/4)*4,32);json.copy(padded);
 const rest=original.subarray(20+original.readUInt32LE(12));
 const header=Buffer.alloc(20);original.copy(header,0,0,12);
 header.writeUInt32LE(20+padded.length+rest.length,8);header.writeUInt32LE(padded.length,12);header.writeUInt32LE(0x4e4f534a,16);
 return Buffer.concat([header,padded,rest]);
}
module.exports={nativeMaterials,writeGlb};
