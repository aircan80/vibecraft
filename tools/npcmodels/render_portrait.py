"""Blender background script: native GLB -> transparent orthographic portrait.
Run blender --background --factory-startup --disable-autoexec --python THIS -- GLB OUTPUT
"""
import bpy, sys, math, json, hashlib, time
from pathlib import Path
from mathutils import Vector
started=time.monotonic()
arguments=sys.argv[sys.argv.index('--')+1:]
source, output=map(Path,arguments[:2])
turntable='--turntable' in arguments[2:]
request_path=arguments[arguments.index('--request')+1] if '--request' in arguments else None
request=json.loads(Path(request_path).read_text()) if request_path else None
display_scale=float(request['displayScale']) if request else 1.3
if not math.isfinite(display_scale) or not 0 < display_scale < 100:raise ValueError('invalid native display scale')
resolution=512 if request else 768
portrait_name='portrait.png' if request else 'mechanical-squirrel.png'
output.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(source.resolve()))
custom_shapes={b.custom_shape for o in bpy.context.scene.objects if o.type=='ARMATURE' for b in o.pose.bones if b.custom_shape}
meshes=[o for o in bpy.context.scene.objects if o.type=='MESH' and o not in custom_shapes]
if not meshes: raise RuntimeError('GLB contains no mesh')
# Native display scale is recorded explicitly rather than baked into mesh coordinates.
for obj in meshes: obj.scale *= display_scale
bpy.context.view_layer.update()
points=[o.matrix_world @ v.co for o in meshes for v in o.data.vertices]
lo=Vector(tuple(min(p[i] for p in points) for i in range(3)))
hi=Vector(tuple(max(p[i] for p in points) for i in range(3)))
center=(lo+hi)/2; span=hi-lo
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=24 if request else 48
scene.render.resolution_x=resolution;scene.render.resolution_y=resolution;scene.render.resolution_percentage=100
scene.render.film_transparent=True
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA'
scene.view_settings.view_transform='Standard';scene.view_settings.look='Medium High Contrast'
world=bpy.data.worlds.new('Soft studio');scene.world=world;world.use_nodes=True
world.node_tree.nodes['Background'].inputs['Color'].default_value=(0.55,0.60,0.70,1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value=0.7
# glTF is Y-up; Blender importer maps native model Z-up to Blender Y-forward.
# Bounds framing is independent of dimensions, preserving tail and whiskers.
radius=max(span)
def aim(obj,target):obj.rotation_euler=(target-obj.location).to_track_quat('-Z','Y').to_euler()
camdata=bpy.data.cameras.new('Portrait');cam=bpy.data.objects.new('Portrait',camdata);scene.collection.objects.link(cam)
cam.location=center+Vector((1.3,-1.7,1.0)).normalized()*radius*4
camdata.type='ORTHO';camdata.ortho_scale=radius*1.30;aim(cam,center);scene.camera=cam
for name,offset,power,size in [('Key',(-2,-3,4),220,4),('Fill',(3,-1,2),110,3),('Rim',(0,3,3),160,3)]:
 data=bpy.data.lights.new(name,'AREA');data.energy=power;data.shape='DISK';data.size=radius*size
 obj=bpy.data.objects.new(name,data);scene.collection.objects.link(obj);obj.location=center+Vector(offset)*radius;aim(obj,center)
# Creature material is opaque (M2 blendingMode 0); retain its original decoded texture.
materials=[]
for mat in bpy.data.materials:
 if not mat.use_nodes:continue
 bsdf=next((n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
 if bsdf:bsdf.inputs['Roughness'].default_value=0.76;bsdf.inputs['Metallic'].default_value=0.0
 materials.append(mat.name)
scene.render.filepath=str((output/portrait_name).resolve())
bpy.ops.render.render(write_still=True)
report={'renderer':bpy.app.version_string,'engine':'Cycles CPU','samples':scene.cycles.samples,'resolution':[resolution,resolution],
 'transparent':True,'camera':'orthographic three-quarter','pose':'native bind pose; no animation',
 'displayScale':display_scale,'bounds':{'min':list(lo),'max':list(hi)},'materials':materials,
 'inputSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
 'outputSha256':hashlib.sha256((output/portrait_name).read_bytes()).hexdigest(),
 'durationSeconds':round(time.monotonic()-started,3)}
proof_path=source.parent.parent/'attestation/content-proof.json'
if proof_path.exists():
 proof=json.loads(proof_path.read_text())
 if proof.get('proof_status')=='all_three_poc_assets_match_official_build_root':
  report['buildAttestation']={'build':proof['build'],'status':proof['proof_status'],'proofPath':str(proof_path.resolve()),'proofSha256':hashlib.sha256(proof_path.read_bytes()).hexdigest(),'buildConfig':proof['build_config_md5'],'rootContentKey':proof['root_content_key']}
if turntable:
 scene.cycles.samples=16
 frames=[]
 for index in range(12):
  angle=2*math.pi*index/12
  cam.location=center+Vector((math.cos(angle),math.sin(angle),0.38)).normalized()*radius*4
  aim(cam,center)
  filename=f'turntable-{index:02d}.png'
  scene.render.filepath=str((output/filename).resolve())
  bpy.ops.render.render(write_still=True)
  frames.append({'file':filename,'angle':index*30,'sha256':hashlib.sha256((output/filename).read_bytes()).hexdigest()})
 report['turntable']={'frames':frames,'samples':16,'description':'12 still views of the same native bind pose; not animation'}
 # A second actual studio render, with opaque neutral backdrop; no copied image.
 cam.location=center+Vector((1.3,-1.7,1.0)).normalized()*radius*4;aim(cam,center)
 scene.render.film_transparent=False
 world.node_tree.nodes['Background'].inputs['Color'].default_value=(0.08,0.11,0.16,1)
 scene.render.filepath=str((output/'studio.png').resolve())
 bpy.ops.render.render(write_still=True)
 report['studioSha256']=hashlib.sha256((output/'studio.png').read_bytes()).hexdigest()
 report['totalDurationSeconds']=round(time.monotonic()-started,3)
(output/'render-report.json').write_text(json.dumps(report,indent=2)+'\n')
