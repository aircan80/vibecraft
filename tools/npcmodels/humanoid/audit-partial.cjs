'use strict';
const crypto=require('crypto');
function auditPartial(binary,manifest,sections){
 if(manifest.schema!=='authenticated-partial-blte-v1'||manifest.product!=='wow_classic_beta'||manifest.build!=='1.60.1.70450')throw Error('Wrong partial source identity');
 if(crypto.createHash('sha256').update(binary).digest('hex')!==manifest.decodedSha256||binary.length!==manifest.decodedSize)throw Error('Partial byte hash/length mismatch');
 const ranges=manifest.erasedRanges;
 if(!Array.isArray(ranges)||!ranges.length)throw Error('No declared encrypted omissions');
 const physical=sections.filter(s=>s.header.recordCount>0).slice().sort((a,b)=>a.header.fileOffset-b.header.fileOffset);
 if(!physical.length)throw Error('No sections to audit');
 for(const range of ranges){
  const {offset,length}=range;
  if(!Number.isSafeInteger(offset)||!Number.isSafeInteger(length)||offset<0||length<=0||offset+length>binary.length)throw Error('Invalid erased range');
  if(binary.subarray(offset,offset+length).some(v=>v!==0))throw Error('Erased range is not zero placeholder');
  if(offset<physical[0].header.fileOffset)throw Error('Erased metadata');
  let covered=false;
  for(let i=0;i<physical.length;i++){
   const section=physical[i],start=section.header.fileOffset,end=i+1<physical.length?physical[i+1].header.fileOffset:binary.length;
   if(offset<end&&offset+length>start){
    if(!section.isEncrypted)throw Error('Erased bytes overlap readable section');
    if(offset>=start&&offset+length<=end)covered=true;
   }
  }
  if(!covered)throw Error('Erased range crosses section boundaries');
 }
 return {state:'PARTIAL_AUTHENTICATED_CHUNKS',erasedRanges:ranges,skippedSections:sections.filter(s=>s.isEncrypted).map(s=>({fileOffset:s.header.fileOffset,recordCount:s.header.recordCount}))};
}
module.exports={auditPartial};
