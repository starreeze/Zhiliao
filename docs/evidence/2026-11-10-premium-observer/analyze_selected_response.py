from pathlib import Path
import base64,binascii,collections,gzip,html,json,re,zlib

ROOT=Path(__file__).parent
raw_path=ROOT/'selected-response-private.json'
raw=raw_path.read_bytes()
response=json.loads(raw)
anchor=(ROOT/'premium-anchor.txt').read_text()
segments=response['structured_content']['segments']
paragraphs=[s['paragraph']['text'] for s in segments if s['type']=='paragraph']
story=''.join(paragraphs)
field_stats={}
json_strings=[]
opaque_strings=[]
text_candidates=[]
semantic_values=[]
marker_values=[]
json_parse_failures=[]
known_markers=['精彩内容待解锁','看广告免费解锁','付费阅读内容仅盐选会员可评论','选自盐选专栏']
safe_key=re.compile(r'[A-Za-z_][A-Za-z0-9_]{0,79}\Z')
base64_chars=re.compile(r'[A-Za-z0-9+/]+={0,2}\Z')
semantic_keys={'business_type','sub_type','card_type','auth_result','authentication_result','is_free','is_lock','is_paid','is_purchased','has_purchased','can_reply','is_end','need_pay','need_ad','preview','truncate_ratio','truncate_session_id'}

def keypart(k):
 return k if safe_key.fullmatch(k) else '<dynamic_key>'

def chinese_count(s):
 return sum('\u3400'<=c<='\u9fff' for c in s)

def root_role(path):
 if path.startswith('$.structured_content.segments[*].paragraph.text'):return 'recognized_story'
 if path=='$.excerpt':return 'excerpt'
 if path.startswith('$.author'):return 'author_metadata'
 if path.startswith('$.question'):return 'question_metadata'
 if path.startswith('$.structured_content.segments[*].card.extra_info'):return 'header_or_tail_card_fields'
 if path.startswith('$.structured_content.paging'):return 'paging'
 if path.startswith('$.third_business'):return 'third_business_metadata'
 if path.startswith('$.comment_config'):return 'comment_configuration'
 return 'other_root_fields'

def inspect_string(s,path):
 cn=chinese_count(s)
 st=field_stats.setdefault(path,{'count':0,'min_chars':len(s),'max_chars':0,'total_chars':0,'chinese_chars':0})
 st['count']+=1;st['min_chars']=min(st['min_chars'],len(s));st['max_chars']=max(st['max_chars'],len(s));st['total_chars']+=len(s);st['chinese_chars']+=cn
 for i,m in enumerate(known_markers):
  if m in s:marker_values.append({'path':path,'known_gate_id':i,'occurrences':s.count(m)})
 if cn>=8 and len(s)>=16:
  text_candidates.append({'path':path,'chars':len(s),'chinese_chars':cn,'role':root_role(path),'entire_value_is_in_recognized_story':s in story,'anchor_matches':s.count(anchor)})
 if s.lstrip().startswith(('{','[')):
  try:o=json.loads(s)
  except json.JSONDecodeError:json_parse_failures.append({'path':path,'chars':len(s)})
  else:
   json_strings.append({'path':path,'chars':len(s),'decoded_kind':type(o).__name__,'decoded_keys':[keypart(k) for k in o] if isinstance(o,dict) else None})
   walk(o,path+'::<json>');return
 if len(s)>=16 and len(s)%4==0 and base64_chars.fullmatch(s):
  try:b=base64.b64decode(s,validate=True)
  except binascii.Error:return
  meta={'path':path,'encoded_chars':len(s),'decoded_bytes':len(b)}
  compression=None
  if b.startswith(b'\x1f\x8b'):
   b=gzip.decompress(b);compression='gzip'
  elif b.startswith((b'x\x01',b'x\x9c',b'x\xda')):
   b=zlib.decompress(b);compression='zlib'
  meta['compression']=compression
  try:t=b.decode('utf-8')
  except UnicodeDecodeError:meta['decoded_utf8']=False
  else:
   meta.update({'decoded_utf8':True,'decoded_chars':len(t),'decoded_chinese_chars':chinese_count(t),'decoded_anchor_matches':t.count(anchor)})
   if t.lstrip().startswith(('{','[')):
    try:o=json.loads(t)
    except json.JSONDecodeError:meta['decoded_json']=False
    else:
     meta['decoded_json']=True;walk(o,path+'::<base64-json>')
  opaque_strings.append(meta)

def walk(o,path):
 if isinstance(o,dict):
  for k,v in o.items():
   p=path+'.'+keypart(k)
   if k in semantic_keys:
    if v is None or isinstance(v,(bool,int,float)):semantic_values.append({'path':p,'value':v})
    elif isinstance(v,str) and len(v)<=80 and re.fullmatch(r'[A-Za-z0-9_.:-]*',v):semantic_values.append({'path':p,'value':v})
    else:semantic_values.append({'path':p,'value_kind':type(v).__name__})
   walk(v,p)
 elif isinstance(o,list):
  for v in o:walk(v,path+'[*]')
 elif isinstance(o,str):inspect_string(o,path)

