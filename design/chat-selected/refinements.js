/* Selected C direction. All state stays local to the offline design study. */
const selectedFresh = fresh;
fresh = () => ({...selectedFresh(),reads:{},saved:[],selection:{},searches:{},positions:{}});
models={a:fresh(),b:fresh(),c:fresh()};
scheme='c';
design.c={title:'少一层包裹，多一点连贯',body:'收到的文字直接排版，自己发出的消息用柔和底色区分。玻璃留给导航、输入栏和操作菜单，群身份与贴吧内容照常保留。',tags:['滑动引用','原消息定位','共享内容'],captions:['置顶与未读有清楚层级；会话草稿、筛选与返回位置保留。','左滑回复，长按操作；点击引用可直接定位原消息。','群公告轻条常驻；点群名集中查看成员、图片和帖子。']};
const reduceMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
const originalConversation=conversation,originalInbox=renderInbox,originalChat=renderChat,originalMessage=messageHTML,originalSend=send;
const realRead = p => model().reads[p.id] ?? p.unread;
conversation = (p,room=false) => {
  const m=model(),draft=m.drafts[p.id],latest=m.messages[p.id]?.at(-1),muted=m.muted[p.id];
  let preview=draft?`草稿：${draft}`:latest?.me?`${latest.status==='failed'?'发送失败':latest.status==='sending'?'发送中':'我'}：${latest.text}`:p.preview;
  const effective={...p,unread:realRead(p),preview};
  let html=originalConversation(effective,room);
  html=html.replace('conversation-preview"',`conversation-preview ${draft?'has-draft':''}"`);
  if(muted)html=html.replace('<span class="conversation-time">','<span class="mute-mark" aria-label="已开启免打扰">'+icon('bell')+'</span><span class="conversation-time">');
  return `<div class="conversation-item">${html}<button class="conversation-options" data-action="conversation-options" data-id="${p.id}" aria-label="管理${esc(p.name)}的会话">${icon('more')}</button></div>`;
};
inboxResults = () => {
  const m=model();
  if(demoState==='empty')return empty('还没有对话','收到的私信与加入的吧群会按时间显示在这里。','查看演示会话','restore');
  if(demoState==='error')return empty('连接暂时不可用','保留重试入口；失败与没有消息分别呈现。','重新加载','restore','refresh');
  const query=m.query.trim().toLowerCase();
  let list=[...contacts,...(m.accepted?[requestPerson]:[])].filter(p=>(m.filter==='all'||m.filter===p.type||m.filter==='unread'&&realRead(p)>0)&&`${p.name}${p.forum||''}${m.drafts[p.id]||p.preview}`.toLowerCase().includes(query));
  if(!list.length)return empty(m.filter==='unread'?'未读消息都看完了':'没有找到相关对话',m.filter==='unread'?'可以回到全部对话继续交流。':'换个昵称、吧名或文字试试。',m.filter==='unread'?'查看全部':'清除搜索',m.filter==='unread'?'all-chats':'clear-search','search');
  list.sort((a,b)=>Number(m.pins[b.id]??!!b.pinned)-Number(m.pins[a.id]??!!a.pinned));
  let prevPinned=null;
  return list.map(p=>{const pinned=m.pins[p.id]??!!p.pinned,heading=pinned!==prevPinned?`<div class="section-heading"><span>${pinned?'置顶对话':'最近对话'}</span><span>${pinned?'':list.filter(x=>!(m.pins[x.id]??!!x.pinned)).length+' 个'}</span></div>`:'';prevPinned=pinned;return heading+conversation(p);}).join('');
};
renderInbox = () => {
  const old=phone('inbox'),scroll=$('[data-inbox-scroll]',old)?.scrollTop||0;
  originalInbox();$('.focus-card',old)?.remove();
  $('.phone-header .header-spacer',old).after(element(`<button class="icon-button" data-action="saved-messages" aria-label="本地收藏的消息">${icon('pin')}</button>`));
  $('.filter-row',old).insertAdjacentHTML('beforeend',`<button class="filter-chip ${model().filter==='unread'?'active':''}" data-action="filter" data-id="unread" aria-pressed="${model().filter==='unread'}">未读</button>`);
  $('[data-inbox-scroll]',old).scrollTop=scroll;
};
messageHTML = (msg,id) => {
  let html=originalMessage(msg,id).replace(/<button class="reply-action"[\s\S]*?<\/button>/,'');
  html=html.replace(`data-message="${msg.id}"`,`data-message="${msg.id}" tabindex="0" role="group" aria-label="${esc((msg.me?'我':msg.name)+'：'+msg.text+'；长按或按 Shift 加 F10 打开消息操作')}"`);
  if(msg.quote){const source=msg.quote.id||getMessages(id).find(s=>s.id!==msg.id&&(s.text.includes(msg.quote.text)||msg.quote.text.includes(s.text)))?.id;if(source)html=html.replace('class="reply-quote"',`class="reply-quote" data-quote-source="${source}"`);}
  html=html.replace('</article>',`<span class="swipe-cue" aria-hidden="true">${icon('reply')}</span></article>`);
  const selected=model().selection[id]?.includes(msg.id);
  if(selected)html=html.replace('class="message ', 'class="message is-selected ');
  return html;
};
renderChat = (which,scroll=true) => {
  const root=phone(which),previousId=root.dataset.chatId,sc=$('.chat-scroll',root),position=sc?.scrollTop||0,active=$('textarea',root),hadFocus=active===document.activeElement,start=active?.selectionStart,end=active?.selectionEnd;
  if(previousId&&sc)model().positions[previousId]=position;
  originalChat(which,scroll);
  const id=currentId(which),p=person(id),m=model(),next=$('.chat-scroll',root);
  root.dataset.chatId=id;
  $('.phone-header > .icon-button:last-child',root).before(element(`<button type="button" class="icon-button" data-action="search-chat" aria-label="搜索当前会话">${icon('search')}</button>`));
  const quote=$('.context-strip',root);
  if(quote)quote.outerHTML=`<button class="context-strip group-announcement" data-action="announcement">${icon('announcement')}<strong>群公告</strong><span>友善交流，照片请注明拍摄地点</span>${icon('chevron')}</button>`;
  root.insertBefore(element(`<button class="latest-button glass" data-action="latest" aria-label="回到最新消息" hidden>${icon('back')}<span class="sr-only">最新消息</span></button>`),$('.composer-wrap',root));
  if(!scroll&&next)next.scrollTop=position;
  else if(previousId!==id&&m.positions[id]!=null&&next)next.scrollTop=m.positions[id];
  const search=m.searches[id];if(search?.open)mountSearch(which,search,false);
  if(m.selection[id]?.length)mountSelection(which);
  updateLatest(which);
  if(hadFocus){const input=$('textarea',root);input.focus({preventScroll:true});input.setSelectionRange(start,end);}
};
function element(html){const template=document.createElement('template');template.innerHTML=html;return template.content.firstElementChild;}
function updateLatest(which){const root=phone(which),sc=$('.chat-scroll',root),b=$('.latest-button',root);if(!sc||!b)return;b.hidden=sc.scrollHeight-sc.clientHeight-sc.scrollTop<90;b.style.bottom=($('.composer-wrap',root).offsetHeight+28)+'px';}
function replyTo(which,messageId){
  const id=currentId(which),msg=getMessages(id).find(s=>s.id===messageId);if(!msg)return;
  model().quotes[id]={id:msg.id,name:msg.me?'我':msg.name,text:msg.text};
  const root=phone(which),sc=$('.chat-scroll',root),atBottom=sc.scrollHeight-sc.clientHeight-sc.scrollTop<25,pos=sc.scrollTop;
  $('.composer-wrap',root).outerHTML=composer(which,id);
  if(atBottom)sc.scrollTop=sc.scrollHeight;else sc.scrollTop=pos;
  $('textarea',root).focus({preventScroll:true});updateLatest(which);
}
function locate(which,messageId){
  const root=phone(which),target=$(`[data-message="${CSS.escape(messageId)}"]`,root);
  if(!target){toast(which,'原消息尚未加载，请先查看更早消息');return;}
  target.scrollIntoView({behavior:reduceMotion()?'instant':'smooth',block:'center'});
  target.classList.add('message-located');setTimeout(()=>target.classList.remove('message-located'),1300);updateLatest(which);
}
function showMessageActions(which,messageId){
  const id=currentId(which),msg=getMessages(id).find(s=>s.id===messageId);if(!msg)return;
  openSheet(which,'消息操作',`<div class="menu-message"><span>${esc(msg.me?'我':msg.name)}</span><p>${esc(msg.text)}</p></div><div class="message-action-grid">${[['reply-message','reply','回复'],['copy-message','link','复制文字'],['save-message','pin',model().saved.some(s=>s.messageId===messageId&&s.chatId===id)?'取消本地收藏':'本地收藏'],['select-message','check','选取消息']].map(([action,glyph,label])=>`<button data-action="${action}" data-message-id="${messageId}">${icon(glyph)}<span>${label}</span></button>`).join('')}</div><p class="sheet-subtitle">只演示百度消息的操作方式；本地收藏不与云端同步。</p>`);
}
async function copyText(which,text){
  try{await navigator.clipboard.writeText(text);toast(which,'文字已复制');}catch{openSheet(which,'复制文字',`<textarea class="copy-fallback" readonly aria-label="待复制文字">${esc(text)}</textarea><p class="sheet-subtitle">浏览器未允许自动复制，请选中文字手动复制。</p>`);$('.copy-fallback',phone(which)).select();}
}
function mountSelection(which){
  const root=phone(which),ids=model().selection[currentId(which)]||[];
  $('.selection-bar',root)?.remove();
  if(!ids.length)return;
  root.insertBefore(element(`<div class="selection-bar glass"><button data-action="end-selection" aria-label="退出选取">${icon('close')}</button><strong>已选 ${ids.length} 条</strong><button data-action="copy-selection" aria-label="复制选取的消息">${icon('link')}</button></div>`),$('.chat-scroll',root));
  $$('.message',root).forEach(e=>{e.classList.toggle('is-selected',ids.includes(e.dataset.message));e.setAttribute('aria-label',e.getAttribute('aria-label').replace(/^已选取；/,'').replace(/^/,(ids.includes(e.dataset.message)?'已选取；':'')));});
}
function searchState(id){return model().searches[id] ||= {open:true,query:'',index:0,hits:[]};}
function mountSearch(which,s,focus=true){
  const root=phone(which);$('.chat-search',root)?.remove();
  $('.phone-header',root).after(element(`<div class="chat-search glass"><label>${icon('search')}<input type="search" value="${esc(s.query)}" placeholder="搜索当前已加载消息" aria-label="搜索当前会话内容"></label><div class="search-navigation"><span class="search-results" role="status"></span><button data-action="search-prev" aria-label="上一个搜索结果">↑</button><button data-action="search-next" aria-label="下一个搜索结果">↓</button><button data-action="close-search" aria-label="关闭会话搜索">${icon('close')}</button></div></div>`));
  highlightSearch(which,false);if(focus)$('.chat-search input',root).focus({preventScroll:true});
}
function highlightSearch(which,jump=true){
  const root=phone(which),s=searchState(currentId(which)),query=s.query.trim().toLocaleLowerCase();
  $$('mark',root).forEach(mark=>mark.replaceWith(document.createTextNode(mark.textContent)));
  s.hits=[];
  $$('.message',root).forEach(article=>{
    article.normalize();let matched=false;
    for(const body of $$('.message-bubble>div:first-of-type',article)){
      const text=body.textContent,at=query?text.toLocaleLowerCase().indexOf(query):-1;
      if(at>=0){body.replaceChildren(document.createTextNode(text.slice(0,at)),element('<mark>'+esc(text.slice(at,at+query.length))+'</mark>'),document.createTextNode(text.slice(at+query.length)));matched=true;}
    }
    if(matched)s.hits.push(article.dataset.message);
  });
  s.index=Math.min(Math.max(0,s.index),Math.max(0,s.hits.length-1));
  $('.search-results',root).textContent=query?(s.hits.length?`${s.index+1} / ${s.hits.length}`:'没有找到相关消息'):'搜索当前已加载内容';
  $$('[data-action=search-prev],[data-action=search-next]',root).forEach(b=>b.disabled=!s.hits.length);
  if(jump&&s.hits.length)locate(which,s.hits[s.index]);
}
details = (which,tab='info') => {
  const id=currentId(which),p=person(id),m=model(),messages=getMessages(id),group=p.type==='group';
  let body;
  if(tab==='images')body=`<div class="shared-gallery">${messages.filter(s=>s.kind==='image').map(s=>`<button data-action="shared-locate" data-message-id="${s.id}" aria-label="定位这张图片">${landscape()}<span>${esc(s.me?'我':s.name)} · ${esc(s.time)}</span></button>`).join('')||'<p class="sheet-subtitle">当前已加载消息中没有图片。</p>'}</div>`;
  else if(tab==='posts')body=messages.filter(s=>s.kind==='post').map(s=>`<button class="shared-post" data-action="shared-locate" data-message-id="${s.id}">${icon('forum')}<span><strong>北山漫步 / 周末出行记录</strong><small>${esc(s.me?'我':s.name)} · ${esc(s.time)} · 定位消息</small></span>${icon('chevron')}</button>`).join('')||'<p class="sheet-subtitle">当前已加载消息中没有分享的帖子。</p>';
  else body=(group?`<button class="sheet-row" data-action="announcement"><span>群公告</span>${icon('chevron')}</button><div class="member-grid">${[['北山',2],['阿青',1],['林间来信',0],['晚风',2]].map(([name,seed])=>`<button data-action="member" data-name="${name}">${avatar(seed)}<span>${name}</span></button>`).join('')}</div>`:`<div class="detail-section"><h4>来自共同的兴趣</h4><p>摄影吧 · 账号资料以百度返回为准</p></div>`)+`<button class="sheet-row" data-action="toggle-pin" role="switch" aria-checked="${m.pins[id]??!!p.pinned}"><span>置顶会话</span><span class="switch ${(m.pins[id]??!!p.pinned)?'on':''}" aria-hidden="true"></span></button><button class="sheet-row" data-action="toggle-mute" role="switch" aria-checked="${!!m.muted[id]}"><span>消息免打扰</span><span class="switch ${m.muted[id]?'on':''}" aria-hidden="true"></span></button>`;
  openSheet(which,group?'群资料':'会话资料',`<div class="sheet-profile">${avatar(p.seed,'large')}<div><h3>${p.name}</h3><p>${group?`${p.forum} · ${p.members} 位成员`:'贴吧私信'}</p></div></div><div class="shared-tabs">${[['info','资料'],['images','图片'],['posts','帖子']].map(([key,label])=>`<button data-action="details-tab" data-tab="${key}" class="${key===tab?'active':''}" aria-pressed="${key===tab}">${label}</button>`).join('')}</div>${body}<p class="sheet-subtitle">本地演示 · 共享内容仅整理当前已加载的百度消息，不增加新的云端数据源。</p>`);
};
function conversationOptions(p){
  const m=model();
  openSheet('inbox','管理会话',`<div class="sheet-profile">${avatar(p.seed)}<div><h3>${p.name}</h3><p>${p.type==='group'?p.forum:'贴吧私信'}</p></div></div>${[['list-pin','pin',(m.pins[p.id]??!!p.pinned)?'取消置顶':'置顶会话'],['list-mute','bell',m.muted[p.id]?'关闭免打扰':'开启免打扰'],['list-read','check','标为本地已读']].map(([action,glyph,text])=>`<button class="sheet-row" data-action="${action}" data-id="${p.id}">${icon(glyph)}<span>${text}</span></button>`).join('')}<p class="sheet-subtitle">演示开关仅保存在当前页面，未读演示不回写百度。</p>`);
}
send = (which,kind) => {originalSend(which,kind);renderInbox();};
// Capture new actions before the inherited generic click handler.
document.addEventListener('click',e=>{
  const b=e.target.closest('[data-action]'),article=e.target.closest('[data-message]'),root=e.target.closest('[data-phone]');if(!root)return;
  const which=root.dataset.phone,id=which==='inbox'?null:currentId(which),m=model(),a=b?.dataset.action;
  const handle=()=>{e.preventDefault();e.stopImmediatePropagation();};
  if(a==='open'){if(which!=='inbox')closeSheet();m.reads[b.dataset.id]=0;return;}
  if(a==='back'){m.positions[id]=$('.chat-scroll',root)?.scrollTop||0;renderInbox();return;}
  if(a==='all-chats'){handle();m.filter='all';renderInbox();return;}
  if(a==='saved-messages'){handle();openSheet('inbox','本地收藏的消息',m.saved.length?m.saved.map(s=>{const msg=getMessages(s.chatId).find(x=>x.id===s.messageId);return `<button class="saved-message" data-action="open-saved" data-id="${s.chatId}" data-message-id="${s.messageId}"><strong>${esc(person(s.chatId).name)}</strong><span>${esc(msg.text)}</span><small>点击定位原消息</small></button>`;}).join(''):'<p class="sheet-subtitle">还没有本地收藏。长按一条消息，选择「本地收藏」。</p>');return;}
  if(a==='open-saved'){handle();const p=person(b.dataset.id),messageId=b.dataset.messageId;closeSheet();m[p.type==='dm'?'dm':'group']=p.id;pane=p.type==='dm'?'dm':'group';renderInbox();renderChat(pane);updatePane();locate(pane,messageId);return;}
  if(a==='conversation-options'){handle();conversationOptions(person(b.dataset.id));return;}
  if(a?.startsWith('list-')){handle();const p=person(b.dataset.id);if(a==='list-pin')m.pins[p.id]=!(m.pins[p.id]??!!p.pinned);if(a==='list-mute')m.muted[p.id]=!m.muted[p.id];if(a==='list-read')m.reads[p.id]=0;closeSheet();renderInbox();return;}
  if(a==='details-tab'){handle();details(which,b.dataset.tab);return;}
  if(a==='reply-message'){handle();closeSheet();replyTo(which,b.dataset.messageId);return;}
  if(a==='copy-message'){handle();const msg=getMessages(id).find(s=>s.id===b.dataset.messageId);closeSheet();copyText(which,msg.text);return;}
  if(a==='save-message'){handle();const ix=m.saved.findIndex(s=>s.chatId===id&&s.messageId===b.dataset.messageId);if(ix>=0)m.saved.splice(ix,1);else m.saved.push({chatId:id,messageId:b.dataset.messageId});closeSheet();toast(which,ix>=0?'已取消本地收藏':'已加入本地演示收藏');return;}
  if(a==='select-message'){handle();m.selection[id]=[b.dataset.messageId];closeSheet();mountSelection(which);return;}
  if(a==='end-selection'){handle();m.selection[id]=[];$('.selection-bar',root)?.remove();$$('.message',root).forEach(x=>x.classList.remove('is-selected'));return;}
  if(a==='copy-selection'){handle();copyText(which,getMessages(id).filter(s=>m.selection[id].includes(s.id)).map(s=>(s.me?'我':s.name)+'：'+s.text).join('\n'));return;}
  if(article&&m.selection[id]?.length&&!a){handle();const arr=m.selection[id],at=arr.indexOf(article.dataset.message);if(at>=0)arr.splice(at,1);else arr.push(article.dataset.message);if(arr.length)mountSelection(which);else{ $('.selection-bar',root)?.remove();$$('.message',root).forEach(x=>x.classList.remove('is-selected'));}return;}
  if(b?.dataset.quoteSource){handle();locate(which,b.dataset.quoteSource);return;}
  if(a==='shared-locate'){handle();closeSheet();locate(which,b.dataset.messageId);return;}
  if(a==='latest'){handle();$('.chat-scroll',root).scrollTo({top:$('.chat-scroll',root).scrollHeight,behavior:reduceMotion()?'instant':'smooth'});return;}
  if(a==='search-chat'){handle();const s=searchState(id);s.open=true;mountSearch(which,s);return;}
  if(a==='close-search'){handle();m.searches[id].open=false;$('.chat-search',root).remove();$$('mark',root).forEach(x=>x.replaceWith(document.createTextNode(x.textContent)));updateLatest(which);return;}
  if(a==='search-next'||a==='search-prev'){handle();const s=searchState(id);s.index=(s.index+(a==='search-next'?1:-1)+s.hits.length)%s.hits.length;highlightSearch(which);return;}
},true);
document.addEventListener('input',e=>{
  if(e.target.matches('textarea.composer-input,.composer textarea')){
    const which=e.target.closest('[data-phone]').dataset.phone,id=currentId(which),preview=$(`[data-action=open][data-id="${id}"] .conversation-preview`,phone('inbox')),draft=model().drafts[id];
    if(preview){preview.textContent=draft?`草稿：${draft}`:person(id).preview;preview.classList.toggle('has-draft',!!draft);}return;
  }
  if(!e.target.matches('.chat-search input'))return;const which=e.target.closest('[data-phone]').dataset.phone,s=searchState(currentId(which));s.query=e.target.value;s.index=0;highlightSearch(which);
});
document.addEventListener('scroll',e=>{if(e.target.matches?.('.chat-scroll')){const which=e.target.closest('[data-phone]').dataset.phone;model().positions[currentId(which)]=e.target.scrollTop;updateLatest(which);}},true);
document.addEventListener('contextmenu',e=>{const row=e.target.closest('[data-message]');if(row&&!getSelection().toString()){e.preventDefault();showMessageActions(row.closest('[data-phone]').dataset.phone,row.dataset.message);}});
document.addEventListener('keydown',e=>{const row=e.target.closest('[data-message]');if(row&&(e.key==='ContextMenu'||e.key==='F10'&&e.shiftKey)){e.preventDefault();showMessageActions(row.closest('[data-phone]').dataset.phone,row.dataset.message);}});
// A compact, single-axis gesture: direct tracking; velocity-aware spring on release.
let gesture=null,suppressUntil=0;const springs=new WeakMap();
function stopSpring(row){const s=springs.get(row);if(s)cancelAnimationFrame(s.frame);springs.delete(row);}
function settle(row,x,velocity){
  stopSpring(row);if(reduceMotion()){row.style.transform='';row.classList.remove('is-swiping');return;}
  const state={x,v:Math.max(-1100,Math.min(1100,velocity)),time:performance.now(),frame:0};springs.set(row,state);
  const frame=t=>{const dt=Math.min(.025,(t-state.time)/1000);state.time=t;state.v+=(-470*state.x-43*state.v)*dt;state.x+=state.v*dt;row.style.transform=`translateX(${state.x}px)`;if(Math.abs(state.x)<.25&&Math.abs(state.v)<4){row.style.transform='';row.classList.remove('is-swiping');springs.delete(row);return;}state.frame=requestAnimationFrame(frame);};state.frame=requestAnimationFrame(frame);
}
function endGesture(cancel=false){
  if(!gesture)return;const g=gesture;gesture=null;clearTimeout(g.timer);
  if(g.locked){try{g.row.releasePointerCapture(g.pointer);}catch{}suppressUntil=performance.now()+450;const fire=!cancel&&(g.x<=-48||g.x<-16&&g.v< -450);settle(g.row,g.x,g.v);if(fire)replyTo(g.which,g.row.dataset.message);}
}
document.addEventListener('pointerdown',e=>{
  if(gesture){endGesture(true);return;}
  const row=e.target.closest('[data-message]');if(!row||e.target.closest('button,a,input,textarea')||e.button!==0||!e.isPrimary)return;
  const which=row.closest('[data-phone]').dataset.phone;if(model().selection[currentId(which)]?.length)return;
  const transform=getComputedStyle(row).transform,offset=transform==='none'?0:new DOMMatrixReadOnly(transform).m41;stopSpring(row);
  const g={row,which,pointer:e.pointerId,startX:e.clientX,startY:e.clientY,lastX:e.clientX,time:performance.now(),x:offset,initial:offset,v:0,locked:false};gesture=g;
  g.timer=setTimeout(()=>{if(gesture!==g)return;gesture=null;suppressUntil=performance.now()+450;showMessageActions(which,row.dataset.message);},430);
});
document.addEventListener('pointermove',e=>{
  const g=gesture;if(!g||g.pointer!==e.pointerId)return;const dx=e.clientX-g.startX,dy=e.clientY-g.startY;
  if(Math.hypot(dx,dy)>7)clearTimeout(g.timer);
  if(!g.locked){if(Math.abs(dy)>9&&Math.abs(dy)>Math.abs(dx)){endGesture(true);return;}if(dx< -9&&Math.abs(dx)>Math.abs(dy)*1.25){g.locked=true;g.row.setPointerCapture(g.pointer);g.row.classList.add('is-swiping');}else return;}
  e.preventDefault();const now=performance.now(),dt=Math.max(1,now-g.time);g.v=(e.clientX-g.lastX)/dt*1000;g.lastX=e.clientX;g.time=now;
  const travel=Math.min(0,dx+g.initial);g.x=travel< -64?-64+(travel+64)*.2:travel;g.row.style.transform=`translateX(${g.x}px)`;$('.swipe-cue',g.row).style.opacity=Math.min(1,Math.abs(g.x)/48);
},{passive:false});
document.addEventListener('pointerup',()=>endGesture());document.addEventListener('pointercancel',()=>endGesture(true));
document.addEventListener('click',e=>{if(performance.now()<suppressUntil&&e.target.closest('[data-message]')){e.preventDefault();e.stopImmediatePropagation();}},true);
const originalRender=render;
render=()=>{originalRender();$('#noteIndex').textContent='SELECTED / C';};
render();
