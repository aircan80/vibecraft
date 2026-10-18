#!/usr/bin/env python3
"""Audit exact-build humanoid customization joins; never authorize a portrait alone.

Geoset defaults and option toggles follow pinned wow.export character-appearance.js
(c2fd7bde36a712be78a5da896c995b84fbfa2545). Equipment overrides remain a
separate required stage. A recipe is dependency evidence, not a rendered NPC.
"""
import argparse
import json
from collections import defaultdict
from pathlib import Path

PRODUCT = 'wow_classic_beta'
BUILD = '1.60.1.70450'
DECODER = 'c2fd7bde36a712be78a5da896c995b84fbfa2545'
TABLES = ('CreatureDisplayInfoOption', 'ChrCustomizationChoice',
          'ChrCustomizationElement', 'ChrCustomizationGeoset',
          'ChrCustomizationOption', 'ChrModel', 'ChrModelMaterial')
UNSUPPORTED = ('ChrCustomizationSkinnedModelID', 'ChrCustomizationBoneSetID',
               'ChrCustomizationCondModelID', 'ChrCustomizationDisplayInfoID',
               'ChrCustItemGeoModifyID', 'ChrCustomizationVoiceID', 'AnimKitID',
               'ParticleColorID', 'ChrCustGeoComponentLinkID')


def number(row, key):
    return int(row.get(key, 0))


def checked_table(name, document, allow_incomplete_prototype=False):
    if (document.get('product'), document.get('build')) != (PRODUCT, BUILD):
        raise ValueError(f'{name}: wrong product/build')
    if document.get('table') != name or document.get('decoderCommit') != DECODER:
        raise ValueError(f'{name}: wrong table/decoder')
    for key in ('sourceSha256', 'definitionSha256'):
        value = document.get(key, '')
        if len(value) != 64 or any(c not in '0123456789abcdef' for c in value):
            raise ValueError(f'{name}: missing {key}')
    partial = document.get('skippedEncryptedSections') != []
    if partial and not allow_incomplete_prototype:
        raise ValueError(f'{name}: incomplete encrypted sections')
    if partial and not isinstance(document.get('inputCompleteness'), dict):
        raise ValueError(f'{name}: missing incompleteness evidence')
    rows = document['rows']
    index = {number(row, 'ID'): row for row in rows}
    if len(index) != len(rows):
        raise ValueError(f'{name}: duplicate record IDs')
    return index


def geoset_value(row):
    # JS concatenates padded fields, rather than multiplying blindly for >99.
    return int(str(number(row, 'GeosetType')).zfill(2) +
               str(number(row, 'GeosetID')).zfill(2))