walk(response,'$')
root_schema=[]
for k,v in response.items():
 d={'field':keypart(k),'kind':type(v).__name__}
 if isinstance(v,(str,list,dict)):d['length']=len(v)
 if isinstance(v,dict):d['keys']=[keypart(w) for w in v]
 root_schema.append(d)
card_summaries=[]
for i,s in enumerate(segments):
 if s['type']=='card':
  card=s['card'];parsed=json.loads(card['extra_info'])
  card_summaries.append({'index':i,'card_type':card['card_type'],'card_keys':list(card),'extra_info_keys':list(parsed),'extra_info_chars':len(card['extra_info'])})
root_nonstory=[x for x in text_candidates if x['role'] not in ['recognized_story','excerpt']]
normalize=lambda s:re.sub(r'[\s\u200b\ufeff]+','',html.unescape(re.sub(r'<[^>]*>','',s)))
excerpt_core=normalize(response.get('excerpt','')).rstrip('.…')
normalized_story=normalize(story)
events=[json.loads(line) for line in (ROOT/'capture-final-events.jsonl').read_text().splitlines()]
provenance=[{k:v for k,v in e.items() if k not in ['shape','received_segments_aggregate','elapsed_ms']} for e in events if e['event'] in ['private_capture_saved','response_structure'] and (e.get('answer_id')=='1977823141666501380' or e.get('requested_fixture_id')=='1977823141666501380')]
body_alternatives={k:{'kind':type(response.get(k)).__name__,'length':len(response[k]) if isinstance(response.get(k),(list,dict,str)) else None} for k in ['structured_content_vip','video','thumbnail','catalog']}

result={
 'schema':'sanitized_selected_native_response',
 'provenance':provenance,
 'alternative_body_containers':body_alternatives,
 'normalized_excerpt_core_chars':len(excerpt_core),
 'normalized_excerpt_core_is_story_prefix':normalized_story.startswith(excerpt_core),
 'selected_answer_id':str(response['id']),
 'representation':'parsed_json_reserialized_snapshot',
 'parsed_snapshot_bytes':len(raw),
 'received_peek_bytes':[e['peek_bytes'] for e in provenance if e['event']=='response_structure'],
 'root_schema':root_schema,
 'field_role_note':'Path categories only; actual fields and lengths require interpretation with renderer/schema evidence.',
 'segment_count':len(segments),
 'segment_types':dict(collections.Counter(s['type']+(':'+s['card']['card_type'] if s['type']=='card' else '') for s in segments)),
 'recognized_story_paragraphs':len(paragraphs),'recognized_story_chars':len(story),
 'anchor_chars':len(anchor),'anchor_matches':story.count(anchor),'anchor_at_story_end':story.endswith(anchor),'story_chars_after_anchor':len(story)-story.rfind(anchor)-len(anchor) if anchor in story else None,
 'last_segment_type':segments[-1]['type'],'last_card_type':segments[-1]['card']['card_type'],
 'excerpt_chars':len(response.get('excerpt','')),'excerpt_fully_in_story':response.get('excerpt','') in story,
 'card_summaries':card_summaries,
 'json_strings_parsed':json_strings,'json_like_parse_failures':json_parse_failures,
 'semantic_values':semantic_values,'known_marker_locations':marker_values,
 'opaque_or_base64_strings':opaque_strings,
 'all_string_field_inventory':[dict(path=p,**v) for p,v in sorted(field_stats.items())],
 'non_story_textual_candidates':root_nonstory,
 'raw_values_logged':False
}
out=ROOT/'response-evidence.json';out.write_text(json.dumps(result,ensure_ascii=False,indent=2));out.chmod(0o600)
print(json.dumps({k:result[k] for k in ['selected_answer_id','parsed_snapshot_bytes','received_peek_bytes','segment_count','segment_types','recognized_story_paragraphs','recognized_story_chars','anchor_chars','anchor_matches','anchor_at_story_end','story_chars_after_anchor','last_card_type','excerpt_chars','excerpt_fully_in_story']},ensure_ascii=False))
print('parsed_json_strings',len(json_strings),'string_field_groups',len(field_stats),'opaque_base64_candidates',len(opaque_strings),'json_like_parse_failures',len(json_parse_failures))
print('card_summaries',json.dumps(card_summaries,ensure_ascii=False))
print('semantic_values',json.dumps(semantic_values,ensure_ascii=False))
print('non_story_textual_candidates',json.dumps(root_nonstory,ensure_ascii=False))
