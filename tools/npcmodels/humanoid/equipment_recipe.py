#!/usr/bin/env python3
"""Known-data NPC gear evidence; no automatic render/cache authorization.

NPC extra IDs, not creature IDs, key NPCModelItemSlotDisplayInfo.NpcModelID.
Native slot0 maps to equipment head1 and M2 attachment11 in pinned
wow.export EquipmentSlots.js. Never substitute a named/listfile hat.
"""
import argparse
import json
from pathlib import Path
from customization_recipe import BUILD, PRODUCT, DECODER, checked_table, number


def choose_component(candidates, components, race, sex):
    """Strict native selection, deliberately omitting exporter's first fallback."""
    candidates = set(candidates)
    tiers = [lambda r: number(r,'RaceID') == race and number(r,'GenderIndex') == sex,
             lambda r: number(r,'RaceID') == race and number(r,'GenderIndex') == 2,
             lambda r: number(r,'RaceID') == 0 and number(r,'GenderIndex') in (sex,2)]
    for rank, predicate in enumerate(tiers):
        matches = sorted(fid for fid in candidates if fid in components and predicate(components[fid]))
        if matches:
            return dict(state='UNIQUE' if len(matches)==1 else 'AMBIGUOUS', tier=rank,
                        fileDataIds=matches, rows=[components[f] for f in matches])
    return dict(state='MISSING_COMPONENT_MATCH', fileDataIds=sorted(candidates))


def equipment_recipe(slot_document, display_document, textures, extra_id, race, sex,
                     model_files=None, component_files=None, helmet_data=None,
                     allow_incomplete_prototype=False):
    inputs=[slot_document,display_document,textures]
    indices={d['table']:checked_table(d['table'],d,allow_incomplete_prototype) for d in inputs}
    for name,document in [('ModelFileData',model_files),('ComponentModelFileData',component_files),('HelmetGeosetData',helmet_data)]:
        if document is not None:
            indices[name]=checked_table(name,document,allow_incomplete_prototype)
            inputs.append(document)
    missing={d['table']:{k:d.get(k)for k in ('inputCompleteness','skippedEncryptedSections')}
             for d in inputs if d.get('skippedEncryptedSections')!=[]}
    slots=[r for r in indices['NPCModelItemSlotDisplayInfo'].values() if number(r,'NpcModelID')==int(extra_id)]
    result=[]
    for slot in slots:
        display=indices['ItemDisplayInfo'].get(number(slot,'ItemDisplayInfoID'))
        entry=dict(slotRecord=slot,display=display,blockers=[],modelDependencies=[],textureDependencies=[])
        if display is None:
            entry['blockers'].append('ItemDisplayInfo row missing; encrypted absence is not empty gear.')
            result.append(entry);continue
        for position,resource in enumerate(display.get('ModelResourcesID',[])):
            if not resource:continue
            dependency=dict(position=position,resourceId=resource,state='MISSING_MODEL_FILE_TABLE')
            if 'ModelFileData'in indices:
                rows=[r for r in indices['ModelFileData'].values() if number(r,'ModelResourcesID')==resource]
                candidates=sorted({number(r,'ID')for r in rows})
                dependency.update(rows=rows,fileDataIds=candidates,state='UNRESOLVED_COMPONENT_VARIANT')
                if 'ComponentModelFileData'in indices:
                    dependency['selection']=choose_component(candidates,indices['ComponentModelFileData'],race,sex)
                    dependency['state']=dependency['selection']['state']
            entry['modelDependencies'].append(dependency)
        for position,resource in enumerate(display.get('ModelMaterialResourcesID',[])):
            if not resource:continue
            rows=[r for r in indices['TextureFileData'].values() if number(r,'MaterialResourcesID')==resource and number(r,'UsageType')==0]
            ids=sorted({number(r,'FileDataID')for r in rows})
            entry['textureDependencies'].append(dict(position=position,resourceId=resource,rows=rows,
                fileDataIds=ids,state='UNIQUE'if len(ids)==1 else 'MISSING_OR_AMBIGUOUS'))
        if number(slot,'ItemSlot')==0:
            entry['attachment']=dict(id=11,source='wow.export EquipmentSlots.js native0→equipment1→HELMET11',
                transformState='UNRESOLVED_NATIVE_ANCHOR_AND_BONE',
                required='Read character M2 or SKEL attachment11, local position, bone index and evaluated bone-world transform; apply exact coordinate conversion and character model transform.')
            vis=display.get('HelmetGeosetVis',[])
            # Array index selection is sex-specific in pinned DBItemGeosets.
            selected_vis=vis[sex]if sex in (0,1)and len(vis)>sex else None
            entry['helmetVisibility']=dict(rawIds=vis,sex=sex,selectedId=selected_vis,state='UNRESOLVED')
            if selected_vis is not None and 'HelmetGeosetData'in indices:
                rows=[r for r in indices['HelmetGeosetData'].values()if number(r,'HelmetGeosetVisDataID')==selected_vis and number(r,'RaceID')==race]
                entry['helmetVisibility'].update(rows=rows,hideGroups=[number(r,'HideGeosetGroup')for r in rows],
                    hideGeosetRanges=[dict(first=number(r,'HideGeosetGroup')*100+1,last=number(r,'HideGeosetGroup')*100+99)for r in rows],
                    state='KNOWN_ROWS_ONLY'if missing else 'RESOLVED_ROWS')
            entry['blockers'].append('Native attachment bone transform and item material binding must be independently verified.')
        entry['blockers'].append('Equipment geoset/texture composition is not authorized by this dependency recipe.')
        result.append(entry)
    return dict(schema='npc-equipment-recipe-v1',product=PRODUCT,build=BUILD,extraId=int(extra_id),race=race,sex=sex,
        state='INCOMPLETE_GEAR_PROTOTYPE'if missing else 'GEAR_DEPENDENCY_PLAN_ONLY',readyToRender=False,
        inputIncompleteness=missing,slots=result,
        blockers=['Missing encrypted rows can hide additional equipment.' ]if missing else [],
        provenance={d['table']:{k:d[k]for k in ('sourceSha256','definitionSha256','decoderCommit')}for d in inputs},
        algorithmSource=dict(commit=DECODER,files=['src/js/wow/EquipmentSlots.js','src/js/db/caches/DBItemModels.js','src/js/db/caches/DBItemGeosets.js','src/js/3D/renderers/M2RendererGL.js','src/js/modules/tab_creatures.js']))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tables',type=Path,required=True)
    p.add_argument('--extra-id',type=int,required=True)
    p.add_argument('--race',type=int,required=True)
    p.add_argument('--sex',type=int,choices=[0,1],required=True)
    p.add_argument('--allow-incomplete-prototype',action='store_true')
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args()
    def read(name,required=True):
        path=a.tables/(name+'.json')
        if not path.exists()and a.allow_incomplete_prototype:path=a.tables/(name+'.partial.json')
        if not path.exists()and not required:return None
        return json.loads(path.read_text())
    r=equipment_recipe(read('NPCModelItemSlotDisplayInfo'),read('ItemDisplayInfo'),read('TextureFileData'),
                       a.extra_id,a.race,a.sex,read('ModelFileData',False),read('ComponentModelFileData',False),read('HelmetGeosetData',False),a.allow_incomplete_prototype)
    a.output.write_text(json.dumps(r,indent=2)+'\n')
    print(json.dumps({'state':r['state'],'slots':len(r['slots'])}))


if __name__=='__main__':main()
