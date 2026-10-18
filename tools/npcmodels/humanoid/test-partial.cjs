'use strict';const assert=require('assert'),crypto=require('crypto');const {auditPartial}=require('./audit-partial.cjs');
function fixture(){const bytes=Buffer.alloc(100);const m={schema:'authenticated-partial-blte-v1',product:'wow_classic_beta',build:'1.60.1.70450',decodedSha256:crypto.createHash('sha256').update(bytes).digest('hex'),decodedSize:100,erasedRanges:[{offset:80,length:20,keyName:'test'}]};const sections=[{header:{fileOffset:20,recordCount:2},isEncrypted:false},{header:{fileOffset:80,recordCount:1},isEncrypted:true}];return[bytes,m,sections];}
assert.equal(auditPartial(...fixture()).state,'PARTIAL_AUTHENTICATED_CHUNKS');
let [b,m,s]=fixture();s[1].isEncrypted=false;assert.throws(()=>auditPartial(b,m,s),/readable/);
[b,m,s]=fixture();m.erasedRanges[0].offset=10;assert.throws(()=>auditPartial(b,m,s),/metadata/);
[b,m,s]=fixture();m.decodedSha256='wrong';assert.throws(()=>auditPartial(b,m,s),/hash/);
[b,m,s]=fixture();m.erasedRanges[0].offset=70;m.erasedRanges[0].length=30;assert.throws(()=>auditPartial(b,m,s),/readable/);
[b,m,s]=fixture();m.build='1.15.9.70003';assert.throws(()=>auditPartial(b,m,s),/identity/);
console.log('6 partial section audit checks passed');