def build_recipe(documents, extra_id, model_geosets=None, chr_model_id=None,
                 allow_incomplete_prototype=False):
    """Return blocked or geometry-only evidence. Missing tables are hard errors.

    chr_model_id must be resolved independently (race/sex are insufficient).
    model_geosets must come from the actual M2/SKIN, never synthesized here.
    """
    indices = {name: checked_table(name, documents[name], allow_incomplete_prototype) for name in TABLES}
    blockers, selected, raw_elements, selected_geosets = [], [], [], []
    incompleteness = {name: {key: documents[name].get(key) for key in
                     ('inputCompleteness', 'skippedEncryptedSections')}
                     for name in TABLES if documents[name].get('skippedEncryptedSections') != []}
    for name in incompleteness:
        blockers.append(f'{name}: encrypted records missing; known selections may be incomplete.')
    option_rows = [r for r in indices['CreatureDisplayInfoOption'].values()
                   if number(r, 'CreatureDisplayInfoExtraID') == int(extra_id)]
    if not option_rows:
        blockers.append('No explicit customization options for this extra ID; defaults are not inferred.')
    by_choice = defaultdict(list)
    for row in indices['ChrCustomizationElement'].values():
        by_choice[number(row, 'ChrCustomizationChoiceID')].append(row)
    seen_options = set()
    for row in option_rows:
        option_id, choice_id = number(row, 'ChrCustomizationOptionID'), number(row, 'ChrCustomizationChoiceID')
        choice = indices['ChrCustomizationChoice'].get(choice_id)
        option = indices['ChrCustomizationOption'].get(option_id)
        if option_id in seen_options:
            blockers.append(f'Duplicate selection for option {option_id}.')
        seen_options.add(option_id)
        if not choice or not option or number(choice, 'ChrCustomizationOptionID') != option_id:
            blockers.append(f'Missing or mismatched selected choice {choice_id} / option {option_id}.')
            continue
        if chr_model_id is not None and number(option, 'ChrModelID') not in (0, int(chr_model_id)):
            blockers.append(f'Option {option_id} belongs to another ChrModel.')
        selected.append(dict(optionRecord=row, option=option, choice=choice))
        for element in by_choice[choice_id]:
            raw_elements.append(element)
            if number(element, 'RelatedChrCustomizationChoiceID'):
                blockers.append(f'Element {element["ID"]}: related-choice condition is not implemented.')
            for field in UNSUPPORTED:
                if number(element, field):
                    blockers.append(f'Element {element["ID"]}: unsupported {field}={element[field]}.')
            if number(element, 'ChrCustomizationMaterialID'):
                blockers.append(f'Element {element["ID"]}: customization material composition is required.')
            geo_id = number(element, 'ChrCustomizationGeosetID')
            if geo_id:
                geo = indices['ChrCustomizationGeoset'].get(geo_id)
                if geo is None:
                    blockers.append(f'Missing selected geoset record {geo_id}.')
                else:
                    selected_geosets.append(dict(elementId=element['ID'], row=geo,
                                                 modelGeosetId=geoset_value(geo)))
                    if number(geo, 'Modifier') != -1:
                        blockers.append(f'Geoset {geo_id}: unsupported Modifier={geo.get("Modifier")}.')
    model = indices['ChrModel'].get(int(chr_model_id)) if chr_model_id is not None else None
    if model is None:
        blockers.append('Exact ChrModel has not been independently resolved.')
    materials = [r for r in indices['ChrModelMaterial'].values()
                 if model and number(r, 'CharComponentTextureLayoutsID') == number(model, 'CharComponentTextureLayoutID')]
    mask = None
    if model_geosets is None:
        blockers.append('Actual model geoset IDs have not been supplied.')
    else:
        ids = [int(g) for g in model_geosets]
        if len(ids) != len(set(ids)) or any(g < 0 for g in ids):
            raise ValueError('Model geoset IDs must be unique nonnegative integers')
        mask = {g: (g == 0 or str(g).endswith('01') or str(g).startswith('32'))
                and not str(g).startswith(('17', '35')) for g in ids}
        # Faithful order: each selected option iterates ALL its available choices.
        # More than one geoset-bearing element is ambiguous in the pinned cache
        # (last row wins), so block rather than depend on DB iteration accidents.
        for selection in selected:
            for choice in indices['ChrCustomizationChoice'].values():
                if number(choice, 'ChrCustomizationOptionID') != number(selection['option'], 'ID'):
                    continue
                elements = [e for e in by_choice[number(choice, 'ID')] if number(e, 'ChrCustomizationGeosetID')]
                if len(elements) > 1:
                    blockers.append(f'Choice {choice["ID"]}: multiple geoset elements require explicit semantics.')
                for element in elements:
                    if number(element, 'RelatedChrCustomizationChoiceID'):
                        blockers.append(f'Geoset alternative element {element["ID"]}: conditional mask unsupported.')
                    geo = indices['ChrCustomizationGeoset'].get(number(element, 'ChrCustomizationGeosetID'))
                    if geo is None:
                        blockers.append(f'Missing alternative geoset record for element {element["ID"]}.')
                        continue
                    if number(geo, 'Modifier') != -1:
                        blockers.append(f'Alternative geoset {geo["ID"]}: unsupported Modifier={geo.get("Modifier")}.')
                    value = geoset_value(geo)
                    if value in mask and value != 0:
                        mask[value] = number(choice, 'ID') == number(selection['choice'], 'ID')
        mask = [dict(id=g, checked=enabled) for g, enabled in mask.items()]
    geometry_blockers = list(dict.fromkeys(blockers))
    return dict(schema='humanoid-customization-recipe-v1', product=PRODUCT, build=BUILD,
                extraId=int(extra_id), state='INCOMPLETE_RECONSTRUCTION_PROTOTYPE' if incompleteness else 'DEPENDENCY_PLAN_ONLY', readyToRender=False,
                allowIncompletePrototype=bool(allow_incomplete_prototype), incompleteness=incompleteness,
                geometryResolved=not geometry_blockers,
                blockers=geometry_blockers + ['Equipment visibility, textures and attachments require a separately verified stage.'],
                selected=selected, elements=raw_elements, geosets=selected_geosets,
                customizationGeosetMask=mask,
                enabledGeosetIds=[g['id'] for g in mask if g['checked']] if mask is not None else None,
                chrModel=model, modelMaterials=materials,
                provenance={name: {k: documents[name][k] for k in
                            ('sourceSha256', 'definitionSha256', 'decoderCommit')} for name in TABLES},
                algorithmSource=dict(commit=DECODER, path='src/js/ui/character-appearance.js',
                                     method='apply_customization_geosets'),
                warning='Customization-only mask must not be used as final equipment visibility.')


