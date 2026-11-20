/* Standalone interaction study. No network, persistence or real account actions. */
'use strict';
const $ = (q, root = document) => root.querySelector(q);
const $$ = (q, root = document) => [...root.querySelectorAll(q)];
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths = {
  back:'<path d="m14 5-7 7 7 7"/>', close:'<path d="m6 6 12 12M6 18 18 6"/>',
  more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>', plus:'<path d="M12 5v14M5 12h14"/>',
  heart:'<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/>',
  reply:'<path d="m9 5-6 6 6 6M3 11h11c4 0 7 2 7 7"/>', at:'<circle cx="12" cy="12" r="4"/><path d="M16 8v6a2 2 0 0 0 4 0v-2a8 8 0 1 0-3 6"/>',
  home:'<path d="m3 11 9-8 9 8v9H4v-9M9 21v-8h6v8"/>', forum:'<rect x="3" y="4" width="18" height="16" rx="5"/><path d="M8 9h8M8 14h5"/>',
  chat:'<path d="M21 11a9 8 0 0 1-9 8H6l-4 3 1.7-6A8 8 0 0 1 3 11a9 8 0 0 1 18 0Z"/>',
  user:'<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
  send:'<path d="m4 13 16-9-7 17-2-8-7-1ZM11 13l9-9"/>', smile:'<circle cx="12" cy="12" r="9"/><path d="M8 14a4 4 0 0 0 8 0M8 9h.01M16 9h.01"/>',
  image:'<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 5-5 4 4 4-6 5 7"/>',
  link:'<path d="m10 14 4-4M8 16l-2 2a4 4 0 0 1-5-6l5-5a4 4 0 0 1 6 0M16 8l2-2a4 4 0 0 1 5 6l-5 5a4 4 0 0 1-6 0" transform="translate(1 0) scale(.9)"/>',
  pin:'<path d="m8 3 8 0-1 6 4 4H5l4-4-1-6ZM12 13v8"/>', chevron:'<path d="m9 5 7 7-7 7"/>',
  bell:'<path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5l-2 3ZM10 21h4"/>',
  people:'<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 5v2"/>',
  announcement:'<path d="m4 9 13-5v16L4 15V9ZM4 15l2 6h4l-2-5M20 9v6"/>',
  check:'<path d="m5 12 4 4L19 6"/>', refresh:'<path d="M20 7v5h-5M4 17v-5h5M5 7a8 8 0 0 1 13-2l2 3M4 16l2 3a8 8 0 0 0 13-2"/>',
  request:'<rect x="3" y="5" width="18" height="15" rx="4"/><path d="m3 7 9 7 9-7"/>', arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>'
};
const icon = (name, cls='') => `<svg viewBox="0 0 24 24" aria-hidden="true" class="${cls}">${paths[name] || paths.chat}</svg>`;
const palette = [['#d9e4ce','#576e40','#fcf8de'],['#e3d9cd','#786556','#eabf9c'],['#d2e1e6','#54738a','#eaf3ee'],['#e8d2c9','#9c6252','#f7ead1'],['#d9d2e5','#796384','#dce6f1'],['#cfded6','#567764','#ecdec3']];
function portrait(seed=0){
  const [bg, fg, skin] = palette[Math.abs(seed)%palette.length];
  return `<svg viewBox="0 0 64 64" aria-hidden="true"><rect width="64" height="64" fill="${bg}"/><circle cx="51" cy="10" r="17" fill="#ffffff38"/><path d="M6 67q0-24 26-24t26 24" fill="${fg}"/><ellipse cx="32" cy="28" rx="14" ry="17" fill="${skin}"/><path d="M17 29C10 7 42 1 47 24l-8-6-7 6-12-1-3 6Z" fill="${fg}"/><path d="M27 30h1m9 0h1" stroke="${fg}" stroke-width="2" stroke-linecap="round"/><path d="M29 37q4 3 7-1" stroke="${fg}" stroke-width="1.5" fill="none"/></svg>`;
}
function landscape(kind=0){
  const cols = kind%2 ? ['#ded8cc','#baad95','#748273','#566754'] : ['#d7e4e5','#b6cfcd','#769e95','#426958'];
  return `<svg viewBox="0 0 300 170" role="img" aria-label="本地演示山景插画"><rect width="300" height="170" fill="${cols[0]}"/><circle cx="225" cy="40" r="18" fill="#fff7df"/><path d="m0 113 76-89 65 89 62-62 97 82v37H0Z" fill="${cols[1]}"/><path d="m0 139 99-85 101 108 100-90v98H0Z" fill="${cols[2]}"/><path d="M0 142q89-29 166 3t134-4v29H0Z" fill="${cols[3]}"/><path d="m121 170 32-33 16 5-18 28" fill="#e6e7cc"/></svg>`;
}
const avatar = (seed, cls='') => `<span class="avatar ${cls}">${portrait(seed)}</span>`;
const contacts = [
  {id:'lin',name:'林间来信',seed:0,preview:'这张照片是在哪里拍的？',time:'14:32',unread:2,pinned:true,type:'dm'},
  {id:'xia',name:'小夏',seed:1,preview:'分享了一个帖子：我的周末徒步记录',time:'13:46',unread:0,type:'dm'},
  {id:'wind',name:'晚风',seed:2,preview:'谢谢，问题已经解决啦',time:'昨天',unread:0,type:'dm'},
  {id:'pixel',name:'像素蘑菇',seed:4,preview:'这套配色很舒服',time:'周五',unread:0,type:'dm'},
  {id:'mountain',name:'山野共拍',seed:5,forum:'摄影吧',members:128,preview:'北山：周末的路线整理好了',time:'14:29',unread:12,pinned:true,type:'group'},
  {id:'hydrogen',name:'氢友交流',seed:2,forum:'Android吧',members:86,preview:'阿青：这个细节我也注意到了',time:'14:16',unread:3,type:'group'},
  {id:'book',name:'书页之间',seed:3,forum:'读书吧',members:64,preview:'木木：分享一下最近读到的这段话',time:'昨天',unread:0,type:'group'}
];
const requestPerson = {id:'orange',name:'橘子海',seed:3,preview:'想请教一下照片的调色思路',time:'刚刚',unread:0,type:'dm'};
const person = id => contacts.find(p=>p.id===id) || (id==='orange'?requestPerson:contacts[0]);
const design = {
  a:{title:'熟悉、直接，不错过对话',body:'把私信和吧群按时间放在同一列表，用轻标签区分。对话沿用左右气泡，玻璃只包裹功能区，让消息更容易读。',tags:['统一时间线','置顶会话','轻量群公告'],captions:['通知在上，对话在下；一眼看到新的私信与群消息。','左右对话气泡，引用与分享保留清楚的上下文。','公告收在轻条中，群资料随用随开。']},
  b:{title:'给私信与吧群各自的空间',body:'私信保留常用联系人，吧群按所属吧归类。群内提供聊天与图片入口，配合分层输入卡片，适合经常参与多个社区的人。',tags:['私信 / 吧群双轨','按吧归类','分层输入卡片'],captions:['私信与吧群分区；切换「吧群」查看按吧归类的群列表。','上层写内容，下层放附件与发送，长消息也从容。','群身份更明确；聊天、图片、资料三个入口各司其职。']},
  c:{title:'让文字成为对话的主体',body:'用更紧凑的列表承接频繁对话。收到的消息直接呈现头像、姓名与正文，减少重复气泡；引用可展开上下文，群聊仍按时间排列。',tags:['紧凑会话','正文少包裹','引用上下文'],captions:['继续上次讨论放在前面，其他会话保持紧凑。','收到的文字直接排版，自己发送的消息用柔和底色区分。','上下文入口不占大块空间，长讨论更连贯。']}
};
function initialMessages(id){
  if(person(id).type==='group') return [
    {id:'g1',name:'北山',seed:2,text:'周末的路线整理好了。从北门进，沿溪流走到观景台，大约两小时。',time:'14:22',role:'群主'},
    {id:'g2',name:'阿青',seed:1,text:'上次去拍到的光线，给大家参考一下。',kind:'image',time:'14:24'},
    {id:'g3',me:true,text:'好呀，路线有没有帖子？我先收藏一下。',time:'14:26',status:'sent'},
    {id:'g4',name:'北山',seed:2,text:'有的，把出行记录放这里了。',kind:'post',time:'14:29',quote:{name:'我',text:'路线有没有帖子？'}}
  ];
  return [
    {id:'d1',name:person(id).name,seed:person(id).seed,text:'看到了你在摄影吧发的照片，光线好舒服。',time:'14:28'},
    {id:'d2',me:true,text:'谢谢！是昨天傍晚路过山脚时拍的。',time:'14:29',status:'sent'},
    {id:'d3',name:person(id).name,seed:person(id).seed,text:'是这一张吗？',kind:'image',time:'14:30'},
    {id:'d4',name:person(id).name,seed:person(id).seed,text:'这张照片是在哪里拍的？想周末也去走走。',time:'14:32'},
    {id:'d5',me:true,text:'北山公园，从东门进，沿着溪流走就能看到。',time:'14:33',status:'sent',quote:{name:person(id).name,text:'这张照片是在哪里拍的？'}}
  ];
}
const fresh = () => ({filter:'all',track:'dm',query:'',dm:'lin',group:'mountain',groupTab:'chat',drafts:{},quotes:{},messages:{},earlier:{},muted:{},pins:{},accepted:false});
let scheme = /^[abc]$/.test(location.hash.slice(1)) ? location.hash.slice(1) : document.body.dataset.startConcept || 'a';
let models = {a:fresh(),b:fresh(),c:fresh()}, demoState='normal', pane='inbox', sheetReturnFocus=null, idCounter=0;
const model = () => models[scheme];
const currentId = which => which==='dm' ? model().dm : model().group;
const getMessages = id => model().messages[id] ||= initialMessages(id);
const phone = which => $(`[data-phone="${which}"]`);
const statusBar = () => `<div class="status"><span>9:41</span><span class="status-right">5G <svg viewBox="0 0 40 15" aria-hidden="true"><path d="M2 12V9m4 3V6m4 6V3m4 9V1M20 4h14v8H20zM36 7v2"/></svg></span></div>`;
const button = (action, label, glyph='more', extra='') => `<button type="button" class="icon-button" data-action="${action}" aria-label="${label}" ${extra}>${icon(glyph)}</button>`;
const notices = () => `<div class="notice-row">${[['heart','点赞'],['reply','回复'],['at','@ 我']].map(([i,t])=>`<button class="notice-tile" data-action="notice" data-label="${t}">${icon(i)}${t}${i==='reply'?'<span class="dot"></span>':''}</button>`).join('')}</div>`;
const nav = () => `<nav class="bottom-nav glass" aria-label="应用主导航">${[['home','刷帖'],['forum','逛吧'],['plus','发帖'],['chat','消息'],['user','我的']].map(([i,t])=>`<button data-action="nav" data-label="${t}" class="${i==='chat'?'active':i==='plus'?'nav-plus':''}" aria-label="${t}" ${i==='chat'?'aria-current="page"':''}>${icon(i)}${i==='plus'?'':`<span>${t}</span>`}${i==='chat'?'<i class="nav-dot"></i>':''}</button>`).join('')}</nav><div class="home-indicator"></div>`;
function conversation(p, room=false){
  const pinned = model().pins[p.id] ?? !!p.pinned;
  return `<button class="${room?'room-card':'conversation'} ${pinned?'pinned':''} ${[model().dm,model().group].includes(p.id)?'selected':''}" data-action="open" data-id="${p.id}">${avatar(p.seed,p.type==='dm'?'round':'')}<span class="conversation-body"><span class="conversation-title"><strong>${p.name}</strong>${p.type==='group'&&!room?'<span class="group-tag">吧群</span>':''}</span><span class="conversation-preview">${p.preview}</span></span><span class="conversation-right"><span class="conversation-time">${p.time}</span>${p.unread?`<span class="unread">${p.unread}</span>`:pinned?`<span class="pin-mark">${icon('pin')}</span>`:''}</span></button>`;
}
function inboxResults(){
  const m=model();
  if(demoState==='empty') return empty('还没有会话','以后收到的私信和加入的吧群会出现在这里。','查看演示会话','restore');
  if(demoState==='error') return empty('会话暂时没有加载出来','这里会保留重试入口，不把加载失败当作空列表。','重新加载','restore','refresh');
  const query = m.query.trim().toLowerCase();
  let list = [...contacts,...(m.accepted?[requestPerson]:[])].filter(p=>(m.filter==='all'||p.type===m.filter)&&(scheme!=='b'||p.type===m.track)&&`${p.name}${p.forum||''}${p.preview}`.toLowerCase().includes(query));
  if(!list.length) return empty('没有找到相关会话','换个昵称或吧名试试。','清除搜索','clear-search','search');
  if(scheme==='b'&&m.track==='group') return list.map(p=>`<div class="forum-label">${avatar(p.seed)}${p.forum}<span class="member-count">· 1 个群</span></div>${conversation(p,true)}`).join('');
  if(scheme==='a'&&!query&&m.filter==='all') list=[...(m.accepted?[requestPerson]:[]),list[0],list[4],list[5],list[1],list[2],list[3],list[6]].filter(Boolean);
  return `<div class="section-heading"><span>${scheme==='c'?'全部对话':scheme==='b'?'最近私信':'最近会话'}</span><span>本地演示</span></div>${list.map(p=>conversation(p)).join('')}`;
}
function empty(title,body,label,action,glyph='chat'){
  return `<div class="empty-state"><div class="empty-icon">${icon(glyph)}</div><h3>${title}</h3><p>${body}</p><button data-action="${action}">${label}</button></div>`;
}
function renderInbox(){
  const m=model();
  phone('inbox').innerHTML=statusBar()+`<header class="phone-header"><h2>${scheme==='c'?'对话':'消息'}</h2><span class="header-spacer"></span>${button('new','新建私信','plus')}</header>${notices()}`+
    (scheme==='b'?`<div class="track-tabs" aria-label="会话分类">${[['dm','私信'],['group','吧群']].map(([id,t])=>`<button data-action="track" data-id="${id}" class="${m.track===id?'active':''}" aria-pressed="${m.track===id}">${t}${id==='group'?' <span class="bubble-count">3</span>':''}</button>`).join('')}</div>`:'')+
    `<label class="search-field">${icon('search')}<input type="search" aria-label="搜索会话" placeholder="${scheme==='b'&&m.track==='group'?'搜索群名或所属吧':'搜索昵称、群名或消息'}" value="${esc(m.query)}"></label>`+
    (scheme!=='b'?`<div class="filter-row">${[['all','全部'],['dm','私信'],['group','吧群']].map(([id,t])=>`<button class="filter-chip ${m.filter===id?'active':''}" data-action="filter" data-id="${id}" aria-pressed="${m.filter===id}">${t}</button>`).join('')}</div>`:'')+
    `<div class="scroll-area" data-inbox-scroll>${scheme==='b'&&m.track==='dm'&&demoState==='normal'?`<div class="section-heading"><span>常用联系人</span></div><div class="people-rail">${contacts.filter(p=>p.type==='dm').map(p=>`<button data-action="open" data-id="${p.id}">${avatar(p.seed,'round')}<span>${p.name}</span></button>`).join('')}</div>`:''}${scheme==='c'&&demoState==='normal'?`<button class="focus-card" data-action="open" data-id="mountain"><span class="focus-top">继续上次讨论 <span>摄影吧 · 14:29</span></span><span class="focus-person">${avatar(5)}<strong>山野共拍</strong><span class="focus-arrow">${icon('arrow')}</span></span><p>北山：周末的路线整理好了，一起去吗？</p></button>`:''}<div data-results>${inboxResults()}</div>${demoState==='normal'?`<button class="request-entry" data-action="requests">${icon('request')}<span><strong>陌生人消息</strong><small>${m.accepted?'已接受此演示会话':'新的对话，先看看再决定'}</small></span><span class="unread">${m.accepted?'0':'1'}</span></button>`:''}</div>${nav()}`;
}
function messageHTML(msg,id){
  const text=esc(msg.text);
  return `<article class="message ${msg.me?'me':''}" data-message="${msg.id}">${msg.me?'':avatar(msg.seed)}<div class="message-body">${msg.me?'':`<div class="message-name">${esc(msg.name)}${msg.role?`<span class="role-tag">${esc(msg.role)}</span>`:''}</div>`}<div class="message-bubble">${msg.quote?`<button class="reply-quote" data-action="context" data-name="${esc(msg.quote.name)}" data-text="${esc(msg.quote.text)}"><strong>${esc(msg.quote.name)}</strong><span>${esc(msg.quote.text)}</span></button>`:''}<div>${text}</div>${msg.kind==='image'?`<button class="picture-message" data-action="picture" aria-label="查看演示图片">${landscape()}</button>`:msg.kind==='post'?`<button class="post-share" data-action="post"><span class="post-cover">${landscape(1)}</span><strong>北山漫步 / 周末出行记录</strong><small>摄影吧 · 帖子分享</small></button>`:''}</div><div class="message-time"><span>${esc(msg.time)}</span>${msg.me?`<span class="${msg.status==='failed'?'send-failed':''}">${msg.status==='sending'?'发送中…':msg.status==='failed'?'发送失败':'已发送 · 演示'}</span>${msg.status==='failed'?`<button class="retry-send" data-action="retry" data-message-id="${msg.id}">重试</button>`:''}`:''}<button class="reply-action" data-action="quote" data-message-id="${msg.id}" aria-label="引用${msg.me?'我的':esc(msg.name)+'的'}消息">${icon('reply')}</button></div></div></article>`;
}
function composer(which,id){
  const m=model(), quote=m.quotes[id], draft=m.drafts[id]||'';
  const quoteBlock=quote?`<div class="composer-quote"><div><strong>回复 ${esc(quote.name)}</strong><p>${esc(quote.text)}</p></div><button data-action="cancel-quote" aria-label="取消引用">${icon('close')}</button></div>`:'';
  const input=`<textarea rows="1" aria-label="${which==='dm'?'输入私信':'输入群消息'}" placeholder="${which==='dm'?'写一条私信…':'一起聊聊…'}" maxlength="4000">${esc(draft)}</textarea>`;
  const send=`<button class="send-button" type="submit" aria-label="发送演示消息" ${draft.trim()?'':'disabled'}>${scheme==='b'?'发送':''}${icon('send')}</button>`;
  return `<div class="composer-wrap">${quoteBlock}<form class="composer glass" data-compose="${which}">${scheme==='b'?`${input}<div class="composer-tools">${button('attachments','添加附件','plus')}${button('emoji','添加表情','smile')}${button('share-post','分享演示帖子','link')}${send}</div>`:`${button('attachments','添加附件','plus')}${input}${button('emoji','添加表情','smile')}${send}`}</form><div class="composer-fineprint">本地交互演示 · 不会真实发送</div></div>`;
}
function renderChat(which, scroll=true){
  const id=currentId(which), p=person(id), m=model(), group=which==='group';
  const title=scheme==='b'&&group?'吧群':p.name;
  const subtitle=group?`${p.forum} · ${p.members} 位成员`:scheme==='c'?'私信 · 查看资料':'贴吧私信 · 查看资料';
  let head=`<header class="phone-header">${button('back','返回会话列表','back')}<span class="chat-avatar">${portrait(p.seed)}</span><button class="chat-heading" data-action="details"><strong>${title}</strong><small>${subtitle}</small></button>${button('details',group?'查看群资料':'查看会话资料','more')}</header>`;
  if(group&&scheme==='b') head+=`<div class="group-hero">${avatar(p.seed)}<div class="hero-copy"><h3>${p.name}</h3><p>${p.forum} · ${p.members} 位成员</p></div><button data-action="details" aria-label="群成员">${icon('people')}</button></div><div class="group-tabs">${[['chat','聊天'],['images','图片'],['info','群资料']].map(([k,t])=>`<button data-action="group-tab" data-id="${k}" class="${m.groupTab===k?'active':''}" aria-pressed="${m.groupTab===k}">${t}</button>`).join('')}</div>`;
  if(group&&scheme==='a') head+=`<button class="announcement" data-action="announcement">${icon('announcement')}<span><b>群公告</b>　友善交流，照片请注明拍摄地点</span>${icon('chevron','chevron')}</button>`;
  if(group&&scheme==='c') head+=`<button class="context-strip" data-action="context" data-name="北山" data-text="周末北山徒步，集合地点与路线见群公告。">${icon('reply')}<strong>正在讨论</strong><span>周末徒步 · 查看上下文</span>${icon('chevron')}</button>`;
  let body;
  if(group&&scheme==='b'&&m.groupTab==='images') body=`<div class="scroll-area"><div class="gallery">${[0,1,2,3].map(i=>`<button data-action="picture">${landscape(i)}<p>群内图片 · 本地演示</p></button>`).join('')}</div></div>`;
  else if(group&&scheme==='b'&&m.groupTab==='info') body=`<div class="scroll-area"><div class="group-room-summary"><h3>${p.name}</h3><p>来自 ${p.forum} 的同好交流空间。<br>这里保留群身份与公告，不用聊天消息承载所有导航。</p><button class="sheet-row" data-action="announcement"><span>群公告</span>${icon('chevron')}</button><button class="sheet-row" data-action="details"><span>成员与群设置</span>${icon('chevron')}</button></div></div>`;
  else body=`${demoState==='error'?'<div class="error-banner"><span>连接中断，已加载的消息仍可阅读</span><button data-action="restore">重试</button></div>':''}<div class="scroll-area chat-scroll"><div class="chat-content">${demoState==='empty'?empty(group?'群聊还很安静':'从一句问候开始',group?'这里会按时间显示新的群消息。':'发送第一条消息，开启你们的对话。','载入演示消息','restore'): `<button class="earlier-button" data-action="earlier">${m.earlier[id]?'更早消息已加载':'查看更早消息'}</button><div class="day-stamp">今天 · 14:28</div>${getMessages(id).map(msg=>messageHTML(msg,id)).join('')}`}</div></div>`;
  phone(which).innerHTML=statusBar()+head+body+composer(which,id)+'<div class="home-indicator"></div>';
  if(scroll) scrollBottom(which);
}
function scrollBottom(which){ const sc=$('.chat-scroll',phone(which));if(sc) sc.scrollTop=sc.scrollHeight; }
function updatePane(){ $('#screens').dataset.activePane=pane;$$('[data-pane]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.pane===pane))); }
function render(){
  $('#screens').dataset.concept=scheme;
  $$('[data-concept].concept').forEach(b=>{b.classList.toggle('selected',b.dataset.concept===scheme);b.setAttribute('aria-pressed',String(b.dataset.concept===scheme));});
  const d=design[scheme];$('#noteIndex').textContent=`0${'abc'.indexOf(scheme)+1} / 03`;$('#noteTitle').textContent=d.title;$('#noteBody').textContent=d.body;$('#noteTags').innerHTML=d.tags.map(t=>`<span>${t}</span>`).join('');
  ['Inbox','Dm','Group'].forEach((s,i)=>$('#caption'+s).textContent=d.captions[i]);
  renderInbox();renderChat('dm');renderChat('group');updatePane();
}
function toast(which,text){const root=phone(which);$('.toast',root)?.remove();const el=document.createElement('div');el.className='toast glass';el.role='status';el.textContent=text;root.append(el);setTimeout(()=>el.remove(),2400);}
function closeSheet(){const active=$('.overlay');if(!active)return;active.remove();if(sheetReturnFocus?.isConnected)sheetReturnFocus.focus();}
function openSheet(which,title,content){
  closeSheet();sheetReturnFocus=document.activeElement;
  const overlay=document.createElement('div');overlay.className='overlay';overlay.innerHTML=`<section class="sheet" role="dialog" aria-modal="true" aria-label="${esc(title)}" tabindex="-1"><div class="sheet-handle"></div><div class="sheet-header"><h3>${esc(title)}</h3>${button('close-sheet','关闭','close')}</div>${content}</section>`;
  overlay.addEventListener('click',e=>{if(e.target===overlay)closeSheet();});
  phone(which).append(overlay);$('.sheet',overlay).focus();
}
function details(which){
  const p=person(currentId(which)),m=model();
  const profile=`<div class="sheet-profile">${avatar(p.seed,'large')}<div><h3>${p.name}</h3><p>${p.type==='group'?`${p.forum} · ${p.members} 位成员`:'贴吧账号 · 私信资料'}</p></div></div>`;
  openSheet(which,p.type==='group'?'群资料':'会话资料',profile+(p.type==='group'?`<button class="sheet-row" data-action="announcement"><span>群公告</span>${icon('chevron')}</button><div class="member-grid">${['北山','阿青','林间来信','晚风'].map((name,i)=>`<button data-action="member" data-name="${name}">${avatar(i)}<span>${name}</span></button>`).join('')}</div>`:`<div class="detail-section"><h4>来自共同的兴趣</h4><p>摄影吧 · 这里展示接口可提供的账号资料。</p></div>`)+
    `<button class="sheet-row" data-action="toggle-pin"><span>置顶会话</span><span class="switch ${(m.pins[p.id]??!!p.pinned)?'on':''}" role="switch" aria-checked="${m.pins[p.id]??!!p.pinned}"></span></button><button class="sheet-row" data-action="toggle-mute"><span>消息免打扰</span><span class="switch ${m.muted[p.id]?'on':''}" role="switch" aria-checked="${!!m.muted[p.id]}"></span></button><p class="sheet-subtitle">头像、人数与开关均用于设计演示；真实客户端以百度接口支持为准。</p>`);
}
function send(which,kind){
  const m=model(),id=currentId(which),draft=(m.drafts[id]||'').trim();
  if(!draft&&!kind)return;
  const msg={id:'local-'+(++idCounter),me:true,text:draft||(kind==='image'?'分享一张照片':'分享一个帖子'),kind,time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}),status:'sending',quote:m.quotes[id]};
  if(demoState==='empty') {demoState='normal';$('#stateSelect').value='normal';m.messages[id]=[];renderInbox();}
  getMessages(id).push(msg);m.drafts[id]='';delete m.quotes[id];renderChat(which);if(!kind)$('textarea',phone(which))?.focus();
  const failed=demoState==='error';
  setTimeout(()=>{msg.status=failed?'failed':'sent';if(model()===m&&currentId(which)===id)renderChat(which);},650);
}
document.addEventListener('input',e=>{
  const root=e.target.closest('[data-phone]');if(!root)return;
  const which=root.dataset.phone;
  if(e.target.matches('input[type=search]')){model().query=e.target.value;$('[data-results]',root).innerHTML=inboxResults();}
  if(e.target.matches('textarea')){model().drafts[currentId(which)]=e.target.value;$('.send-button',root).disabled=!e.target.value.trim();e.target.style.height='auto';e.target.style.height=Math.min(e.target.scrollHeight,94)+'px';}
});
document.addEventListener('submit',e=>{const form=e.target.closest('[data-compose]');if(form){e.preventDefault();send(form.dataset.compose);}});
document.addEventListener('click',e=>{
  const concept=e.target.closest('button.concept');if(concept){scheme=concept.dataset.concept;history.replaceState(null,'','#'+scheme);render();return;}
  const paneButton=e.target.closest('[data-pane]');if(paneButton){pane=paneButton.dataset.pane;updatePane();return;}
  const b=e.target.closest('[data-action]');if(!b)return;
  const which=b.closest('[data-phone]')?.dataset.phone||'inbox',a=b.dataset.action,m=model(),id=which==='inbox'?null:currentId(which);
  if(a==='close-sheet'){closeSheet();return;}
  if(a==='open'){const p=person(b.dataset.id);m[p.type==='dm'?'dm':'group']=p.id;if(p.type==='group')m.groupTab='chat';pane=p.type==='dm'?'dm':'group';renderInbox();renderChat(pane);updatePane();return;}
  if(a==='back'){pane='inbox';updatePane();toast(which,'已返回会话列表');return;}
  if(a==='filter'||a==='track'){m[a]=b.dataset.id;renderInbox();return;}
  if(a==='restore'){demoState='normal';$('#stateSelect').value='normal';render();toast(which,'演示数据已加载');return;}
  if(a==='clear-search'){m.query='';renderInbox();return;}
  if(a==='nav'){toast('inbox',b.dataset.label==='消息'?'当前正在预览消息页':`${b.dataset.label}属于其他页面，本稿预览消息功能`);return;}
  if(a==='details'){details(which);return;}
  if(a==='announcement'){openSheet(which,'群公告','<div class="detail-section"><h4>北山 · 11 月 14 日</h4><p>欢迎来到山野共拍！分享照片时，请尽量注明拍摄地点。讨论友善，尊重不同观点。周末徒步请量力而行，具体集合安排在群内确认。</p></div><p class="sheet-subtitle">本地演示公告 · 实装时读取百度返回内容。</p>');return;}
  if(a==='group-tab'){m.groupTab=b.dataset.id;renderChat('group');return;}
  if(a==='toggle-pin'||a==='toggle-mute'){const store=a==='toggle-pin'?m.pins:m.muted;store[id]=!(store[id]??(a==='toggle-pin'&&!!person(id).pinned));details(which);renderInbox();return;}
  if(a==='quote'){const msg=getMessages(id).find(x=>x.id===b.dataset.messageId);m.quotes[id]={name:msg.me?'我':msg.name,text:msg.text};renderChat(which,false);$('textarea',phone(which)).focus();return;}
  if(a==='cancel-quote'){delete m.quotes[id];renderChat(which,false);$('textarea',phone(which)).focus();return;}
  if(a==='context'){openSheet(which,'相关对话',`<div class="detail-section"><h4>${esc(b.dataset.name)}</h4><p>${esc(b.dataset.text)}</p></div><p class="sheet-subtitle">引用消息展开后，保留当前会话的位置。</p>`);return;}
  if(a==='earlier'){if(m.earlier[id]){toast(which,'已展示这份设计稿的全部演示消息');return;}const sc=$('.chat-scroll',phone(which)),height=sc.scrollHeight;getMessages(id).unshift({id:'old-'+id,name:which==='group'?'北山':person(id).name,seed:2,text:'昨天聊到的路线，今天终于整理好啦。',time:'昨天 19:48'});m.earlier[id]=true;renderChat(which,false);const next=$('.chat-scroll',phone(which));next.scrollTop=next.scrollHeight-height;return;}
  if(a==='retry'){const msg=getMessages(id).find(x=>x.id===b.dataset.messageId);msg.status='sending';renderChat(which);setTimeout(()=>{msg.status='sent';if(m===model()&&currentId(which)===id){renderChat(which);toast(which,'重试成功 · 仅本地演示');}},550);return;}
  if(a==='emoji'){openSheet(which,'添加表情',`<div class="emoji-grid">${['🙂','😊','👍','✨','🌿','📷','❤️','🎉','🤔','👀','🙌','☀️','😄','🥹','🌈','🍃','💬','👌'].map(t=>`<button data-action="insert-emoji" data-value="${t}" aria-label="插入 ${t}">${t}</button>`).join('')}</div><p class="sheet-subtitle">布局示例；原生版本使用贴吧表情资源。</p>`);return;}
  if(a==='insert-emoji'){m.drafts[id]=(m.drafts[id]||'')+b.dataset.value;closeSheet();renderChat(which,false);$('textarea',phone(which)).focus();return;}
  if(a==='attachments'){openSheet(which,'添加到对话',`<div class="attachment-grid"><button data-action="send-image">${icon('image')}演示图片</button><button data-action="share-post">${icon('link')}演示帖子</button><button data-action="emoji">${icon('smile')}表情</button></div><p class="sheet-subtitle">这里仅插入本地示例，不访问照片、不上传文件。</p>`);return;}
  if(a==='send-image'||a==='share-post'){closeSheet();send(which,a==='send-image'?'image':'post');return;}
  if(a==='picture'){openSheet(which,'图片预览',`<div class="picture-preview">${landscape()}</div><p class="sheet-subtitle">本地矢量示例图 · 展示图片在聊天中的查看入口。</p>`);return;}
  if(a==='post'){openSheet(which,'分享的帖子',`<div class="picture-preview">${landscape(1)}</div><div class="detail-section"><h4>摄影吧 · 本地帖子示例</h4><p>北山漫步 / 周末出行记录</p><p>从东门沿溪流慢慢走，傍晚的光线正好。</p></div>`);return;}
  if(a==='new'){openSheet('inbox','新建私信',`<p class="sheet-subtitle">选择一位演示联系人，开始对话。</p>${contacts.filter(p=>p.type==='dm').map(p=>`<button class="new-contact" data-action="open" data-id="${p.id}">${avatar(p.seed,'round')}<span>${p.name}</span></button>`).join('')}`);return;}
  if(a==='requests'){openSheet('inbox','陌生人消息',`<div class="sheet-profile">${avatar(3)}<div><h3>橘子海</h3><p>来自摄影吧 · 本地演示</p></div></div><div class="detail-section"><p>你好！想请教一下照片的调色思路，方便交流吗？</p></div><button class="sheet-row" data-action="accept"><span>${m.accepted?'查看已接受的演示会话':'接受并查看对话'}</span>${icon('chevron')}</button><p class="sheet-subtitle">接受仅改变本地稿状态，不发送真实回复。</p>`);return;}
  if(a==='accept'){m.accepted=true;m.dm='orange';m.messages.orange ||= [{id:'request-orange',name:'橘子海',seed:3,text:'你好！想请教一下照片的调色思路，方便交流吗？',time:'刚刚'}];closeSheet();pane='dm';renderInbox();renderChat('dm');updatePane();toast('dm','已接受 · 示例会话已打开');return;}
  if(a==='member'){openSheet(which,'成员资料',`<div class="sheet-profile">${avatar(1)}<div><h3>${esc(b.dataset.name)}</h3><p>摄影吧 · 群成员</p></div></div><p class="sheet-subtitle">成员资料以百度接口实际可提供的内容为准。</p><button class="sheet-row" data-action="open" data-id="lin"><span>查看私信布局</span>${icon('chevron')}</button>`);return;}
  if(a==='notice'){openSheet('inbox',b.dataset.label,`<div class="detail-section"><h4>今天 · 本地通知示例</h4><p>林间来信${b.dataset.label==='点赞'?'赞了你的帖子':b.dataset.label==='回复'?'回复我：这张照片的光线真好。':'在讨论中提到了你。'}</p></div><p class="sheet-subtitle">保留现有消息通知入口，本稿重点展示私信和吧群。</p>`);}
});
$('#themeToggle').addEventListener('click',e=>{const on=document.body.classList.toggle('dark');e.currentTarget.setAttribute('aria-pressed',String(on));e.currentTarget.textContent=on?'浅色':'深色';});
$('#fontToggle').addEventListener('click',e=>{const on=document.body.classList.toggle('large-type');e.currentTarget.setAttribute('aria-pressed',String(on));});
$('#stateSelect').addEventListener('change',e=>{demoState=e.target.value;render();});
$('#reset').addEventListener('click',()=>{models={a:fresh(),b:fresh(),c:fresh()};demoState='normal';pane='inbox';$('#stateSelect').value='normal';render();});
document.addEventListener('keydown',e=>{
  if(e.key==='Escape')closeSheet();
  if(e.key==='Enter'&&(e.ctrlKey||e.metaKey)&&e.target.matches('textarea')){e.preventDefault();send(e.target.closest('[data-phone]').dataset.phone);}
  const overlay=$('.overlay');if(e.key==='Tab'&&overlay){const list=$$('button:not([disabled]),input,textarea,[tabindex="0"]',overlay),first=list[0],last=list.at(-1);if(e.shiftKey&&(document.activeElement===first||document.activeElement===$('.sheet',overlay))){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}
});
window.addEventListener('hashchange',()=>{const c=location.hash.slice(1);if(/^[abc]$/.test(c)){scheme=c;render();}});
render();
