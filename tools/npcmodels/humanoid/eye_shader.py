"""Narrow native shader16403 reference, intentionally not a generic M2 renderer."""
ALPHA_TEST = 0.501960814


def mod_add(tex1, tex2, mesh=(1., 1., 1., 1.), lighting=(1., 1., 1.)):
    """Linear inputs; preserve HDR color and alpha sum until output conversion.

    Native blendingMode1 tests the *sum* then returns mesh opacity, not the sum.
    This differs from alpha-over compositing and from clamping each source.
    """
    alpha = tex1[3] + tex2[3]
    if alpha < ALPHA_TEST:
        return None
    return tuple(mesh[i] * tex1[i] * lighting[i] + tex2[i] for i in range(3)) + (mesh[3],)


def sample_mod_add(sample, uv1, uv2):
    return mod_add(sample(uv1), sample(uv2))


def validate_eye_contract(inspection):
    indices = [i for i, s in enumerate(inspection['subMeshes']) if s['submeshID'] == 3301]
    if len(indices) != 1:
        raise ValueError('Expected exactly one native eye geoset3301')
    units = [u for u in inspection['textureUnits'] if u['skinSectionIndex'] == indices[0]]
    if len(units) != 1:
        raise ValueError('Unsupported eye multipass material')
    u = units[0]
    m = inspection['materials'][u['materialIndex']]
    refs = inspection['textureCombos'][u['textureComboIndex']:u['textureComboIndex']+2]
    if (u['shaderID'], u['textureCount'], m['blendingMode'], m['flags']) != (16403, 2, 1, 0):
        raise ValueError('Unsupported shader or native blend/lighting/cull flags')
    if [inspection['textureTypes'][r] for r in refs] != [19, 19]:
        raise ValueError('Expected two references to actual eye texture type19')
    # 0x4000 selects Diffuse_T1_T2; do not reuse UV1 for the additive texture.
    return {'shaderId':16403, 'pixelShader':'Combiners_Mod_Add',
            'vertexShader':'Diffuse_T1_T2', 'uvSets':[0,1], 'blendMode':1,
            'alphaTest':ALPHA_TEST, 'materialFlags':0, 'geoset':3301}


def install_blender_eye(material, meshes, inspection):
    """Diffuse(tex1)*studio lighting + emission(tex2), native summed-alpha cutout.

    Both image nodes decode sRGB to scene-linear. No MixRGB energy-normalization,
    alpha-over, or color clamp is introduced. Exterior lighting/fog is not copied:
    this laboratory uses the declared studio rig rather than the game world.
    """
    contract = validate_eye_contract(inspection)
    eye_meshes = [o for o in meshes if any(s.material == material for s in o.material_slots)]
    if not eye_meshes or any(len(o.data.uv_layers) != 2 for o in eye_meshes):
        raise ValueError('Native eye requires both exported UV arrays')
    uv_names = [u.name for u in eye_meshes[0].data.uv_layers]
    if any([u.name for u in o.data.uv_layers] != uv_names for o in eye_meshes):
        raise ValueError('Inconsistent eye UV array names')
    image = next((n.image for n in material.node_tree.nodes if n.type == 'TEX_IMAGE' and n.image), None)
    if image is None:
        raise ValueError('Missing embedded verified eye image')
    image.colorspace_settings.name = 'sRGB'
    material.node_tree.nodes.clear()
    nodes, links = material.node_tree.nodes, material.node_tree.links
    def new(kind, label):
        node = nodes.new(kind);node.label=label;return node
    textures=[]
    for i, uv in enumerate(uv_names):
        coords=new('ShaderNodeUVMap',f'Native UV{i+1}');coords.uv_map=uv
        texture=new('ShaderNodeTexImage',f'Eye3484614 sample{i+1}');texture.image=image
        texture.interpolation='Linear';texture.extension='REPEAT'
        links.new(coords.outputs['UV'],texture.inputs['Vector']);textures.append(texture)
    diffuse=new('ShaderNodeBsdfDiffuse','mesh_color(white) * tex1.rgb, lit')
    diffuse.inputs['Roughness'].default_value=0.0
    links.new(textures[0].outputs['Color'],diffuse.inputs['Color'])
    additive=new('ShaderNodeEmission','Native additive specular = tex2.rgb')
    additive.inputs['Strength'].default_value=1
    links.new(textures[1].outputs['Color'],additive.inputs['Color'])
    total=new('ShaderNodeAddShader','lit diffuse + additive (unclamped)')
    links.new(diffuse.outputs[0],total.inputs[0]);links.new(additive.outputs[0],total.inputs[1])
    alpha=new('ShaderNodeMath','tex1.a + tex2.a');alpha.operation='ADD';alpha.use_clamp=False
    links.new(textures[0].outputs['Alpha'],alpha.inputs[0]);links.new(textures[1].outputs['Alpha'],alpha.inputs[1])
    discard=new('ShaderNodeMath','Native blend1 strict alpha threshold');discard.operation='LESS_THAN';discard.inputs[1].default_value=ALPHA_TEST
    links.new(alpha.outputs[0],discard.inputs[0])
    # Native material flags0 means backface culling enabled.
    geometry=new('ShaderNodeNewGeometry','Native backface culling')
    invisible=new('ShaderNodeMath','discard OR backface');invisible.operation='MAXIMUM'
    links.new(discard.outputs[0],invisible.inputs[0]);links.new(geometry.outputs['Backfacing'],invisible.inputs[1])
    transparent=new('ShaderNodeBsdfTransparent','discarded fragment')
    mix=new('ShaderNodeMixShader','Opaque mesh opacity1 except discarded fragments')
    links.new(invisible.outputs[0],mix.inputs[0]);links.new(total.outputs[0],mix.inputs[1]);links.new(transparent.outputs[0],mix.inputs[2])
    output=new('ShaderNodeOutputMaterial','Native16403 surface');links.new(mix.outputs[0],output.inputs['Surface'])
    contract.update(uvLayerNames=uv_names,workingColorSpace='scene linear, sRGB image decode',
                    exteriorLighting='declared Cycles studio approximation; no world fog',backfaceCulling=True)
    return contract
