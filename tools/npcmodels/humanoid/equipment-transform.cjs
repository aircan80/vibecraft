'use strict';
// Bind-pose-only equivalent of pinned M2RendererGL.getAttachmentTransform.
function bindAttachment(model, id) {
 const i=model.attachmentLookup?.[id];
 const a=Number.isInteger(i)&&i>=0?model.attachments?.[i]:null;
 if(!a||a.id!==id||!Number.isInteger(a.bone)||a.bone<0||a.bone>=model.bones.length)throw Error('Missing exact native attachment/bone');
 const chain=[],seen=new Set();let bone=a.bone;
 while(bone>=0){
  if(seen.has(bone)||bone>=model.bones.length)throw Error('Invalid native bone hierarchy');
  seen.add(bone);const row=model.bones[bone];
  if(row.flags&0x78)throw Error('Unsupported billboard bone attachment');
  chain.push({index:bone,parentBone:row.parentBone,flags:row.flags,pivot:row.pivot});bone=row.parentBone;
 }
 const [x,y,z]=a.position;
 if(![x,y,z].every(Number.isFinite))throw Error('Invalid native attachment position');
 return {attachmentId:id,attachment:a,boneChain:chain,mode:'native bind pose; animation disabled',
  boneWorldMatrix:[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],
  translationWebGL:[x,z,-y],matrix:[1,0,0,0,0,1,0,0,0,0,1,0,x,z,-y,1],
  justification:'M2RendererGL starts every local matrix at identity; with no animation applied parent-chain multiplication remains identity. No rest pivot is added twice.'};
}
function translatedVertices(vertices,translation){
 if(vertices.length%3||translation.length!==3)throw Error('Invalid vertex dimensions');
 return vertices.map((v,i)=>v+translation[i%3]);
}
function helmetVisible(id,ranges){return !ranges.some(r=>id>=r.first&&id<=r.last);}
module.exports={bindAttachment,translatedVertices,helmetVisible};

function verifyGLBPositions(bytes, expected) {
 if(bytes.readUInt32LE(0)!==0x46546c67)throw Error('Invalid GLB');
 const jsonLength=bytes.readUInt32LE(12), document=JSON.parse(bytes.subarray(20,20+jsonLength));
 const a=document.accessors[document.meshes[0].primitives[0].attributes.POSITION], view=document.bufferViews[a.bufferView];
 if(a.type!=='VEC3'||a.componentType!==5126||a.count*3!==expected.length||view.buffer!==0)throw Error('Unsupported exported position layout');
 const start=20+jsonLength+8+(view.byteOffset??0)+(a.byteOffset??0),stride=view.byteStride??12;
 let error=0;const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
 for(let i=0;i<a.count;i++)for(let j=0;j<3;j++){
  const value=bytes.readFloatLE(start+i*stride+j*4);error=Math.max(error,Math.abs(value-expected[i*3+j]));min[j]=Math.min(min[j],value);max[j]=Math.max(max[j],value);
 }
 if(!Number.isFinite(error)||error>1e-6)throw Error(`Exported attachment position mismatch ${error}`);
 return {vertices:a.count,maxAbsoluteError:error,tolerance:1e-6,bounds:{min,max},coordinateSystem:'native loader WebGL [x,z,-y]'};
}
module.exports.verifyGLBPositions=verifyGLBPositions;
