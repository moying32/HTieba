'use strict';
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
const escapeHTML = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths = {
 home:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
 compass:'<circle cx="12" cy="12" r="9"/><path d="m16 8-2.5 5.5L8 16l2.5-5.5z"/>',
 plus:'<path d="M12 4v16M4 12h16"/>',
 message:'<path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-1 1v-8.5a9 9 0 1 1 18-1z"/><path d="M7 9h10M7 13h7"/>',
 user:'<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
 search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
 arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>',
 chevron:'<path d="m9 5 7 7-7 7"/>',
 back:'<path d="m14 5-7 7 7 7M7 12h14"/>',
 heart:'<path d="M20.5 5.5c-2.5-2.5-6-1.5-8.5 1-2.5-2.5-6-3.5-8.5-1C-.5 10 4 15 12 21c8-6 12.5-11 8.5-15.5z"/>',
 reply:'<path d="M21 11a8 8 0 0 1-8 8H7l-5 3V11a9 9 0 0 1 19 0z"/>',
 bookmark:'<path d="M5 3h14v18l-7-4-7 4z"/>',
 share:'<path d="M12 16V3m-5 5 5-5 5 5M5 12H3v9h18v-9h-2"/>',
 more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
 filter:'<path d="M4 7h16M7 12h10M10 17h4"/><circle cx="8" cy="7" r="2" fill="currentColor" stroke="none"/>',
 forum:'<rect x="3" y="4" width="18" height="16" rx="5"/><path d="M8 9h8M8 13h5"/>',
 check:'<path d="m5 12 4 4L20 5"/>',
 checkin:'<rect x="3" y="5" width="18" height="16" rx="4"/><path d="M7 3v4m10-4v4M7 14l3 3 6-6"/>',
 ranking:'<path d="M4 20V10h5v10m1 0V4h5v16m1 0v-7h5v7M2 21h21"/>',
 grid:'<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
 at:'<circle cx="11" cy="12" r="4"/><path d="M15 8v7c0 4 7 2 7-3a10 10 0 1 0-4 8"/>',
 settings:'<path d="m9 3-1 3-3 1-2 3 2 3v4l3 1 2 3h4l2-3 3-1v-4l2-3-2-3-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
 history:'<path d="M3 10a9 9 0 1 1 1 7M3 4v6h6M12 7v6l4 2"/>',
 edit:'<path d="M13 5 19 11M4 20l4-1L21 6l-5-5L3 14z"/>',
 image:'<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 6-6 4 4 3-3 5 5"/>',
 video:'<rect x="2" y="5" width="14" height="14" rx="3"/><path d="m16 10 6-4v12l-6-4"/>',
 poll:'<path d="M4 4v17h17M9 16v-5m5 5V7m5 9v-3"/>',
 smile:'<circle cx="12" cy="12" r="9"/><path d="M7 14q5 6 10 0M8 8h.01M16 8h.01"/>',
 close:'<path d="m6 6 12 12M6 18 18 6"/>',
 down:'<path d="m5 9 7 7 7-7"/>',
 send:'<path d="m3 3 19 9-19 9 4-9zM7 12h15"/>',
 shield:'<path d="m12 2 9 4v7c-2 5-5 8-9 10-4-2-7-5-9-10V6z"/><path d="m8 12 3 3 5-6"/>',
 bell:'<path d="M4 17h16l-2-4V9a6 6 0 0 0-12 0v4zM9 21h6"/>',
 eye:'<path d="M2 12q10-16 20 0-10 16-20 0z"/><circle cx="12" cy="12" r="3"/>',
 logout:'<path d="M10 3H3v18h7M8 12h14m-5-5 5 5-5 5"/>',
 info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>',
 reset:'<path d="M3 10a9 9 0 1 1 1 7M3 4v6h6"/>',
 gesture:'<path d="M9 13V5a2 2 0 0 1 4 0v7-2a2 2 0 0 1 4 0v2a2 2 0 0 1 4 0v5l-5 5H9l-6-8a2 2 0 0 1 3-2l3 3M3 3v5m-2-2 2 2 2-2"/>',
 cloud:'<path d="M6 19a5 5 0 0 1-1-10 7 7 0 0 1 14-1 5.5 5.5 0 0 1-1 11M12 12v10m-3-3 3 3 3-3"/>',
 leaf:'<path d="M4 20C-3 9 5 2 21 3c0 14-6 20-15 15M4 20 15 9"/>',
 lock:'<rect x="4" y="10" width="16" height="12" rx="3"/><path d="M7 10V6a5 5 0 0 1 10 0v4M12 15v3"/>',
 link:'<path d="m10 14 4-4M8 16l-2 2a4 4 0 0 1-6-6l6-6a4 4 0 0 1 6 0m0 2 2-2a4 4 0 0 1 6 6l-6 6a4 4 0 0 1-6 0"/>',
 moon:'<path d="M20 14A9 9 0 0 1 10 3a9 9 0 1 0 10 11z"/>',
 warning:'<path d="m12 2 11 19H1zM12 9v5M12 17h.01"/>',
 trash:'<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>'
};
const icon = (name, cls = '') => `<svg class="icon ${cls}" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.forum}</svg>`;
function hydrateIcons(root = document) { $$('[data-icon]', root).forEach(el => { el.innerHTML = icon(el.dataset.icon); el.removeAttribute('data-icon'); }); }
const phone = $('#phone'), scroller = $('#scroll-view'), content = $('#page-content'), header = $('#app-header'), dock = $('#dock'), detailBar = $('#detail-bar'), overlayRoot = $('#overlay-root');
const basePosts = [
 {id:1,author:'山野来信',avatar:'山',tone:'dark',time:'12 分钟前',forum:'摄影',title:'把周末还给山野，才发现生活可以这么慢。',text:'没有赶路，也没有计划。在湖边坐了一下午，风吹过来的时候，忽然觉得什么都刚刚好。',body:['这周终于关掉闹钟，去了一趟一直想去的山里。','沿着湖边走了很久，没有什么特别要完成的事。风经过水面，远处的山被云遮住一半，坐下来就不想走了。','有时候，周末不一定要安排得很满。给自己留一点空白，才有机会听见生活原本的声音。','你们最近有什么想去的地方吗？一起在楼里分享吧。'],image:'landscape.svg',comments:128,likes:326,views:'1.2 万'},
 {id:2,author:'今天也想早点睡',avatar:'眠',tone:'sand',time:'28 分钟前',forum:'生活',title:'有什么你坚持了很久，后来发现真的有用的小习惯？',text:'我先来：睡前把手机放在够不到的地方。坚持两周之后，终于不再熬到凌晨两点了。',body:['不是那种需要很强意志力的改变，是每天多做一点点就能坚持的小事。','我先来：睡前把手机放在够不到的地方。坚持两周之后，终于不再熬到凌晨两点了。','想听听大家的小习惯，慢慢试着把生活过好。'],comments:86,likes:204,views:'8,163'},
 {id:3,author:'像素漫游',avatar:'P',tone:'blue',time:'42 分钟前',forum:'Minecraft',title:'生存档第 100 天，给自己盖了一间林中小屋',text:'不追求效率的一天。用云杉木搭了屋顶，在门口种了花，等一个像素世界的日落。',body:['这个存档已经玩了 100 天了。没有着急打龙，也没做很大的机器，就是慢慢把喜欢的地方盖好。','这次终于做完了门前的小路。欢迎分享你们的生存基地。'],comments:57,likes:189,views:'6,802'},
 {id:4,author:'一页空白',avatar:'页',tone:'lavender',time:'1 小时前',forum:'设计',title:'少一点界面，多一点内容。你理想中的社区是什么样的？',text:'能安静阅读，也能随时加入一场有意思的讨论。信息和人都在，界面刚刚好。',body:['社区最吸引我的始终是人，是那些偶然遇见的真诚讨论。','希望工具可以退后一步，让有价值的内容被看见，也让回复变得自然。','你们最在意社区的哪一个细节？'],comments:43,likes:96,views:'4,276'},
 {id:5,author:'候鸟停靠站',avatar:'鸟',tone:'rose',time:'2 小时前',forum:'旅行',title:'不赶景点，在一座陌生城市里散步',text:'走进一条没有攻略的小巷，买到一杯好喝的咖啡。旅行的惊喜，可能就藏在这些小事里。',body:['这次旅行只买了来回的车票。剩下的时间，留给随便走走。','在一家小店坐到了日落，老板聊起他养的猫。这也许就是我喜欢旅行的原因。'],comments:32,likes:156,views:'3,921'},
 {id:6,author:'早起看云',avatar:'云',tone:'blue',time:'3 小时前',forum:'摄影',title:'阴天也有自己的颜色',text:'试着把饱和度放低，原来灰色也能很温柔。分享一下这周的练习，欢迎交流。',body:['以前总想等到晴天再出门。最近发现，阴天的光线也有很细腻的层次。','所有颜色都安静下来之后，画面里的人反而更清晰了。'],comments:21,likes:72,views:'2,106'}
];
const forums = [
 {name:'摄影',letter:'光',tone:'dark',members:'388 万',description:'把日常，拍成值得记住的瞬间。'},
 {name:'Minecraft',letter:'M',tone:'',members:'276 万',description:'在方块之间，建造自己的世界。'},
 {name:'生活',letter:'生',tone:'sand',members:'182 万',description:'生活里的小事，都值得被认真听见。'},
 {name:'设计',letter:'形',tone:'blue',members:'96 万',description:'关于审美、灵感和好用的设计。'},
 {name:'旅行',letter:'远',tone:'rose',members:'245 万',description:'下一站，去遇见不一样的风景。'},
 {name:'数码',letter:'数',tone:'blue',members:'521 万',description:'聊聊好产品，也聊聊真实体验。'},
 {name:'读书',letter:'书',tone:'sand',members:'158 万',description:'一页一页，去看更大的世界。'},
 {name:'音乐',letter:'音',tone:'',members:'362 万',description:'总有一首歌，刚好唱进心里。'}
];
const readStore = () => { try { return JSON.parse(localStorage.getItem('hydrogen-design-v1') || '{}'); } catch { return {}; } };
const stored = readStore();
const state = {
 page:'feed',feedTab:'推荐',forumTab:'全部',forum:'摄影',post:1,search:'',searchTab:'综合',list:'收藏',notification:'回复',chat:'林间晚风',person:'山野来信',
 likes:stored.likes || [],saved:stored.saved || [],following:stored.following || ['摄影','Minecraft','生活','设计'],followingPeople:stored.followingPeople || ['山野来信','像素漫游'],history:stored.history || [],searchHistory:stored.searchHistory || ['摄影','Minecraft','周末去哪儿'],
 settings:{theme:'light',glass:true,large:false,reduced:false,autoHide:true,notifications:true,stranger:true,videoWifi:true,...stored.settings},
 posts:stored.posts || [],draft:stored.draft || {forum:'摄影',title:'',body:''},comments:stored.comments || {},chats:stored.chats || {},profile:stored.profile || {name:'Moying32',bio:'保持好奇，记录日常。'},
 checked:false,feedState:'normal',feedLimit:5,onlyOP:false,sortNewest:false,unread:true,replyLikes:[],profileTab:'帖子'
};
let stack = [], modal = null, lastFocus = null, overlayToken = 0, toastTimer, priorScroll = 0, scrollDelta = 0, mediaFiles = [], poll = null, composeVideo = false;
let motionSystem = matchMedia('(prefers-reduced-motion: reduce)'), colorSystem = matchMedia('(prefers-color-scheme: dark)');
const allPosts = () => [...state.posts, ...basePosts];
const currentPost = () => allPosts().find(p => p.id === Number(state.post)) || basePosts[0];
const persist = () => { try { localStorage.setItem('hydrogen-design-v1', JSON.stringify({likes:state.likes,saved:state.saved,following:state.following,followingPeople:state.followingPeople,history:state.history,searchHistory:state.searchHistory,settings:state.settings,posts:state.posts.map(p=>({...p,image:p.image?.startsWith('blob:')?undefined:p.image})),draft:state.draft,comments:state.comments,chats:state.chats,profile:state.profile})); } catch { toast('本地存储不可用，当前状态保留在本次预览中'); } };
const iconButton = (name, action, label, extra = '') => `<button type="button" class="icon-button" data-action="${action}" aria-label="${escapeHTML(label)}" ${extra}>${icon(name)}</button>`;
const avatar = (p, extra = '') => `<span class="avatar ${p.tone || ''} ${extra}">${escapeHTML(p.avatar || p.author?.slice(0,1) || '氢')}</span>`;
const simpleHeader = (title, right = '') => `<div class="topbar"><div class="back-title">${iconButton('back','back','返回')}<h1 class="page-heading">${escapeHTML(title)}</h1></div>${right}</div>`;
const tabs = (items, active, action) => `<div class="tabs" role="tablist">${items.map(v=>`<button class="tab ${v === active ? 'active' : ''}" role="tab" aria-selected="${v === active}" data-action="${action}" data-value="${v}">${v}</button>`).join('')}</div>`;
function toast(message) { clearTimeout(toastTimer); const t = $('#toast'); t.textContent = message; t.classList.add('show'); toastTimer = setTimeout(()=>t.classList.remove('show'), 2600); }
function setSettings() {
 const s=state.settings, theme=s.theme==='system'?(colorSystem.matches?'dark':'light'):s.theme;
 phone.dataset.theme=theme; phone.classList.toggle('solid',!s.glass); phone.classList.toggle('large-text',s.large); phone.classList.toggle('reduced-motion',s.reduced||motionSystem.matches);
 const seed=/^[0-9a-f]{6}$/i.test(s.accent||'')?s.accent:'326A54';
 const rgb=seed.match(/../g).map(v=>parseInt(v,16));
 const lum=values=>values.map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;}).reduce((v,c,i)=>v+c*[.2126,.7152,.0722][i],0);
 const paper=theme==='dark'?[22,30,25]:[249,250,246];
 const ratio=v=>(Math.max(lum(v),lum(paper))+.05)/(Math.min(lum(v),lum(paper))+.05);
 let tone=rgb;
 if(ratio(tone)<4.5){const target=theme==='dark'?255:0;let lo=0,hi=1;for(let i=0;i<16;i++){const mid=(lo+hi)/2;if(ratio(rgb.map(v=>v+(target-v)*mid))>=4.5)hi=mid;else lo=mid;}tone=rgb.map(v=>Math.round(v+(target-v)*hi));}
 phone.style.setProperty('--accent',`rgb(${tone.join(',')})`);
 phone.style.setProperty('--accent-soft',`rgb(${paper.map((v,i)=>Math.round(v*.88+rgb[i]*.12)).join(',')})`);
 $$('[data-setting]').forEach(el=>el.checked=Boolean(s[el.dataset.setting]));
 $$('[data-action=theme]').forEach(el=>{const on=el.dataset.value===s.theme;el.classList.toggle('selected',on);el.setAttribute('aria-pressed',on);});
 $('meta[name="theme-color"]').content = innerWidth <= 620 ? (theme==='dark'?'#161e19':'#f9faf6') : '#e9ece6';
}
function updateDock() {
 const index={feed:0,forums:1,messages:3,profile:4}[state.page];
 dock.hidden=index===undefined; detailBar.hidden=state.page!=='detail';
 $$('#dock [data-page]').forEach(btn=>{if(btn.dataset.page===state.page)btn.setAttribute('aria-current','page');else btn.removeAttribute('aria-current');});
 if(index!==undefined)$('#dock-lens').style.transform=`translateX(${index * 100}%)`;
 $('#nav-unread').hidden=!state.unread; dock.classList.remove('dock-hidden');
}
function captureRoute() { return {page:state.page,feedTab:state.feedTab,forum:state.forum,forumTab:state.forumTab,post:state.post,search:state.search,searchTab:state.searchTab,list:state.list,notification:state.notification,chat:state.chat,person:state.person,scroll:scroller.scrollTop}; }
function navigate(page, args={}, topLevel=false) {
 if(modal==='compose'){readDraft();persist();}
 closeOverlay(true);
 if(topLevel)stack=[];else stack.push(captureRoute());
 Object.assign(state,args,{page});
 if(page==='messages')state.unread=false;
 if(page==='detail'){state.history=[state.post,...state.history.filter(id=>id!==state.post)].slice(0,30);persist();}
 render(); scroller.scrollTop=0; priorScroll=0; scrollDelta=0;
}
function back() {
 if(modal){closeOverlay();return;}
 const prev=stack.pop();if(!prev){navigate('feed',{},true);return;}
 Object.assign(state,prev);render();scroller.scrollTop=prev.scroll;priorScroll=prev.scroll;
}
function rerenderKeepScroll() { const y=scroller.scrollTop;render();scroller.scrollTop=y;priorScroll=y; }
function postCard(p) {
 const like=state.likes.includes(p.id),save=state.saved.includes(p.id);
 return `<article class="post" data-post-id="${p.id}"><div class="post-meta"><button class="author-button" data-action="person" data-value="${escapeHTML(p.author)}" aria-label="查看${escapeHTML(p.author)}的主页">${avatar(p)}</button><button class="author-button" data-action="person" data-value="${escapeHTML(p.author)}"><span class="author-name">${escapeHTML(p.author)}</span><span class="author-sub">${escapeHTML(p.time)}</span></button><button class="post-forum" data-action="forum" data-value="${escapeHTML(p.forum)}">${icon('forum')}${escapeHTML(p.forum)}吧</button><button class="more-button" data-action="post-menu" data-id="${p.id}" aria-label="更多帖子操作">${icon('more')}</button></div><button class="post-main" data-action="detail" data-id="${p.id}"><h2 class="post-title">${escapeHTML(p.title)}</h2><p class="post-excerpt">${escapeHTML(p.text)}</p>${p.image?`<div class="media-wrap"><img class="post-image" src="${escapeHTML(p.image)}" alt="远山、湖泊和树林构成的山野插画" loading="lazy"><span class="image-count">${icon('image').replace('class="icon','class="icon tiny')} 原创示意</span></div>`:''}</button><div class="post-actions"><button class="post-action" data-action="detail" data-id="${p.id}" aria-label="查看 ${p.comments} 条回复">${icon('reply')}<span>${p.comments+(state.comments[p.id]?.length||0)}</span></button><button class="post-action like ${like?'is-on':''}" data-action="like" data-id="${p.id}" aria-pressed="${like}" aria-label="${like?'取消点赞':'点赞'}">${icon('heart')}<span>${p.likes+(like?1:0)}</span></button><button class="post-action ${save?'is-on':''}" data-action="save" data-id="${p.id}" aria-pressed="${save}" aria-label="${save?'取消收藏':'收藏'}">${icon('bookmark')}</button><button class="post-action" data-action="share" data-id="${p.id}" aria-label="分享帖子">${icon('share')}</button></div></article>`;
}
function emptyState(type, title, text, action, button) { return `<div class="empty-state"><div class="empty-orb">${icon(type)}</div><h3>${title}</h3><p>${text}</p>${action?`<button class="primary-button" data-action="${action}">${button}</button>`:''}</div>`; }
function feedHTML() {
 if(state.feedState==='loading')return `${[1,2,3].map(()=>'<div class="skeleton-post" role="status" aria-label="正在加载帖子"><div class="skeleton skeleton-avatar"></div><div class="skeleton" style="width:87%"></div><div class="skeleton" style="width:66%"></div><div class="skeleton skeleton-image"></div></div>').join('')}`;
 if(state.feedState==='empty')return emptyState('leaf','这里还很安静','去逛逛感兴趣的吧，新的讨论会从这里开始。','discover','发现感兴趣的吧');
 if(state.feedState==='error')return emptyState('cloud','暂时没能加载出来','网络似乎走神了。已加载的内容会为你保留，再试一次吧。','retry','重新加载');
 let posts=allPosts();
 if(state.feedTab==='关注')posts=posts.filter(p=>state.followingPeople.includes(p.author));
 if(state.feedTab==='热榜')posts=[...posts].sort((a,b)=>b.comments-a.comments);
 if(!posts.length)return emptyState('user','从一次关注开始','关注喜欢的创作者，在这里看到他们的最新动态。','discover','去发现');
 return `<div class="feed-kicker"><span><span class="live-dot"></span>${state.feedTab==='推荐'?'发现值得停留的讨论':state.feedTab==='关注'?'你关注的人，新的日常':'此刻，大家正在聊'}</span><span>${state.feedTab==='热榜'?'每小时更新':'为你精选'}</span></div>${posts.slice(0,state.feedLimit).map(postCard).join('')}${posts.length>state.feedLimit?'<button class="load-more" data-action="load-more">继续浏览</button>':'<div class="list-end">已经看到这里了，去发现新的讨论吧</div>'}`;
}
function forumTile(f){return `<span class="forum-tile ${f.tone}">${escapeHTML(f.letter)}</span>`;}
function forumItem(f){const on=state.following.includes(f.name);return `<div class="forum-list-item"><button data-action="forum" data-value="${escapeHTML(f.name)}" aria-label="进入${escapeHTML(f.name)}吧">${forumTile(f)}</button><button class="forum-info author-button" data-action="forum" data-value="${escapeHTML(f.name)}"><h3>${escapeHTML(f.name)}吧</h3><p>${f.members} 吧友 · ${escapeHTML(f.description)}</p></button><button class="follow-button ${on?'followed':''}" data-action="follow-forum" data-value="${escapeHTML(f.name)}" aria-pressed="${on}">${on?'已关注':'+ 关注'}</button></div>`;}
function forumsHTML(){return `<button class="search-launch" data-action="forum-search">${icon('search')}搜索你感兴趣的吧<kbd>发现</kbd></button><section class="section"><h2 class="section-title">我关注的吧 <button class="text-button" data-action="my-forums">全部 ${state.following.length} ${icon('chevron').replace('class="icon','class="icon tiny')}</button></h2><div class="forum-grid">${state.following.slice(0,8).map(name=>forums.find(f=>f.name===name)).filter(Boolean).map(f=>`<button data-action="forum" data-value="${f.name}">${forumTile(f)}<span>${f.name}</span></button>`).join('')||'<p class="section-description">关注后，你的吧会出现在这里</p>'}</div></section><div class="quick-tools"><button data-action="checkin">${icon(state.checked?'check':'checkin')}${state.checked?'今日已签到':'一键签到'}</button><button data-action="forum-ranking">${icon('ranking')}吧排行榜</button><button data-action="forum-categories">${icon('grid')}全部分类</button></div><div class="divider-space"></div><section class="section"><h2 class="section-title">也许你会喜欢 <button class="text-button" data-action="shuffle-forums">换一换 ${icon('reset').replace('class="icon','class="icon tiny')}</button></h2><p class="section-description">找到同频的人，从一个吧开始。</p><div id="recommended-forums">${forums.slice(4).map(forumItem).join('')}</div></section>`;}
function forumHTML(){const f=forums.find(f=>f.name===state.forum)||forums[0],on=state.following.includes(f.name);let posts=allPosts().filter(p=>p.forum===f.name);if(state.forumTab==='最新')posts=[...posts].reverse();if(state.forumTab==='精华')posts=posts.filter(p=>p.likes>200);return `<div class="forum-cover"><div class="forum-cover-head">${forumTile(f)}<div><h2>${f.name}吧</h2><p>关注 ${f.members} · 帖子 12.6 万</p></div><button class="follow-button ${on?'followed':''}" data-action="follow-forum" data-value="${f.name}" aria-pressed="${on}">${on?'已关注':'+ 关注'}</button></div><p>${f.description}</p></div><button class="notice" style="width:100%;text-align:left" data-action="forum-notice">${icon('bell')}吧规与公告 · 欢迎分享，也请善待每一次交流 ${icon('chevron')}</button><div class="forum-tabs" role="tablist">${['全部','最新','精华'].map(v=>`<button class="tab ${state.forumTab===v?'active':''}" role="tab" aria-selected="${state.forumTab===v}" data-action="forum-tab" data-value="${v}">${v}</button>`).join('')}<button class="text-button" style="margin-left:auto" data-action="checkin">${state.checked?'已签到':'签到'}</button></div>${posts.length?posts.map(postCard).join(''):emptyState('forum','还没有符合条件的帖子','这个分类正在等待新的讨论。','compose','发起讨论')}<button class="load-more" data-action="compose" data-forum="${f.name}">在 ${f.name}吧 发帖</button>`;}
function messagesHTML(){const chats=[['林间晚风','风','','关注','刚刚','你上次分享的那个地方，我也想去看看。'],['摄影交流小组','光','dark','群聊','12:30','山野来信：周末有人一起去拍日落吗？'],['像素漫游','P','blue','关注','昨天','小屋又添了一扇窗，来看看！'],['未关注的消息','陌','sand','陌生人','昨天','有 2 位新朋友想和你聊聊']];return `<div class="message-shortcuts">${[['heart','点赞','',3],['reply','回复','green',2],['at','@我','blue',1]].map(([i,t,c,n])=>`<button data-action="notifications" data-value="${t}"><span class="message-symbol ${c}">${icon(i)}<i class="unread-badge">${n}</i></span>${t}</button>`).join('')}</div><div class="subsection-heading"><span>聊天</span><button class="text-button" data-action="new-chat">${icon('edit').replace('class="icon','class="icon tiny')} 发起聊天</button></div>${chats.map(([name,av,tone,type,time,preview])=>`<button class="conversation" data-action="chat" data-value="${name}"><span class="avatar ${tone}">${av}</span><span class="conversation-copy"><span class="conversation-name"><span>${name}<i class="mini-label">${type}</i></span><small>${time}</small></span><p>${escapeHTML(state.chats[name]?.at(-1)?.text||preview)}</p></span>${name==='林间晚风'?'<span class="conversation-dot"></span>':''}</button>`).join('')}<div class="list-end">消息留给人，打扰留在外面</div>`;}
function menuRow(i,label,action,small='',extra=''){return `<button class="menu-row" data-action="${action}" ${extra}>${icon(i)}<span>${label}</span>${small?`<small>${small}</small>`:''}${icon('chevron','arrow-icon')}</button>`;}
function profileHTML(){return `<div class="profile-cover"></div><section class="profile-info"><span class="avatar">M</span><button class="profile-edit" data-action="edit-profile">编辑资料</button><h2>${escapeHTML(state.profile.name)}</h2><p class="profile-id">@moying32 · 贴吧的第 1,286 天</p><p>${escapeHTML(state.profile.bio)}</p><div class="profile-stats"><button data-action="list" data-value="我的帖子">${state.posts.length}<small>帖子</small></button><button data-action="relationships" data-value="关注">${state.followingPeople.length}<small>关注</small></button><button data-action="relationships" data-value="粉丝">128<small>粉丝</small></button></div></section><div class="collection-grid">${[['bookmark','收藏',state.saved.length],['heart','点赞',state.likes.length],['history','浏览历史',state.history.length]].map(([i,t,n])=>`<button data-action="list" data-value="${t}">${icon(i)}<span>${t}</span><small>${n} 条</small></button>`).join('')}</div><div class="menu-group" style="margin-top:16px">${menuRow('forum','我关注的吧','my-forums',`${state.following.length} 个`)}${menuRow('edit','我的草稿','drafts',state.draft.title||state.draft.body?'1 条草稿':'随时记录')}${menuRow('message','我的回复','list','', 'data-value="我的回复"')}</div><div class="divider-space"></div><div class="menu-group">${menuRow('settings','设置','settings')}${menuRow('info','关于氢贴吧','about')}</div><div class="about-signature" style="padding-top:20px">轻一点，离讨论近一点。</div>`;}
function settingsHTML(){const row=(label,small,key)=>`<label class="setting-row"><span>${label}<small>${small}</small></span><input class="setting-toggle" type="checkbox" role="switch" data-setting="${key}" ${state.settings[key]?'checked':''}></label>`;return `<div class="setting-section-label">外观与阅读</div><label class="setting-row"><span>外观主题<small>让阅读适应此刻的光线</small></span><select data-setting-select="theme" aria-label="外观主题">${[['system','跟随系统'],['light','浅色'],['dark','深色']].map(([v,l])=>`<option value="${v}" ${state.settings.theme===v?'selected':''}>${l}</option>`).join('')}</select></label><label class="setting-row"><span>自定义主题色<small>任意色值，自动适配文字对比度</small></span><input type="color" aria-label="自定义主题色" data-accent value="#${state.settings.accent||'326A54'}" style="width:58px;min-height:48px;border:0;background:none"></label>${row('液态玻璃','局部折射、透光与边缘高光','glass')}${row('大字号','放大帖子正文与评论','large')}${row('减少动态效果','减弱位移，保留直接的状态反馈','reduced')}${row('浏览时收起导航','向下浏览隐藏，反向滚动恢复','autoHide')}<div class="setting-section-label">消息与隐私</div>${row('消息通知','回复、@ 与赞的消息提醒','notifications')}${row('接收陌生人消息','未关注的消息单独收纳','stranger')}<div class="menu-group">${menuRow('eye','屏蔽与内容偏好','preferences')}${menuRow('shield','账号与安全','account')}</div><div class="setting-section-label">通用</div>${row('仅 Wi-Fi 自动播放视频','节省移动网络流量','videoWifi')}<div class="menu-group">${menuRow('trash','清理缓存','clear-cache','24.8 MB（示例）')}${menuRow('info','关于软件','about')}</div><div class="about-signature">仅供学习交流，禁止商用。<span class="signature">@Bilibili Moying32</span></div>`;}
function listHTML(){let posts=[];if(state.list==='收藏')posts=allPosts().filter(p=>state.saved.includes(p.id));else if(state.list==='点赞')posts=allPosts().filter(p=>state.likes.includes(p.id));else if(state.list==='浏览历史')posts=state.history.map(id=>allPosts().find(p=>p.id===id)).filter(Boolean);else if(state.list==='我的帖子')posts=state.posts;else if(state.list==='我的回复')return Object.entries(state.comments).flatMap(([id,cs])=>cs.map(c=>`<div class="notification"><span class="avatar">M</span><div class="notification-text">${escapeHTML(c.text)}<small>刚刚 · 我的回复</small><button class="quoted" data-action="detail" data-id="${id}">${escapeHTML(allPosts().find(p=>p.id===Number(id))?.title||'查看原帖')}</button></div></div>`)).join('')||emptyState('reply','讨论，等你加入','在帖子下留下回复，会在这里找到。','go-feed','去刷帖');return posts.length?`<div class="activity-count">${posts.length} 条${state.list==='收藏'?'收藏 · 本地设计稿演示':''}</div>${posts.map(postCard).join('')}`:emptyState(state.list==='收藏'?'bookmark':state.list==='点赞'?'heart':state.list==='我的帖子'?'edit':'history',state.list==='收藏'?'收藏喜欢的，留给以后':state.list==='我的帖子'?'第一篇帖子，从这里开始':'这里还没有记录',state.list==='收藏'?'点一下帖子下方的收藏按钮，把值得重读的内容留下来。':'去看看大家正在讨论什么，或者分享你的日常。',state.list==='我的帖子'?'compose':'go-feed',state.list==='我的帖子'?'写一篇帖子':'去刷帖');}
function searchHTML(){if(!state.search)return `<section class="section"><h2 class="section-title">最近搜索 ${iconButton('trash','clear-search','清空搜索历史')}</h2><div class="search-chips">${state.searchHistory.map(t=>`<button class="chip" data-action="search-term" data-value="${escapeHTML(t)}">${escapeHTML(t)}</button>`).join('')||'<p class="section-description">搜索过的内容会留在这里</p>'}</div></section><section class="section" style="padding-top:3px"><h2 class="section-title">此刻热议 <span class="small-label">HOT SEARCH</span></h2>${['你有哪些坚持很久的小习惯','秋天最后一场徒步','Minecraft 生存建筑','拍下你窗外的天空','最近读完的一本好书','安卓桌面分享'].map((t,i)=>`<button class="hot-row" data-action="search-term" data-value="${t}"><span>${i+1}</span><span>${t}</span><small>${[326,218,186,154,132,96][i]} 万</small></button>`).join('')}</section>`;
 const q=state.search.toLowerCase(),posts=allPosts().filter(p=>[p.title,p.text,p.author,p.forum].some(s=>s.toLowerCase().includes(q))),matchedForums=forums.filter(f=>f.name.toLowerCase().includes(q)),people=[...new Set(allPosts().map(p=>p.author))].filter(n=>n.toLowerCase().includes(q));
 let html='';if(['综合','吧'].includes(state.searchTab)&&matchedForums.length)html+=`<section class="section"><h2 class="section-title">找到这些吧</h2>${matchedForums.map(forumItem).join('')}</section>`;if(['综合','帖子'].includes(state.searchTab)&&posts.length)html+=posts.map(postCard).join('');if(['综合','用户'].includes(state.searchTab)&&people.length)html+=people.map(n=>`<button class="conversation" data-action="person" data-value="${escapeHTML(n)}">${avatar(allPosts().find(p=>p.author===n))}<span class="conversation-copy"><span class="conversation-name">${escapeHTML(n)}</span><p>查看个人主页与最新帖子</p></span>${icon('chevron')}</button>`).join('');return html||emptyState('search','暂时没有找到相关内容','试试“摄影”“生活”或“Minecraft”，设计稿只包含少量示例数据。','clear-query','换个关键词');}
