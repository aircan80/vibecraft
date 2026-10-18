"""Blender background script: native GLB -> transparent orthographic portrait.
Run blender --background --factory-startup --disable-autoexec --python THIS -- GLB OUTPUT
"""
import bpy, sys, math, json, hashlib, time
from pathlib import Path
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
from eye_shader import install_blender_eye
started=time.monotonic()
arguments=sys.argv[sys.argv.index('--')+1:]
source, output=map(Path,arguments[:2])
turntable='--turntable' in arguments[2:]
output.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(source.resolve()))
helmet_source=source.parent/'native-helmet.glb'
if '--helmet' in arguments[2:]:
 if not helmet_source.exists():raise RuntimeError('Requested native helmet GLB missing')
 bpy.ops.import_scene.gltf(filepath=str(helmet_source.resolve()))
custom_shapes={b.custom_shape for o in bpy.context.scene.objects if o.type=='ARMATURE' for b in o.pose.bones if b.custom_shape}
meshes=[o for o in bpy.context.scene.objects if o.type=='MESH' and o not in custom_shapes]
if not meshes: raise RuntimeError('GLB contains no mesh')
# Actual CreatureDisplayInfo15660 scale, common to body and native helmet.
display_scale=0.40000000596
for obj in meshes: obj.scale *= display_scale
bpy.context.view_layer.update()
points=[o.matrix_world @ v.co for o in meshes for v in o.data.vertices]
lo=Vector(tuple(min(p[i] for p in points) for i in range(3)))
hi=Vector(tuple(max(p[i] for p in points) for i in range(3)))
center=(lo+hi)/2; span=hi-lo
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=24
scene.render.resolution_x=768;scene.render.resolution_y=768;scene.render.resolution_percentage=100
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
 data=bpy.data.lights.new(name,'AREA');data.energy=power*(display_scale/1.3)**2;data.shape='DISK';data.size=radius*size
 obj=bpy.data.objects.new(name,data);scene.collection.objects.link(obj);obj.location=center+Vector(offset)*radius;aim(obj,center)
# Creature material is opaque (M2 blendingMode 0); retain its original decoded texture.
materials=[]
for mat in bpy.data.materials:
 if not mat.use_nodes:continue
 bsdf=next((n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
 if bsdf:bsdf.inputs['Roughness'].default_value=0.76;bsdf.inputs['Metallic'].default_value=0.0
 materials.append(mat.name)
eye_contract=None
inspection=json.loads((source.parent/'model-inspection.json').read_text())
for material in bpy.data.materials:
 if material.name=='data-19':eye_contract=install_blender_eye(material,meshes,inspection)
scene.render.filepath=str((output/'partial-humanoid.png').resolve())
bpy.ops.render.render(write_still=True)
report={'renderer':bpy.app.version_string,'engine':'Cycles CPU','samples':24,'resolution':[768,768],
 'transparent':True,'camera':'orthographic three-quarter','pose':'native bind pose; no animation',
 'nativeDisplayScale':display_scale,'modelScale':1.0,'displayScaleApplied':True,'bounds':{'min':list(lo),'max':list(hi)},'materials':materials,
 'inputSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
 'outputSha256':hashlib.sha256((output/'partial-humanoid.png').read_bytes()).hexdigest(),
 'durationSeconds':round(time.monotonic()-started,3),'eyeShader':eye_contract,'nativeHelmet':json.loads((source.parent/'helmet-proof.json').read_text()) if '--helmet' in arguments[2:] else None}
# Optional native head/shoulders framing; this changes only the camera.
if '--head' in arguments[2:]:
 head_center=Vector((center.x,center.y,hi.z-span.z*0.20))
 cam.location=head_center+Vector((2.4,-0.6,0.35)).normalized()*radius*4
 camdata.ortho_scale=span.z*0.56;aim(cam,head_center)
 scene.render.filepath=str((output/'partial-humanoid-head.png').resolve())
 bpy.ops.render.render(write_still=True)
 report['headPortrait']={'file':'partial-humanoid-head.png','camera':'head and shoulders; same unmodified geometry','sha256':hashlib.sha256((output/'partial-humanoid-head.png').read_bytes()).hexdigest()}
if '--head' in arguments[2:]:
 scene.render.resolution_x=128;scene.render.resolution_y=128
 scene.render.filepath=str((output/'partial-humanoid-head-128.png').resolve())
 bpy.ops.render.render(write_still=True)
 report['headPortrait128']={'file':'partial-humanoid-head-128.png','sha256':hashlib.sha256((output/'partial-humanoid-head-128.png').read_bytes()).hexdigest()}
report['totalDurationSeconds']=round(time.monotonic()-started,3)
report['status']='LAB_PARTIAL_BODY_ONLY_NOT_AUTHORIZED'
report['limitations']='Incomplete customization and remaining gear coverage; review only, never app cache.'
(output/'render-report.json').write_text(json.dumps(report,indent=2)+'\n')