def texture_dependencies(recipe, materials, layers, textures):
    """Resolve selected known material dependencies, not final atlas composition.

    Related-choice filtering follows apply_customization_textures. Missing or
    ambiguous joins remain evidence; no first-row fallback or body/hair reuse.
    """
    material_index = checked_table('ChrCustomizationMaterial', materials)
    layer_index = checked_table('ChrModelTextureLayer', layers)
    # Older TextureFileData decoder output predates skippedEncryptedSections.
    # No completeness is inferred: require the caller to supply that evidence.
    texture_index = checked_table('TextureFileData', textures)
    selected_ids = {number(s['choice'], 'ID') for s in recipe['selected']}
    model = recipe.get('chrModel')
    if model is None:
        raise ValueError('Exact ChrModel required for material layer lookup')
    layout = number(model, 'CharComponentTextureLayoutID')
    results = []
    for element in recipe['elements']:
        material_id = number(element, 'ChrCustomizationMaterialID')
        if not material_id:
            continue
        related = number(element, 'RelatedChrCustomizationChoiceID')
        if related and related not in selected_ids:
            continue
        material = material_index.get(material_id)
        entry = dict(element=element, relatedChoiceSatisfied=not related or related in selected_ids,
                     material=material, state='MISSING_MATERIAL')
        if material is not None:
            target = number(material, 'ChrModelTextureTargetID')
            matched = [r for r in layer_index.values()
                       if number(r, 'CharComponentTextureLayoutsID') == layout
                       and r.get('ChrModelTextureTargetID', [None])[0] == target]
            resources = sorted({number(r, 'FileDataID') for r in texture_index.values()
                                if number(r, 'UsageType') == 0 and number(r, 'MaterialResourcesID') == number(material, 'MaterialResourcesID')})
            entry.update(layers=matched, textureFileDataIds=resources,
                         state='UNIQUE_DEPENDENCY' if len(matched) == 1 and len(resources) == 1 else 'MISSING_OR_AMBIGUOUS')
            if len(matched) == 1:
                entry['textureType'] = number(matched[0], 'TextureType')
                entry['modelMaterials'] = [r for r in recipe['modelMaterials']
                                          if number(r, 'TextureType') == entry['textureType']]
        results.append(entry)
    return dict(schema='known-customization-texture-dependencies-v1', readyToRender=False,
                inputRecipeState=recipe['state'], dependencies=results,
                provenance={d['table']: {k: d[k] for k in ('sourceSha256', 'definitionSha256', 'decoderCommit')}
                            for d in (materials, layers, textures)},
                warning='Known selected choices only; incomplete source rows and unresolved equipment still prevent complete appearance claims.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tables', type=Path, required=True)
    parser.add_argument('--extra-id', type=int, required=True)
    parser.add_argument('--chr-model-id', type=int)
    parser.add_argument('--model-geosets', type=Path, help='JSON array of actual M2/SKIN geoset IDs')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--allow-incomplete-prototype', action='store_true',
                        help='Known-data reconstruction only; never authorizes a portrait')
    args = parser.parse_args()
    docs = {}
    for name in TABLES:
        path = args.tables / (name + '.json')
        if not path.exists() and args.allow_incomplete_prototype:
            path = args.tables / (name + '.partial.json')
        docs[name] = json.loads(path.read_text())
    ids = json.loads(args.model_geosets.read_text()) if args.model_geosets else None
    result = build_recipe(docs, args.extra_id, ids, args.chr_model_id, args.allow_incomplete_prototype)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'state': result['state'], 'geometryResolved': result['geometryResolved'],
                      'blockers': result['blockers']}))


if __name__ == '__main__':
    main()