const commentSeeds=[{id:'a',author:'林间晚风',avatar:'风',tone:'',floor:'2 楼',text:'光是看着就觉得心情安静下来了。比起打卡，还是这样慢慢走更适合我。',likes:28,nested:true},{id:'b',author:'今天也想早点睡',avatar:'眠',tone:'sand',floor:'3 楼',text:'我也想找一个这样的地方，放空一整天。请问是在哪里呀？',likes:16},{id:'c',author:'山野来信',avatar:'山',tone:'dark',floor:'4 楼 · 楼主',text:'是在一次周末徒步路上发现的湖。路线很轻松，下次整理成攻略分享给大家。',likes:12},{id:'d',author:'像素漫游',avatar:'P',tone:'blue',floor:'5 楼',text:'已经开始想在游戏里还原这个地方了。山和湖的颜色真的很舒服。',likes:9}];
function commentHTML(c,inNested=false){return `<div class="comment">${avatar(c)}<div class="comment-main"><div class="comment-head"><button data-action="person" data-value="${escapeHTML(c.author)}">${escapeHTML(c.author)}</button><small>${escapeHTML(c.floor)}</small></div><p class="comment-body">${escapeHTML(c.text)}</p><div class="comment-foot"><span>刚刚 · 广东</span><button data-action="${inNested?'nested-reply':'reply'}" data-value="${escapeHTML(c.author)}">回复</button><button style="margin-left:auto" data-action="comment-like" data-id="${c.id}" aria-pressed="${state.replyLikes.includes(c.id)}">${icon('heart')} ${c.likes+(state.replyLikes.includes(c.id)?1:0)}</button></div>${c.nested?'<button class="nested-preview" data-action="nested"><strong>山野来信：</strong>是呀，什么都不安排也很好。<br><span>查看 3 条楼中楼回复 ›</span></button>':''}</div></div>`;}
function detailHTML(){const p=currentPost();let comments=[...(p.id<100?commentSeeds:[]),...(state.comments[p.id]||[]).map((c,i)=>({...c,author:state.profile.name,avatar:'M',floor:`${i+(p.id<100?6:2)} 楼`,likes:0}))];if(state.onlyOP)comments=comments.filter(c=>c.author===p.author);if(state.sortNewest)comments.reverse();return `<article class="detail-post"><div class="post-meta">${avatar(p)}<button class="author-button" data-action="person" data-value="${escapeHTML(p.author)}"><span class="author-name">${escapeHTML(p.author)}</span><span class="author-sub">${escapeHTML(p.time)} · ${escapeHTML(p.forum)}吧</span></button><button class="follow-button ${state.followingPeople.includes(p.author)?'followed':''}" style="margin-left:auto" data-action="follow-person" data-value="${escapeHTML(p.author)}" aria-pressed="${state.followingPeople.includes(p.author)}">${state.followingPeople.includes(p.author)?'已关注':'+ 关注'}</button></div><h1 class="detail-title">${escapeHTML(p.title)}</h1><div class="detail-body">${(p.body||[p.text]).slice(0,2).map(t=>`<p>${escapeHTML(t)}</p>`).join('')}${p.image?`<button class="post-main" data-action="image" data-value="${escapeHTML(p.image)}"><img class="post-image" src="${escapeHTML(p.image)}" alt="查看山野插画大图"></button><div class="detail-caption">山野来信 / 原创插画示意 · 点击查看大图</div>`:''}${(p.body||[]).slice(2).map(t=>`<p>${escapeHTML(t)}</p>`).join('')}${p.poll?`<div class="poll-block"><div>一起投个票</div>${p.poll.map((t,i)=>`<button class="menu-row" data-action="vote" data-value="${i}">${escapeHTML(t)}<small>选择</small></button>`).join('')}</div>`:''}</div><p class="detail-meta">${p.views||'1'} 次浏览 · 仅展示示例内容</p></article><div class="comments-heading"><span>全部回复 <span style="color:var(--muted);font-weight:400">${p.comments+(state.comments[p.id]?.length||0)}</span></span><button data-action="sort-comments">${state.sortNewest?'最新优先':'热门优先'} ${icon('down').replace('class="icon','class="icon tiny')}</button></div>${comments.map(commentHTML).join('')}<div class="list-end">${state.onlyOP?'已开启只看楼主':'以上为楼层与楼中楼的示例内容'}</div>`;}
function notificationsHTML(){const type=state.notification;return `<div class="notice">${icon('info')}这里是${type}通知的交互示例</div>${[0,1,2].map((_,i)=>{const p=basePosts[i];return `<div class="notification">${avatar(p)}<div class="notification-text"><button class="author-button" data-action="person" data-value="${p.author}"><strong>${p.author}</strong></button> ${type==='点赞'?'赞了你的帖子':type==='@我'?'在讨论中提到了你':'回复了你的帖子'}<small>${[5,26,48][i]} 分钟前</small>${type!=='点赞'?`<p style="margin-bottom:9px">${type==='@我'?'@Moying32 这个讨论你应该会喜欢。':'我也有一样的感受，感谢分享！'}</p>`:''}<button class="quoted" data-action="detail" data-id="${p.id}">${escapeHTML(p.title)}</button></div></div>`;}).join('')}`;}
function chatHTML(){const group=state.chat==='摄影交流小组',initial=group?'周末有人一起去拍日落吗？想去湖边走走，顺便试试新镜头。':state.chat==='未关注的消息'?'你好，看到了你在摄影吧的分享，想问一下拍摄地点。':'你上次分享的那个地方，我也想去看看。';return `<div class="chat-area"><div class="chat-date">今天 12:30 · ${group?'群聊':'本地聊天示例'}</div><div class="chat-bubble">${initial}</div><div class="chat-bubble me">可以呀！有空一起去走走。</div>${(state.chats[state.chat]||[]).map(c=>`<div class="chat-bubble ${c.me?'me':''}">${escapeHTML(c.text)}</div>`).join('')}</div>`;}
function personHTML(){const p=allPosts().find(p=>p.author===state.person)||basePosts[0],on=state.followingPeople.includes(state.person);return `<div class="profile-cover"></div><section class="profile-info">${avatar({...p,avatar:state.person.slice(0,1)})}<button class="profile-edit" data-action="follow-person" data-value="${escapeHTML(state.person)}" aria-pressed="${on}">${on?'已关注':'+ 关注'}</button><h2>${escapeHTML(state.person)}</h2><p>日常记录，偶尔分享一些有意思的事。</p><div class="profile-stats"><button data-action="person-stats" data-value="帖子">${allPosts().filter(p=>p.author===state.person).length}<small>帖子</small></button><button data-action="person-stats" data-value="关注">86<small>关注</small></button><button data-action="person-stats" data-value="粉丝">1,284<small>粉丝</small></button></div><button class="secondary-button full-button" style="margin-bottom:16px" data-action="chat-person">${icon('message')} 发私信</button></section>${tabs(['帖子','回复'],state.profileTab,'profile-tab')}${state.profileTab==='帖子'?allPosts().filter(p=>p.author===state.person).map(postCard).join(''):commentHTML({...commentSeeds[0],author:state.person})}`;}
function render(){
 let h='',html='';
 switch(state.page){
 case 'feed': h=`<div class="topbar"><span class="app-title"><img src="icon.svg" alt="氢贴吧新图标">氢贴吧<span class="edition-pill">氢 · 日常</span></span><div class="header-tools">${iconButton('search','search','搜索帖子、吧和用户')}<button class="icon-button" data-action="nav" data-page="profile" aria-label="我的主页"><span class="mini-avatar">M</span></button></div></div><div class="tabs" role="tablist">${['推荐','关注','热榜'].map(t=>`<button class="tab ${t===state.feedTab?'active':''}" role="tab" aria-selected="${t===state.feedTab}" data-action="feed-tab" data-value="${t}">${t}</button>`).join('')}<button class="feed-filter" data-action="feed-options" aria-label="信息流偏好">${icon('filter')}</button></div>`;html=feedHTML();break;
 case 'forums':h=`<div class="topbar"><h1 class="page-heading">逛吧</h1>${iconButton('grid','forum-categories','吧分类')}</div>`;html=forumsHTML();break;
 case 'forum':h=simpleHeader(`${state.forum}吧`,iconButton('more','forum-menu','吧的更多操作'));html=forumHTML();break;
 case 'messages':h=`<div class="topbar"><h1 class="page-heading">消息</h1>${iconButton('check','mark-read','全部已读')}</div>`;html=messagesHTML();break;
 case 'profile':h=`<div class="topbar"><h1 class="page-heading">我的</h1></div>`;html=profileHTML();break;
 case 'settings':h=simpleHeader('设置');html=settingsHTML();break;
 case 'search':h=`<form class="topbar search-topbar" id="search-form">${iconButton('back','back','返回')}<label class="search-input-wrap">${icon('search')}<input id="search-input" type="search" name="q" placeholder="搜索帖子、吧和用户" aria-label="搜索帖子、吧和用户" enterkeyhint="search" value="${escapeHTML(state.search)}" autocomplete="off"></label><button class="text-button" type="submit">搜索</button></form>${state.search?tabs(['综合','帖子','吧','用户'],state.searchTab,'search-tab'):''}`;html=searchHTML();break;
 case 'detail':h=simpleHeader('帖子',`<div class="header-tools"><button class="text-button" data-action="only-op" aria-pressed="${state.onlyOP}">${state.onlyOP?'查看全部':'只看楼主'}</button>${iconButton('more','post-menu','更多帖子操作',`data-id="${state.post}"`)}</div>`);html=detailHTML();break;
 case 'list':h=simpleHeader(state.list);html=listHTML();break;
 case 'notifications':h=simpleHeader(state.notification,iconButton('check','mark-read','标记已读'));html=notificationsHTML();break;
 case 'chat':h=simpleHeader(state.chat,iconButton('more','chat-options','聊天设置'));html=chatHTML();break;
 case 'person':h=simpleHeader('个人主页',iconButton('more','person-options','更多用户操作'));html=personHTML();break;
 }
 header.innerHTML=h;content.innerHTML=html;updateDock();
 if(state.page==='detail'){const p=currentPost();detailBar.innerHTML=`<button class="reply-launch" data-action="reply" data-value="楼主">说点什么，加入讨论…</button><button class="icon-button ${state.likes.includes(p.id)?'is-on':''}" data-action="like" data-id="${p.id}" aria-label="点赞" aria-pressed="${state.likes.includes(p.id)}">${icon('heart')}</button><button class="icon-button ${state.saved.includes(p.id)?'is-on':''}" data-action="save" data-id="${p.id}" aria-label="收藏" aria-pressed="${state.saved.includes(p.id)}">${icon('bookmark')}</button>`;}
 $('.chat-input')?.remove();if(state.page==='chat'){$('#app-body').insertAdjacentHTML('beforeend',`<form class="chat-input" id="chat-form"><input name="message" id="chat-message" aria-label="聊天内容" placeholder="发消息…" enterkeyhint="send" autocomplete="off"><button type="submit" aria-label="发送本地演示消息" disabled>${icon('send')}</button></form>`);}
 setSettings();$$('[data-action=state]').forEach(b=>{b.classList.toggle('selected',b.dataset.value===state.feedState);b.setAttribute('aria-pressed',b.dataset.value===state.feedState);});
}
function showOverlay(body,{kind='sheet',title=''}={}){
 closeOverlay(true);lastFocus=document.activeElement;modal=kind;const token=++overlayToken;
 overlayRoot.innerHTML=kind==='image'?`<div class="overlay image-overlay" role="dialog" aria-modal="true" aria-label="图片预览">${iconButton('close','close','关闭图片预览')}${body}</div>`:kind==='compose'?`<div class="overlay composer-overlay" role="dialog" aria-modal="true" aria-label="发帖编辑器"><div class="sheet" tabindex="-1">${body}</div></div>`:`<div class="overlay" role="dialog" aria-modal="true" aria-label="${escapeHTML(title)}"><section class="sheet sheet-glass" tabindex="-1"><div class="sheet-handle"></div><div class="sheet-header"><h2>${title}</h2>${iconButton('close','close','关闭面板')}</div><div class="sheet-content">${body}</div></section></div>`;
 [header,scroller,dock,detailBar,$('.chat-input')].filter(Boolean).forEach(el=>el.inert=true);
 requestAnimationFrame(()=>{if(token!==overlayToken)return;$('.overlay')?.classList.add('visible');$('.sheet',overlayRoot)?.focus({preventScroll:true});if(kind==='image')$('button',overlayRoot)?.focus({preventScroll:true});});
}
function closeOverlay(immediate=false){
 if(!modal)return;
 if(modal==='compose'){readDraft();persist();}
 modal=null;const token=++overlayToken,layer=$('.overlay',overlayRoot);layer?.classList.remove('visible');
 [header,scroller,dock,detailBar,$('.chat-input')].filter(Boolean).forEach(el=>el.inert=false);
 if(immediate||state.settings.reduced||motionSystem.matches)overlayRoot.innerHTML='';else setTimeout(()=>{if(token===overlayToken)overlayRoot.innerHTML='';},200);
 if(lastFocus?.isConnected)lastFocus.focus({preventScroll:true});
}
function openComposer(forum){
 if(forum)state.draft.forum=forum;
 const d=state.draft;
 showOverlay(`<div class="composer-head"><button class="text-button" data-action="close-compose">取消</button><h2>新的讨论</h2><button class="text-button" data-action="save-draft">存草稿</button><button class="primary-button" id="publish-button" data-action="publish" disabled>发布</button></div><div class="composer-scroll"><button class="forum-select" data-action="choose-forum">${icon('forum')}<span id="chosen-forum">${escapeHTML(d.forum||'摄影')}吧</span>${icon('down','arrow-icon')}</button><input class="composer-title" id="compose-title" aria-label="帖子标题" placeholder="给你的讨论起个标题" maxlength="100" value="${escapeHTML(d.title)}"><textarea class="composer-body" id="compose-body" aria-label="帖子正文" placeholder="分享新鲜事，也分享你的想法…" maxlength="10000">${escapeHTML(d.body)}</textarea><div id="media-previews" class="media-previews"></div><div id="poll-container"></div><p class="composer-hint">好的讨论，始于真实的表达。<br>友善交流，尊重每一个不同的声音。</p></div><div class="composer-bottom"><div class="composer-toolbar"><button data-action="add-image" aria-label="添加图片">${icon('image')}</button><button data-action="add-video" aria-label="添加视频">${icon('video')}</button><button data-action="add-poll" aria-label="添加投票">${icon('poll')}</button><button data-action="add-mention" aria-label="提及用户">${icon('at')}</button><button data-action="add-emoji" aria-label="添加表情">${icon('smile')}</button><span class="composer-limit" id="compose-count">0 / 10000</span></div><p class="composer-local">${icon('lock')}设计稿内模拟发布 · 不会发送到贴吧</p></div><input type="file" id="media-input" accept="image/*" multiple hidden><input type="file" id="video-input" accept="video/*" hidden>`,{kind:'compose'});
 updateComposer();renderMedia();renderPoll();
}
function readDraft(){if(!$('#compose-title'))return;state.draft.title=$('#compose-title').value;state.draft.body=$('#compose-body').value;}
function updateComposer(){readDraft();const valid=Boolean(state.draft.title.trim()&&(state.draft.body.trim()||mediaFiles.length||composeVideo));$('#publish-button').disabled=!valid;$('#compose-count').textContent=`${state.draft.body.length} / 10000`;persist();}
function renderMedia(){if(!$('#media-previews'))return;$('#media-previews').innerHTML=mediaFiles.map((m,i)=>`<div class="media-preview"><img src="${m.url}" alt="待发布图片 ${i+1}"><button data-action="remove-media" data-id="${i}" aria-label="移除图片 ${i+1}">${icon('close')}</button></div>`).join('')+(composeVideo?`<div class="media-preview" style="display:grid;place-items:center">${icon('video')}<small>本地视频</small><button data-action="remove-video" aria-label="移除视频">${icon('close')}</button></div>`:'');}
function renderPoll(){if(!$('#poll-container'))return;$('#poll-container').innerHTML=poll?`<div class="poll-block"><div>一起投个票<button class="icon-button" style="width:30px;height:30px" data-action="remove-poll" aria-label="移除投票">${icon('close')}</button></div>${poll.map((v,i)=>`<input data-poll-index="${i}" aria-label="投票选项 ${i+1}" placeholder="选项 ${i+1}" maxlength="40" value="${escapeHTML(v)}">`).join('')}</div>`:'';}
function toggleArray(key,value){state[key]=state[key].includes(value)?state[key].filter(v=>v!==value):[...state[key],value];persist();}
function runSearch(term){const q=term.trim();state.search=q;if(q){state.searchHistory=[q,...state.searchHistory.filter(t=>t!==q)].slice(0,8);persist();}render();scroller.scrollTop=0;}
function openReply(target='楼主',nested=false){const p=currentPost();showOverlay(`<div class="reply-context">回复 ${escapeHTML(target)} · ${escapeHTML(p.title)}</div><textarea class="reply-textarea" id="reply-input" aria-label="回复内容" maxlength="1000" placeholder="友善的讨论，从你开始…">${escapeHTML(state.replyDraft||'')}</textarea><div class="reply-actions"><span>仅在设计稿内展示</span><button class="primary-button" data-action="send-reply" data-nested="${nested}" disabled>发送回复</button></div>`,{kind:nested?'nested-reply':'reply',title:`回复 ${escapeHTML(target)}`});}
function showAbout(){showOverlay(`<div class="about-signature" style="padding-top:3px"><img src="icon.svg" alt="氢贴吧新图标"><strong style="color:var(--ink);font-size:18px">氢贴吧</strong><p style="margin:10px 0 15px">轻一点，离讨论近一点。</p>完整贴吧客户端的交互设计方向<br>当前为 HTML 设计稿 · 示例账号与数据<br><br>仅供学习交流，禁止商用。<span class="signature">@Bilibili Moying32</span></div>`,{title:'关于软件'});}
function openSmallForm(title,fields,action){showOverlay(`<form class="mini-form" id="small-form" data-submit-action="${action}">${fields}<button class="primary-button full-button" type="submit" style="margin-top:15px">保存到本地设计稿</button></form>`,{title});}
document.addEventListener('click',e=>{
 const btn=e.target.closest('[data-action]');if(!btn||btn.disabled)return;
 const a=btn.dataset.action,v=btn.dataset.value,id=Number(btn.dataset.id);
 switch(a){
 case 'nav':navigate(btn.dataset.page,{},true);break;
 case 'back':back();break;
 case 'close':closeOverlay();break;
 case 'go-feed':navigate('feed',{},true);break;
 case 'discover':navigate('forums',{},true);break;
 case 'feed-tab':state.feedTab=v;state.feedLimit=5;render();scroller.scrollTop=0;break;
 case 'feed-options':showOverlay(`<p>保留清晰的内容流，让你决定如何发现讨论。</p>${menuRow('user','管理关注的人','relationships','', 'data-value="关注"')}${menuRow('eye','内容偏好与屏蔽','preferences')}${menuRow('settings','阅读与外观设置','settings')}`,{title:'信息流偏好'});break;
 case 'theme':state.settings.theme=v;setSettings();persist();break;
 case 'state':state.feedState=v;navigate('feed',{},true);break;
 case 'retry':state.feedState='loading';render();setTimeout(()=>{state.feedState='normal';if(state.page==='feed')render();toast('内容已重新加载（本地演示）');},700);break;
 case 'load-more':state.feedLimit+=5;rerenderKeepScroll();break;
 case 'search':navigate('search',{search:'',searchTab:'综合'});break;
 case 'forum-search':navigate('search',{search:'',searchTab:'吧'});break;
 case 'search-term':state.searchTab='综合';runSearch(v);break;
 case 'search-tab':state.searchTab=v;render();break;
 case 'clear-query':runSearch('');$('#search-input')?.focus();break;
 case 'clear-search':state.searchHistory=[];persist();render();toast('搜索历史已清空');break;
 case 'detail':navigate('detail',{post:id,onlyOP:false,sortNewest:false});break;
 case 'forum':navigate('forum',{forum:v,forumTab:'全部'});break;
 case 'forum-tab':state.forumTab=v;rerenderKeepScroll();break;
 case 'follow-forum':toggleArray('following',v);if(modal)closeOverlay(true);rerenderKeepScroll();toast(state.following.includes(v)?`已在设计稿中关注 ${v}吧`:`已取消关注 ${v}吧`);break;
 case 'follow-person':toggleArray('followingPeople',v);rerenderKeepScroll();toast(state.followingPeople.includes(v)?'已关注 · 本地演示':'已取消关注 · 本地演示');break;
 case 'my-forums':showOverlay(`<p>你关注的 ${state.following.length} 个吧</p>${forums.filter(f=>state.following.includes(f.name)).map(forumItem).join('')||'<p>还没有关注的吧，去推荐列表看看吧。</p>'}`,{title:'我关注的吧'});break;
 case 'checkin':if(!state.following.length){toast('先关注一个喜欢的吧');break;}state.checked=true;if(state.page==='forums'||state.page==='forum')rerenderKeepScroll();toast(`已为 ${state.following.length} 个吧模拟签到，经验 +8`);break;
 case 'forum-ranking':showOverlay(`<p>示例排行榜 · 按社区活跃度展示</p>${[...forums].reverse().map(forumItem).join('')}`,{title:'吧排行榜'});break;
 case 'forum-categories':showOverlay(`<p>从兴趣出发，找一个聊得来的地方。</p><div class="search-chips">${forums.map(f=>`<button class="chip" data-action="forum" data-value="${f.name}">${f.name}</button>`).join('')}</div>`,{title:'全部分类'});break;
 case 'shuffle-forums':{const list=[...forums].sort(()=>Math.random()-.5).slice(0,4);$('#recommended-forums').innerHTML=list.map(forumItem).join('');break;}
 case 'forum-notice':showOverlay('<p>欢迎来到这里。请分享真实体验，友善讨论，尊重创作者。请勿发布广告、重复内容或侵犯他人隐私的信息。</p><p>这是吧规与置顶公告入口的示例。</p>',{title:`${escapeHTML(state.forum)}吧 · 吧规与公告`});break;
 case 'forum-menu':showOverlay(`${menuRow('edit','在本吧发帖','compose')}${menuRow('checkin','签到','checkin')}${menuRow('info','查看吧规','forum-notice')}${menuRow('share','分享本吧','share-forum')}`,{title:`${escapeHTML(state.forum)}吧`});break;
 case 'share-forum':showOverlay(`<p>${escapeHTML(state.forum)}吧 · 和同频的人交流</p><button class="primary-button full-button" data-action="copy-link" data-value="https://tieba.baidu.com/f?kw=${encodeURIComponent(state.forum)}">复制吧链接</button>`,{title:'分享社区'});break;
 case 'like':toggleArray('likes',id);rerenderKeepScroll();break;
 case 'save':toggleArray('saved',id);rerenderKeepScroll();toast(state.saved.includes(id)?'已收藏到本地设计稿':'已取消收藏');break;
 case 'post-menu':showOverlay(`${menuRow('bookmark',state.saved.includes(id)?'取消收藏':'收藏帖子','menu-save','',`data-id="${id}"`)}${menuRow('share','分享帖子','share','',`data-id="${id}"`)}${menuRow('eye','不感兴趣','not-interested','',`data-id="${id}"`)}${menuRow('shield','举报内容','report')}`,{title:'帖子操作'});break;
 case 'menu-save':closeOverlay(true);toggleArray('saved',id);rerenderKeepScroll();toast(state.saved.includes(id)?'已收藏到本地设计稿':'已取消收藏');break;
 case 'not-interested':closeOverlay(true);{const el=$(`[data-post-id="${id}"]`);if(el){el.remove();toast('已在本次预览中隐藏这条内容');}else toast('已记录偏好 · 本地演示');}break;
 case 'report':showOverlay('<p>选择原因，在正式客户端中将提交给平台处理。</p><div class="search-chips">'+['广告与垃圾内容','人身攻击','不实信息','其他问题'].map(t=>`<button class="chip" data-action="report-reason" data-value="${t}">${t}</button>`).join('')+'</div>',{title:'举报内容'});break;
 case 'report-reason':closeOverlay();toast(`已选择“${v}”，设计稿不会提交举报`);break;
 case 'share':{const p=allPosts().find(p=>p.id===id)||currentPost();showOverlay(`<p>${escapeHTML(p.title)}</p><button class="primary-button full-button" data-action="copy-link" data-value="氢贴吧设计稿示例：${escapeHTML(p.title)}">${icon('link')}复制示例分享文字</button><p style="font-size:10px;margin-top:15px">此帖子为虚构示例，没有真实帖子链接。</p>`,{title:'分享讨论'});break;}
 case 'copy-link':navigator.clipboard?.writeText(v).then(()=>{closeOverlay();toast('已复制');}).catch(()=>showCopyFallback(v))||showCopyFallback(v);break;
 case 'image':showOverlay(`<img src="${escapeHTML(v)}" alt="放大的示例插画"><p>原创矢量示意图 · 点击空白处或按 Esc 关闭</p>`,{kind:'image'});break;
 case 'only-op':state.onlyOP=!state.onlyOP;rerenderKeepScroll();break;
 case 'sort-comments':state.sortNewest=!state.sortNewest;rerenderKeepScroll();break;
 case 'comment-like':toggleArray('replyLikes',btn.dataset.id);if(modal==='nested')openNested();else rerenderKeepScroll();break;
 case 'nested':openNested();break;
 case 'reply':openReply(v||'楼主');break;
 case 'nested-reply':openReply(v||'林间晚风',true);break;
 case 'send-reply':{const text=$('#reply-input').value.trim();if(!text)break;const nested=btn.dataset.nested==='true';if(nested){state.extraNested=state.extraNested||[];state.extraNested.push(text);}else{state.comments[state.post]=state.comments[state.post]||[];state.comments[state.post].push({id:`local-${Date.now()}`,text});}state.replyDraft='';persist();closeOverlay(true);if(nested)openNested();else rerenderKeepScroll();toast('回复已添加到设计稿，未发送到贴吧');break;}
 case 'vote':btn.closest('.poll-block').innerHTML=`<div>${icon('check')} 已选择第 ${Number(v)+1} 项 · 本地演示</div>`;break;
 case 'compose':openComposer(btn.dataset.forum||(state.page==='forum'?state.forum:undefined));break;
 case 'close-compose':readDraft();persist();closeOverlay();toast(state.draft.title||state.draft.body?'草稿已自动保留':'已退出编辑');break;
 case 'save-draft':readDraft();persist();closeOverlay();toast('草稿已保存在此浏览器');break;
 case 'choose-forum':{readDraft();const choices=forums.map(f=>`<option value="${f.name}" ${state.draft.forum===f.name?'selected':''}>${f.name}吧</option>`).join('');$('#chosen-forum').outerHTML=`<select id="composer-forum-select" aria-label="选择发帖的吧" style="background:var(--paper);color:var(--ink);border:0;padding:7px;font-size:16px">${choices}</select>`;btn.removeAttribute('data-action');$('#composer-forum-select').focus();break;}
 case 'add-image':$('#media-input').click();break;
 case 'add-video':$('#video-input').click();break;
 case 'remove-media':URL.revokeObjectURL(mediaFiles[id].url);mediaFiles.splice(id,1);renderMedia();updateComposer();break;
 case 'remove-video':composeVideo=false;renderMedia();updateComposer();break;
 case 'add-poll':readDraft();poll=poll||['',''];renderPoll();$('[data-poll-index]')?.focus();break;
 case 'remove-poll':poll=null;renderPoll();break;
 case 'add-mention':insertBody('@山野来信 ');break;
 case 'add-emoji':insertBody(' 🌿');break;
 case 'publish':publishPost();break;
 case 'drafts':showOverlay(state.draft.title||state.draft.body?`<p>草稿自动保存在当前浏览器。</p><button class="quoted" data-action="compose"><strong>${escapeHTML(state.draft.title||'未命名草稿')}</strong><br>${escapeHTML(state.draft.body.slice(0,80))}</button><div class="sheet-actions"><button class="secondary-button" data-action="delete-draft">删除草稿</button><button class="primary-button" data-action="compose">继续编辑</button></div>`:'<p>暂时没有草稿。写到一半也没关系，退出编辑时会自动保留文字。</p><button class="primary-button full-button" data-action="compose">开始写帖</button>',{title:'我的草稿'});break;
 case 'delete-draft':state.draft={forum:'摄影',title:'',body:''};mediaFiles.forEach(m=>URL.revokeObjectURL(m.url));mediaFiles=[];poll=null;composeVideo=false;persist();closeOverlay();toast('草稿已删除');break;
 case 'notifications':navigate('notifications',{notification:v});break;
 case 'mark-read':state.unread=false;updateDock();$$('.unread-badge,.conversation-dot').forEach(e=>e.hidden=true);toast('当前消息已标记为已读');break;
 case 'chat':navigate('chat',{chat:v});break;
 case 'new-chat':showOverlay(`<p>选择联系人发起本地演示聊天。</p>${['林间晚风','山野来信','像素漫游'].map(n=>`<button class="sheet-menu" data-action="chat" data-value="${n}">${icon('user')}${n}${icon('chevron')}</button>`).join('')}`,{title:'发起聊天'});break;
 case 'chat-options':showOverlay(`<p>对话设置 · ${escapeHTML(state.chat)}</p>${menuRow('bell','消息提醒','chat-reminder')}${menuRow('trash','清空本地演示消息','clear-chat')}${menuRow('shield','屏蔽与举报','report')}`,{title:'聊天设置'});break;
 case 'chat-reminder':btn.innerHTML=icon('check')+'已切换为免打扰 · 本地示例';btn.disabled=true;break;
 case 'clear-chat':state.chats[state.chat]=[];persist();closeOverlay(true);render();toast('新增的本地聊天记录已清空');break;
 case 'person':navigate('person',{person:v,profileTab:'帖子'});break;
 case 'profile-tab':state.profileTab=v;rerenderKeepScroll();break;
 case 'chat-person':navigate('chat',{chat:state.person});break;
 case 'person-stats':showOverlay(`<p>${escapeHTML(state.person)}的${escapeHTML(v)}列表 · 示例</p>${['山野来信','像素漫游','今天也想早点睡'].map(n=>`<button class="sheet-menu" data-action="person" data-value="${n}">${icon('user')}${n}${icon('chevron')}</button>`).join('')}`,{title:v});break;
 case 'person-options':showOverlay(`${menuRow('message','发私信','chat-person')}${menuRow('shield','举报用户','report')}${menuRow('eye','屏蔽用户','block-person')}`,{title:'用户操作'});break;
 case 'block-person':closeOverlay();toast('已在本次预览中记录屏蔽选择');break;
 case 'relationships':showOverlay(`<p>${v==='粉丝'?'展示部分示例粉丝':'你关注的创作者'}</p>${(v==='粉丝'?['林间晚风','像素漫游','候鸟停靠站']:state.followingPeople).map(n=>`<button class="sheet-menu" data-action="person" data-value="${escapeHTML(n)}">${icon('user')}${escapeHTML(n)}${icon('chevron')}</button>`).join('')||'<p>还没有关注的创作者。</p>'}`,{title:v});break;
 case 'list':navigate('list',{list:v});break;
 case 'edit-profile':openSmallForm('编辑资料',`<label>昵称<input name="name" maxlength="20" required value="${escapeHTML(state.profile.name)}"></label><label>个人简介<textarea name="bio" maxlength="100" rows="3">${escapeHTML(state.profile.bio)}</textarea></label>`,'profile');break;
 case 'settings':navigate('settings');break;
 case 'preferences':showOverlay('<p>示例内容偏好，仅保存在本地设计稿中。</p><div class="search-chips">'+['摄影','旅行','设计','生活','Minecraft','数码'].map(t=>`<button class="chip" data-action="interest" aria-pressed="false">${t}</button>`).join('')+'</div><div class="notice" style="margin-top:18px;border-radius:10px">屏蔽列表为空</div>',{title:'内容偏好与屏蔽'});break;
 case 'interest':{const on=btn.getAttribute('aria-pressed')!=='true';btn.setAttribute('aria-pressed',on);btn.style.background=on?'var(--accent-soft)':'var(--field)';btn.style.color=on?'var(--accent)':'var(--muted)';break;}
 case 'account':showOverlay(`<p>示例账号：@moying32</p><div class="notice" style="border-radius:12px;line-height:1.8">正式客户端采用百度官方登录与安全验证；此设计稿不收集密码。</div>${menuRow('user','切换示例账号','switch-account')}${menuRow('logout','退出示例账号','logout')}`,{title:'账号与安全'});break;
 case 'switch-account':showOverlay('<p>当前只有一个演示账号。正式客户端在这里进入百度官方账号验证流程。</p>',{title:'切换账号'});break;
 case 'logout':showOverlay('<p>当前为虚构示例账号，没有真实登录会话。你可以继续检查全部页面。</p><button class="primary-button full-button" data-action="close">继续预览</button>',{title:'示例账号'});break;
 case 'clear-cache':showOverlay('<p>将清理设计稿展示的示例缓存。收藏和文字草稿会保留。</p><button class="primary-button full-button" data-action="confirm-cache">清理示例缓存</button>',{title:'清理缓存'});break;
 case 'confirm-cache':closeOverlay();toast('示例缓存已清理 · 0 MB');break;
 case 'about':showAbout();break;
 case 'reset':showOverlay('<p>清除本浏览器内设计稿的收藏、点赞、发帖、草稿和设置，恢复初始状态。</p><div class="sheet-actions"><button class="secondary-button" data-action="close">保留数据</button><button class="primary-button" data-action="confirm-reset">重置设计稿</button></div>',{title:'重置本地演示数据'});break;
 case 'confirm-reset':try{localStorage.removeItem('hydrogen-design-v1');}catch{}location.reload();break;
 }
});
function showCopyFallback(value){showOverlay(`<p>复制功能在当前浏览器不可用，可手动选中下方文字。</p><textarea class="reply-textarea" readonly aria-label="可复制文字">${escapeHTML(value)}</textarea>`,{title:'复制分享内容'});}
function insertBody(s){const el=$('#compose-body'),at=el.selectionStart;el.value=el.value.slice(0,at)+s+el.value.slice(el.selectionEnd);el.focus();el.setSelectionRange(at+s.length,at+s.length);updateComposer();}
function openNested(){const rows=[{...commentSeeds[2],floor:'1 条回复',text:'是呀，什么都不安排也很好。'},{...commentSeeds[0],floor:'2 条回复',text:'已经开始期待下一个周末了。'},{...commentSeeds[1],floor:'3 条回复',text:'借楼问一下，路线适合徒步新手吗？'}];showOverlay(`<div class="reply-context">林间晚风：${commentSeeds[0].text}</div>${rows.map(c=>commentHTML({...c,nested:false},true)).join('')}${(state.extraNested||[]).map((t,i)=>commentHTML({id:`nested-local-${i}`,author:state.profile.name,avatar:'M',floor:`${4+i} 条回复`,text:t,likes:0},true)).join('')}<button class="primary-button full-button" style="margin-top:15px" data-action="nested-reply">回复这层讨论</button>`,{kind:'nested',title:'楼中楼 · 继续讨论'});}
function publishPost(){
 readDraft();const d={...state.draft};if(!d.title.trim()||!(d.body.trim()||mediaFiles.length||composeVideo))return;
 if(poll&&poll.some(v=>!v.trim())){toast('请补全投票的两个选项');return;}
 const btn=$('#publish-button'),publishToken=overlayToken;$$('button,input,textarea,select',overlayRoot).forEach(el=>el.disabled=true);btn.textContent='发布中';
 // Deliberate local latency makes the pending state inspectable; no network request.
 setTimeout(()=>{const id=Date.now(),p={id,author:state.profile.name,avatar:'M',tone:'',time:'刚刚',forum:d.forum,title:d.title.trim(),text:d.body.trim(),body:d.body.split(/\n+/).filter(Boolean),comments:0,likes:0,views:'1',poll:poll?[...poll]:null};
 state.posts.unshift(p);state.draft={forum:d.forum,title:'',body:''};persist();
 // Media blobs stay in memory for this session; storage contains only text.
 if(mediaFiles.length)p.image=mediaFiles[0].url;
 if(composeVideo){p.body.push('【已添加本地视频 · 原型仅演示附件状态】');}
 mediaFiles=[];poll=null;composeVideo=false;
 if(modal==='compose'&&overlayToken===publishToken){$('#compose-title').value='';$('#compose-body').value='';closeOverlay(true);navigate('detail',{post:id});}
 toast('已发布到本地设计稿，未发送到贴吧');
 },550);
}
document.addEventListener('input',e=>{
 if(['compose-title','compose-body'].includes(e.target.id))updateComposer();
 if(e.target.dataset.pollIndex!==undefined)poll[Number(e.target.dataset.pollIndex)]=e.target.value;
 if(e.target.id==='reply-input'){state.replyDraft=e.target.value;$('[data-action=send-reply]').disabled=!e.target.value.trim();}
 if(e.target.id==='chat-message')$('#chat-form button').disabled=!e.target.value.trim();
});
document.addEventListener('change',e=>{
 const el=e.target;
 if(el.hasAttribute('data-accent')){state.settings.accent=el.value.slice(1);setSettings();persist();}
 if(el.dataset.setting){state.settings[el.dataset.setting]=el.checked;setSettings();persist();if(!state.settings.autoHide)dock.classList.remove('dock-hidden');}
 if(el.dataset.settingSelect){state.settings[el.dataset.settingSelect]=el.value;setSettings();persist();}
 if(el.id==='composer-forum-select'){state.draft.forum=el.value;persist();}
 if(el.id==='media-input'){for(const f of [...el.files].slice(0,9-mediaFiles.length)){if(!f.type.startsWith('image/'))continue;mediaFiles.push({name:f.name,url:URL.createObjectURL(f)});}renderMedia();updateComposer();el.value='';toast('图片仅在本次预览中保留，文字草稿可跨刷新恢复');}
 if(el.id==='video-input'&&el.files.length){composeVideo=true;renderMedia();updateComposer();el.value='';toast('已添加本地视频附件示例');}
});
document.addEventListener('submit',e=>{
 if(e.target.id==='search-form'){e.preventDefault();runSearch(new FormData(e.target).get('q')||'');}
 if(e.target.id==='chat-form'){e.preventDefault();const input=$('#chat-message'),text=input.value.trim();if(!text)return;state.chats[state.chat]=state.chats[state.chat]||[];state.chats[state.chat].push({me:true,text});persist();rerenderKeepScroll();scroller.scrollTop=scroller.scrollHeight;$('#chat-message').focus();toast('消息仅保存在设计稿内');}
 if(e.target.id==='small-form'){e.preventDefault();const f=new FormData(e.target);if(e.target.dataset.submitAction==='profile'){state.profile.name=String(f.get('name')).trim()||'Moying32';state.profile.bio=String(f.get('bio')).trim();persist();closeOverlay(true);rerenderKeepScroll();toast('资料已更新到本地设计稿');}}
});
overlayRoot.addEventListener('click',e=>{if(e.target.classList.contains('overlay'))closeOverlay();});
document.addEventListener('keydown',e=>{
 document.body.classList.add('keyboard-input');
 if(e.key==='Escape'){e.preventDefault();back();}
 if(e.key==='Tab'&&modal){const items=$$('button:not([disabled]),input:not([hidden]),textarea,select,a[href]',overlayRoot).filter(el=>el.getClientRects().length);if(!items.length)return;const first=items[0],last=items.at(-1);if(e.shiftKey&&(document.activeElement===first||document.activeElement.classList.contains('sheet'))){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}
});
document.addEventListener('pointerdown',()=>document.body.classList.remove('keyboard-input'));
scroller.addEventListener('scroll',()=>{
 const top=scroller.scrollTop,diff=top-priorScroll;priorScroll=top;
 if(modal||dock.hidden||!state.settings.autoHide)return;
 if(top<50){dock.classList.remove('dock-hidden');scrollDelta=0;return;}
 if((scrollDelta>0&&diff<0)||(scrollDelta<0&&diff>0))scrollDelta=0;
 scrollDelta+=diff;
 if(scrollDelta>23)dock.classList.add('dock-hidden');
 if(scrollDelta< -12)dock.classList.remove('dock-hidden');
},{passive:true});
colorSystem.addEventListener('change',setSettings);motionSystem.addEventListener('change',setSettings);
// Original rounded-rectangle edge displacement map: bend only the backdrop,
// never glyphs or touch targets. CSS blur remains the portable fallback.
function generateLensMap(){
 const w=Math.round(dock.clientWidth)||390,h=75,r=h/2,canvas=document.createElement('canvas');canvas.width=w;canvas.height=h;
 const ctx=canvas.getContext('2d'),im=ctx.createImageData(w,h);
 const sdf=(x,y)=>{const qx=Math.abs(x-w/2)-(w/2-r),qy=Math.abs(y-h/2);return Math.hypot(Math.max(qx,0),Math.max(qy,0))+Math.min(Math.max(qx,qy),0)-r;};
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){const d=sdf(x,y),edge=Math.max(0,1-Math.abs(d)/12),gx=(sdf(x+1,y)-sdf(x-1,y))/2,gy=(sdf(x,y+1)-sdf(x,y-1))/2,i=(y*w+x)*4;im.data[i]=128+gx*edge*80;im.data[i+1]=128+gy*edge*80;im.data[i+2]=128;im.data[i+3]=255;}
 ctx.putImageData(im,0,0);$('#lens-map').setAttribute('href',canvas.toDataURL());
}
const resizeObserver=new ResizeObserver(()=>{if(!dock.hidden)generateLensMap();});resizeObserver.observe(phone);
$('.studio-brand').addEventListener('click',e=>{e.preventDefault();navigate('feed',{},true);});
hydrateIcons();render();generateLensMap();
