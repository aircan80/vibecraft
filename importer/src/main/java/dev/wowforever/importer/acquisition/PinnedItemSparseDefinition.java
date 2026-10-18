package dev.wowforever.importer.acquisition;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;

/** Parses the independently authored WoWDBDefs definition only after verifying its reviewed content pin. */
public final class PinnedItemSparseDefinition {
 public static final String COMMIT="b9a63096cdd99b26967f17f9515377ae62cadfd7";
 public static final String SHA256="8664c3b5862dfe4c3a4322941b48b032c57b90412c070deb2028f3e448cf674a";
 public static final String BUILD="1.60.1.70450",LAYOUT="6FCC3191";
 public static final String SOURCE="https://raw.githubusercontent.com/wowdev/WoWDBDefs/"+COMMIT+"/definitions/ItemSparse.dbd";
 record Field(String name,String type,int bits,boolean unsigned,int count,boolean noninlineId) {}
 private final List<Field> fields;private final List<String> columns;
 private PinnedItemSparseDefinition(List<Field> fields,List<String> columns){this.fields=List.copyOf(fields);this.columns=List.copyOf(columns);}
 List<Field> fields(){return fields;}public List<String> columns(){return columns;}
 public static PinnedItemSparseDefinition parse(byte[] bytes)throws IOException {
  if(bytes.length>1_048_576||!SHA256.equals(hash(bytes)))throw new IOException("ItemSparse definition SHA-256 does not match reviewed pin");
  var definition=parseDefinition(bytes,BUILD,LAYOUT);
  if(definition.fields.size()!=69||definition.columns.size()!=104)throw new IOException("Unexpected pinned ItemSparse schema shape");
  return definition;
 }
 /** Fixture parser never bypasses the public production definition content pin. */
 static PinnedItemSparseDefinition parseDefinition(byte[] bytes,String build,String layout)throws IOException {
  if(bytes.length>1_048_576)throw new IOException("Definition exceeds byte limit");
  String text=utf8(bytes);String[] blocks=text.replace("\r\n","\n").split("\\n\\s*\\n");
  if(blocks.length<2||!blocks[0].startsWith("COLUMNS\n"))throw new IOException("Missing DBD COLUMNS section");
  Map<String,String> types=new HashMap<>();String[] declarations=blocks[0].split("\n");
  for(int i=1;i<declarations.length;i++){String line=clean(declarations[i]);if(line.isEmpty())continue;String[] pair=line.split("\\s+",2);if(pair.length!=2)throw new IOException("Malformed DBD column");String type=pair[0].replaceAll("<.*>",""),name=pair[1].replace("?","");if(types.put(name,type)!=null)throw new IOException("Duplicate DBD column");}
  List<String> selected=null;
  for(int i=1;i<blocks.length;i++){
   List<String> lines=blocks[i].lines().map(PinnedItemSparseDefinition::clean).filter(s->!s.isEmpty()).toList();
   boolean matches=lines.stream().filter(s->s.startsWith("BUILD ")).flatMap(s->Arrays.stream(s.substring(6).split(","))).anyMatch(s->s.trim().equals(build));
   if(!matches)continue;
   if(selected!=null)throw new IOException("Ambiguous exact DBD build layout");
   if(!lines.contains("LAYOUT "+layout))throw new IOException("ItemSparse layout hash does not match reviewed pin");selected=lines;
  }
  if(selected==null)throw new IOException("Exact selected build absent from DBD");
  Pattern pattern=Pattern.compile("(?:\\$([^$]*)\\$)?([A-Za-z_][A-Za-z0-9_]*)(?:<(u?)(8|16|32)>)?(?:\\[(\\d+)\\])?");
  List<Field> fields=new ArrayList<>();List<String> columns=new ArrayList<>();Set<String> unique=new HashSet<>();
  for(String line:selected){if(line.startsWith("BUILD ")||line.startsWith("LAYOUT ")||line.startsWith("COMMENT"))continue;Matcher m=pattern.matcher(line);if(!m.matches())throw new IOException("Unsupported DBD field declaration");
   String name=m.group(2),type=types.get(name);Set<String> annotations=m.group(1)==null?Set.of():new HashSet<>(Arrays.asList(m.group(1).split(",")));
   boolean noninline=annotations.contains("noninline");if(!Set.of("id","noninline").containsAll(annotations)||noninline&&(!annotations.contains("id")||!name.equals("ID")))throw new IOException("Unsupported DBD field annotations");
   int bits=m.group(4)==null?0:Integer.parseInt(m.group(4)),count=m.group(5)==null?1:Integer.parseInt(m.group(5));
   if(type==null||!Set.of("int","float","locstring","string").contains(type)||count<1||count>32||type.equals("int")&&bits==0||noninline&&count!=1)throw new IOException("Unsupported DBD field type or array");
   fields.add(new Field(name,type,bits,"u".equals(m.group(3)),count,noninline));
   for(int k=0;k<count;k++){String column=m.group(5)==null?name:name+"_"+k;if(!unique.add(column))throw new IOException("Duplicate expanded DBD column");columns.add(column);}
   if(fields.size()>256||columns.size()>512)throw new IOException("DBD schema exceeds field limit");
  }
  if(fields.isEmpty()||fields.stream().filter(Field::noninlineId).count()!=1)throw new IOException("Expected one noninline ID");
  return new PinnedItemSparseDefinition(fields,columns);
 }
 private static String clean(String line){return line.split("//",2)[0].trim();}
 static String utf8(byte[] bytes)throws IOException{try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException invalid){throw new IOException("Invalid UTF-8",invalid);}}
 static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
