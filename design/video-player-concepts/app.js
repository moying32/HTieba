const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const paths={close:'M6 6l12 12M18 6 6 18',play:'m9 5 11 7-11 7Z',pause:'M8 5v14M16 5v14',fullscreen:'M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5',exit:'M3 8h5V3m8 0v5h5M8 21v-5H3m13 5v-5h5',muted:'m11 5-5 4H3v6h3l5 4ZM16 9l5 6m0-6-5 6',sound:'m11 5-5 4H3v6h3l5 4ZM15 8q5 4 0 8m3-11q8 7 0 14',back:'M3 10a9 9 0 1 1 1 8M3 4v6h6'};
function icon(name){return `<svg viewBox="0 0 24 24" fill="${name==='play'?'currentColor':'none'}" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name]}"/>${name==='back'?'<text x="10" y="16" stroke="none" fill="currentColor" font-size="9" font-family="sans-serif">5</text>':''}</svg>`}
$$('[data-icon]').forEach(b=>b.innerHTML=icon(b.dataset.icon));
const video=$('#video'),player=$('#player'),device=$('#device'),seek=$('#seek'),menu=$('#speedMenu');
let hideTimer,feedbackTimer,tapTimer,seeking=false,initialized=false,closed=false;
let holdTimer,holdActive=false,holdOriginalRate=1,activePointer=null,pressOrigin=null,suppressPressClick=false;
const notes={a:['01','更接近 X 的轻量沉浸','细进度线贴近底边；倍速、静音与全屏各自成组。玻璃只承托按钮，画面不被大块面板遮挡。',['画面优先','轻量玻璃']],b:['02','常用控制，一只手就够了','播放、快退、倍速、声音和全屏收进一个底部浮岛。取消中央播放遮挡，适合边看边调整。',['单手操作','底部浮岛']],c:['03','分层卡片，安静地看完','视频拥有独立画框，进度和控制放在画面外；标题另起一层。浅色玻璃承托信息，横屏时自然移到侧栏。',['分层卡片','画面无遮挡']]};
function format(t){t=Number.isFinite(t)?Math.max(0,t):0;return `${Math.floor(t/60)}:${String(Math.floor(t%60)).padStart(2,'0')}`}
function controls(show=true){player.classList.toggle('controls-hidden',!show);$('#pictureTap').setAttribute('aria-label',show?'隐藏播放控件':'显示播放控件');$$('.controls').forEach(el=>el.inert=!show);armHide()}
function armHide(){clearTimeout(hideTimer);const focusedControl=document.activeElement!==player&&player.contains(document.activeElement)&&document.activeElement.matches(':focus-visible');if(!video.paused&&!video.ended&&!seeking&&!closed&&!holdActive&&!menu.matches(':popover-open')&&!player.classList.contains('controls-hidden')&&!focusedControl)hideTimer=setTimeout(()=>controls(false),2500)}
function finishHold(){clearTimeout(holdTimer);if(!holdActive)return;holdActive=false;video.playbackRate=holdOriginalRate;$('#holdIndicator').hidden=true;controls(true)}
function feedback(message){$('#feedback').textContent=message;$('#feedback').classList.add('visible');clearTimeout(feedbackTimer);feedbackTimer=setTimeout(()=>$('#feedback').classList.remove('visible'),850)}
function sync(){const d=Number.isFinite(video.duration)?video.duration:12;seek.max=d;if(!seeking)seek.value=video.currentTime;seek.style.setProperty('--progress',`${Number(seek.value)/d*100}%`);seek.setAttribute('aria-valuetext',`${format(Number(seek.value))}，共 ${format(d)}`);$$('.current-time').forEach(e=>e.textContent=format(Number(seek.value)));$$('.duration').forEach(e=>e.textContent=format(d));$$('[data-action=play]').forEach(b=>{b.innerHTML=icon(video.paused?'play':'pause');b.setAttribute('aria-label',video.paused?(video.ended?'重新播放':'播放'):'暂停')});player.classList.toggle('is-playing',!video.paused)}
async function togglePlay(){if(video.paused){if(video.ended)video.currentTime=0;try{await video.play()}catch(e){feedback('请点击播放按钮重试')}}else video.pause();controls(true);player.focus({preventScroll:true});armHide()}
function jump(delta){video.currentTime=Math.min(video.duration||12,Math.max(0,video.currentTime+delta));sync();feedback(delta>0?'快进 5 秒':'后退 5 秒');controls(true)}
function setMuted(value){video.muted=value;const b=$('[data-action=mute]');b.innerHTML=icon(value?'muted':'sound');b.setAttribute('aria-label',value?'开启声音':'静音');b.setAttribute('aria-pressed',String(value));armHide()}
async function fullscreen(){try{if(document.fullscreenElement)await document.exitFullscreen();else await device.requestFullscreen()}catch(e){feedback('浏览器未允许全屏，可切换横屏预览')}controls(true)}
$$('[data-action]').forEach(b=>b.addEventListener('click',async()=>{switch(b.dataset.action){case'play':await togglePlay();break;case'back':jump(-5);break;case'mute':setMuted(!video.muted);break;case'fullscreen':await fullscreen();break;case'close':if(document.fullscreenElement)await document.exitFullscreen();video.pause();closed=true;$('#closedPanel').hidden=false;clearTimeout(hideTimer);$('[data-action=reopen]').focus({preventScroll:true});break;case'reopen':closed=false;$('#closedPanel').hidden=true;controls(true);player.focus({preventScroll:true});break;case'retry':$('#errorPanel').hidden=true;initialized=false;video.load();break}}));
document.addEventListener('fullscreenchange',()=>{const b=$('[data-action=fullscreen]');b.innerHTML=icon(document.fullscreenElement?'exit':'fullscreen');b.setAttribute('aria-label',document.fullscreenElement?'退出全屏':'全屏播放');if(!document.fullscreenElement&&menu.matches(':popover-open'))menu.hidePopover()});
$$('[data-concept]').filter(b=>b.tagName==='BUTTON').forEach(b=>b.addEventListener('click',()=>{device.dataset.concept=b.dataset.concept;$$('.concept').forEach(e=>{const selected=e===b;e.classList.toggle('active',selected);e.setAttribute('aria-pressed',String(selected))});const n=notes[b.dataset.concept];$('.note-index').textContent=`${n[0]} / 03`;$('#noteTitle').textContent=n[1];$('#noteBody').textContent=n[2];$('#tags').innerHTML=n[3].map(t=>`<span>${t}</span>`).join('');if(menu.matches(':popover-open'))menu.hidePopover();controls(true)}));
$$('button[data-layout]').forEach(b=>b.addEventListener('click',()=>{device.dataset.layout=b.dataset.layout;$$('button[data-layout]').forEach(e=>{e.classList.toggle('selected',e===b);e.setAttribute('aria-pressed',String(e===b))});controls(true)}));
$$('button[data-source]').forEach(b=>b.addEventListener('click',()=>{if(b.dataset.source===device.dataset.source)return;video.pause();device.dataset.source=b.dataset.source;initialized=false;video.poster=`assets/${b.dataset.source}.jpg`;video.src=`assets/${b.dataset.source}.webm`;video.load();$$('button[data-source]').forEach(e=>{e.classList.toggle('selected',e===b);e.setAttribute('aria-pressed',String(e===b))});controls(true)}));
$$('[data-speed]').forEach(b=>{b.setAttribute('aria-pressed',String(b.dataset.speed==='1'));b.addEventListener('click',()=>{video.playbackRate=Number(b.dataset.speed);$('#speedButton').textContent=`${b.dataset.speed}×`;$('#speedButton').setAttribute('aria-label',`播放倍速：${b.dataset.speed} 倍`);$$('[data-speed]').forEach(e=>e.setAttribute('aria-pressed',String(e===b)));menu.hidePopover();controls(true);feedback(`${b.dataset.speed} 倍速`)})});
menu.addEventListener('toggle',()=>{if(menu.matches(':popover-open')){clearTimeout(hideTimer);requestAnimationFrame(()=>{const r=$('#speedButton').getBoundingClientRect();menu.style.left=`${Math.min(window.innerWidth-145,Math.max(145,r.left+r.width/2))}px`;menu.style.bottom=`${Math.max(20,window.innerHeight-r.top+12)}px`})}else armHide()});
seek.addEventListener('pointerdown',()=>{seeking=true;$('.timeline').classList.add('seeking');clearTimeout(hideTimer)});
seek.addEventListener('input',()=>{seeking=true;clearTimeout(hideTimer);$('#seekPreview').textContent=format(Number(seek.value));sync()});
function finishSeek(){if(!seeking)return;video.currentTime=Number(seek.value);seeking=false;$('.timeline').classList.remove('seeking');sync();armHide()}
seek.addEventListener('change',finishSeek);seek.addEventListener('pointercancel',finishSeek);seek.addEventListener('blur',finishSeek);
$('#pictureTap').addEventListener('pointerdown',e=>{
 e.preventDefault();if(e.button!==0)return;
 if(activePointer!==null){finishHold();suppressPressClick=true;return}
 activePointer=e.pointerId;pressOrigin={x:e.clientX,y:e.clientY,time:performance.now()};suppressPressClick=false;
 e.currentTarget.setPointerCapture(e.pointerId);
 const r=player.getBoundingClientRect(),x=e.clientX-r.left;
 if(x<=r.width*.35||x>=r.width*.65)holdTimer=setTimeout(()=>{
  if(video.paused||video.ended||closed||seeking||suppressPressClick)return;
  holdOriginalRate=video.playbackRate;holdActive=true;video.playbackRate=2;suppressPressClick=true;
  $('#holdIndicator').hidden=false;clearTimeout(tapTimer);controls(false);
 },500);
});
$('#pictureTap').addEventListener('pointermove',e=>{if(e.pointerId!==activePointer||!pressOrigin)return;if(Math.hypot(e.clientX-pressOrigin.x,e.clientY-pressOrigin.y)>18){finishHold();suppressPressClick=true}});
$('#pictureTap').addEventListener('pointerup',e=>{if(e.pointerId===activePointer){if(pressOrigin&&performance.now()-pressOrigin.time>=500)suppressPressClick=true;finishHold();activePointer=null;pressOrigin=null}});
$('#pictureTap').addEventListener('pointercancel',()=>{finishHold();activePointer=null;pressOrigin=null;suppressPressClick=true});
$('#pictureTap').addEventListener('lostpointercapture',()=>{finishHold();activePointer=null;pressOrigin=null});
$('#pictureTap').addEventListener('contextmenu',e=>e.preventDefault());
$('#pictureTap').addEventListener('click',()=>{if(suppressPressClick)return;clearTimeout(tapTimer);tapTimer=setTimeout(()=>controls(player.classList.contains('controls-hidden')),210)});
$('#pictureTap').addEventListener('dblclick',e=>{if(suppressPressClick)return;clearTimeout(tapTimer);const r=player.getBoundingClientRect();jump(e.clientX-r.left<r.width/2?-5:5)});
player.addEventListener('focusin',()=>{clearTimeout(hideTimer);if(!closed)controls(true)});player.addEventListener('focusout',()=>setTimeout(armHide,0));
// Keep keyboard controls scoped to the preview so the design picker remains a normal page.
player.addEventListener('keydown',e=>{if(e.target.matches('input,button')||menu.matches(':popover-open'))return;const k=e.key.toLowerCase();if([' ','arrowleft','arrowright','m','f','escape'].includes(k))e.preventDefault();if(k===' ')togglePlay();if(k==='arrowleft')jump(-5);if(k==='arrowright')jump(5);if(k==='m')setMuted(!video.muted);if(k==='f')fullscreen();if(k==='escape'){if(document.fullscreenElement)document.exitFullscreen();else if(closed){closed=false;$('#closedPanel').hidden=true;controls(true)}else controls(!player.classList.contains('controls-hidden'))}});
$('#themeToggle').addEventListener('click',()=>{const dark=document.body.classList.toggle('dark');$('#themeToggle').textContent=`画布 · ${dark?'深色':'浅色'}`;$('#themeToggle').setAttribute('aria-pressed',String(dark))});
$('#reset').addEventListener('click',()=>{video.pause();video.currentTime=0;video.playbackRate=1;setMuted(true);$('#speedButton').textContent='1×';$$('[data-speed]').forEach(e=>e.setAttribute('aria-pressed',String(e.dataset.speed==='1')));closed=false;$('#closedPanel').hidden=true;controls(true);sync()});
video.addEventListener('loadeddata',()=>{if(!initialized){video.currentTime=3.2;initialized=true}sync()});
['timeupdate','durationchange','seeked','ratechange'].forEach(e=>video.addEventListener(e,sync));
video.addEventListener('play',()=>{sync();armHide()});video.addEventListener('pause',()=>{finishHold();sync();controls(true)});video.addEventListener('ended',()=>{finishHold();sync();controls(true)});video.addEventListener('error',()=>{finishHold();$('#errorPanel').hidden=false;controls(true)});
document.addEventListener('visibilitychange',()=>{if(document.hidden){finishHold();video.pause()}});
window.addEventListener('blur',()=>{finishHold();activePointer=null;pressOrigin=null;suppressPressClick=true});
sync();
$('button[data-concept=b]').click();
