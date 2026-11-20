const fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
const {chromium}=require('playwright');
const assert=(ok,message)=>{if(!ok)throw Error(message)};
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {})});
 const context=await browser.newContext({viewport:{width:1540,height:1080},hasTouch:true});
 const page=await context.newPage(),errors=[],requests=[],checks=[];
 page.on('pageerror',e=>{errors.push(e.message);console.error('PAGE ERROR',e.message)});page.on('request',r=>{if(/^https?:/.test(r.url()))requests.push(r.url())});
 const screens=path.join(__dirname,'screenshots');fs.mkdirSync(screens,{recursive:true});
 await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
 const p=which=>page.locator(`[data-phone=${which}]`),dm=p('dm'),group=p('group'),inbox=p('inbox');
 assert(await page.locator('#screens').getAttribute('data-concept')==='c','C selected');
 assert(!await page.locator('button.concept').count(),'No alternate directions');
 await page.screenshot({path:path.join(screens,'overview.png'),fullPage:true});
 // Accessible message actions and reference jumps.
 await dm.locator('[data-message=d4]').scrollIntoViewIfNeeded();await dm.locator('[data-message=d4]').click({button:'right'});
 assert(await dm.locator('.message-action-grid button').count()===4,'Context actions');
 await dm.locator('[data-action=reply-message]').click();assert(await dm.locator('.composer-quote').innerText().then(t=>t.includes('这张照片')),'Quote composer');
 await dm.locator('textarea').fill('回复保持正文清楚');await dm.locator('.send-button').click();await page.waitForTimeout(700);
 const quote=dm.locator('.message.me').last().locator('[data-quote-source=d4]');assert(await quote.count()===1,'Quote source ID');await quote.click();assert(await dm.locator('[data-message=d4]').getAttribute('class').then(x=>x.includes('message-located')),'Jump highlight');
 await dm.locator('[data-action=search-chat]').click();await dm.locator('.chat-search input').fill('照片');assert(await dm.locator('mark').count()>0,'Search highlights');await dm.locator('[data-action=search-next]').click();assert((await dm.locator('.search-results').innerText()).includes('/'),'Search count');
 await dm.locator('.chat-search input').fill('没有这种内容');assert(await dm.locator('.search-results').innerText()==='没有找到相关消息','No results');await dm.locator('[data-action=close-search]').click();
 await dm.locator('[data-message=d4]').focus();await page.keyboard.press('Shift+F10');await dm.locator('[data-action=save-message]').click();await inbox.locator('[data-action=saved-messages]').click();assert(await inbox.locator('.saved-message').count()===1,'Saved list');await inbox.locator('[data-action=open-saved]').click();assert(await dm.locator('[data-message=d4]').getAttribute('class').then(x=>x.includes('message-located')),'Saved source jump');
 await dm.locator('[data-message=d4]').click({button:'right'});await dm.locator('[data-action=select-message]').click();await dm.locator('[data-message=d2] .message-bubble').click();assert(await dm.locator('.selection-bar strong').innerText()==='已选 2 条','Multi selection');await dm.locator('[data-action=end-selection]').click();
 checks.push('message menu, keyboard alternative, quote/send/source jump, search/highlight/no-results, local bookmarks/source jump, multi selection');
 // Group profile shared content, draft restoration and row actions.
 await group.locator('.chat-heading').click();await group.locator('[data-action=details-tab][data-tab=images]').click();assert(await group.locator('.shared-gallery button').count()===1,'Shared images');await group.locator('[data-action=shared-locate]').click();assert(await group.locator('[data-message=g2]').getAttribute('class').then(x=>x.includes('message-located')),'Image source jump');
 await group.locator('.chat-heading').click();await group.locator('[data-action=details-tab][data-tab=posts]').click();assert(await group.locator('.shared-post').count()===1,'Shared posts');await page.keyboard.press('Escape');
 await group.locator('[data-action=announcement]').click();assert(await group.locator('[role=dialog]').innerText().then(t=>t.includes('照片')),'Announcement');await page.keyboard.press('Escape');
 await dm.locator('textarea').fill('未发送的草稿');await inbox.locator('[data-action=open][data-id=xia]').click();await inbox.locator('[data-action=open][data-id=lin]').click();assert(await dm.locator('textarea').inputValue()==='未发送的草稿','Draft per conversation');assert(await inbox.locator('[data-id=lin] .conversation-preview').innerText().then(t=>t.includes('草稿')),'Inbox draft');
 await inbox.locator('[data-action=conversation-options][data-id=lin]').click();await inbox.locator('[data-action=list-mute]').click();assert(await inbox.locator('[data-id=lin] .mute-mark').count()===1,'List mute');
 await inbox.locator('[data-action=filter][data-id=unread]').click();assert(await inbox.locator('[data-action=open][data-id=lin]').count()===0,'Opened chat locally read');await inbox.locator('[data-action=filter][data-id=all]').click();
 checks.push('group shared images/posts/source jumps, announcement, per-chat draft, inbox draft and mute, unread filter');
 // Actual pointer tracking and real touch events, rather than directly invoking handlers.
 const session=await context.newCDPSession(page);
 async function swipe(row,distance,vertical=0){
  await row.scrollIntoViewIfNeeded();const box=await row.boundingBox(),x=box.x+box.width*.7,y=box.y+18;
  await session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});
  for(let i=1;i<=7;i++){await session.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x+distance*i/7,y:y+vertical*i/7}]});await page.waitForTimeout(15);}
  const transform=await row.evaluate(e=>getComputedStyle(e).transform);
  await session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await page.waitForTimeout(280);return transform;
 }
 await page.click('#reset');
 const transform=await swipe(dm.locator('[data-message=d4]'),-63);assert(transform!=='none','Finger tracking transform');assert(await dm.locator('.composer-quote').count()===1,'Touch swipe replies');await dm.locator('[data-action=cancel-quote]').click();
 await swipe(dm.locator('[data-message=d4]'),0,-45);assert(await dm.locator('.composer-quote').count()===0,'Vertical scroll does not reply');
 const row=dm.locator('[data-message=d4]');await row.scrollIntoViewIfNeeded();const box=await row.boundingBox(),x=box.x+45,y=box.y+16;
 await session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});await page.waitForTimeout(480);await session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});assert(await dm.locator('.message-action-grid').count()===1,'Touch long press menu');await page.keyboard.press('Escape');
 checks.push('CDP touch: direct horizontal tracking, swipe-to-reply, vertical gesture not treated as reply, 430ms long-press menu');
 // Failures, empty, media, preservation during async send.
 await page.selectOption('#stateSelect','error');await dm.locator('textarea').fill('失败演示');await dm.locator('.send-button').click();await dm.locator('textarea').fill('发送回调后仍保留的新草稿');await page.waitForTimeout(730);assert(await dm.locator('textarea').inputValue()==='发送回调后仍保留的新草稿','Async callback keeps new draft');assert(await dm.locator('.retry-send').count()===1,'Failed message retained');await dm.locator('.retry-send').click();await page.waitForTimeout(610);assert(await dm.locator('.retry-send').count()===0,'Retry');
 await page.selectOption('#stateSelect','empty');assert(await inbox.locator('.empty-state').count()===1,'Empty');await inbox.locator('[data-action=restore]').click();await page.click('#reset');
 checks.push('failed/retry, empty/retry, fresh draft preserved during async completion');
 for(const width of [1540,390,320]){
  await page.setViewportSize({width,height:1080});
  for(const dark of [false,true]){
   if(dark)await page.click('#themeToggle');
   for(const large of [false,true]){
    if(large)await page.click('#fontToggle');
    for(const which of ['inbox','dm','group']){
     if(width<1100)await page.click(`[data-pane=${which}]`);
     assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`Viewport overflow ${width}`);
     const overflow=await p(which).evaluate(root=>[...root.querySelectorAll('button,textarea,.message-bubble')].filter(el=>{const r=el.getBoundingClientRect(),b=root.getBoundingClientRect();return r.width>0&&(r.left<b.left-1||r.right>b.right+1)}).map(el=>el.className));assert(!overflow.length,`${width}/${dark}/${large}/${which}: overflow ${overflow}`);
     if(width===390&&!large)await p(which).screenshot({path:path.join(screens,`${which}-${dark?'dark':'light'}.png`)});
     if(width===320&&dark&&large)await p(which).screenshot({path:path.join(screens,`${which}-320-dark-large.png`)});
    }
    if(large)await page.click('#fontToggle');
   }
   if(dark)await page.click('#themeToggle');
  }
 }
 checks.push('1540/390/320px × 3 views × light/dark × normal/large type, no horizontal clipping');
 await page.emulateMedia({reducedMotion:'reduce'});await group.locator('.chat-heading').click();assert(await group.locator('.sheet').evaluate(el=>getComputedStyle(el).animationName)==='none','Reduced motion');await page.keyboard.press('Escape');
 await page.goto(pathToFileURL(path.join(__dirname,'氢贴吧-私信与吧群-C方案深化.html')).href);assert(await page.locator('#screens').getAttribute('data-concept')==='c','Standalone C');assert(await dm.locator('[data-action=search-chat]').count()===1,'Standalone enhancements');
 assert(!errors.length,errors.join('\n'));assert(!requests.length,'No remote requests');checks.push('reduced motion, offline single file, zero JS errors, zero remote requests');
 fs.writeFileSync(path.join(__dirname,'validation.json'),JSON.stringify({date:'2026-11-14',passed:true,scope:'Desktop Chrome + emulated touch; no physical-device or native messaging protocol validation',checks,errors,remoteRequests:requests},null,2));console.log(JSON.stringify({passed:true,checks:checks.length,errors}));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
