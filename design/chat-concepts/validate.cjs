const fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
const {chromium}=require('playwright');
const assert=(condition,message)=>{if(!condition)throw new Error(message)};
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {})});
 const page=await browser.newPage({viewport:{width:1540,height:1080},deviceScaleFactor:1});
 const errors=[],network=[],checks=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))network.push(r.url())});
 const screenshots=path.join(__dirname,'screenshots');fs.mkdirSync(screenshots,{recursive:true});
 const target=which=>page.locator(`[data-phone=${which}]`);
 await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
 for(const concept of ['a','b','c']){
  await page.click(`button.concept[data-concept=${concept}]`);
  assert(await target('inbox').locator('.conversation').count()>0,concept+': inbox renders');
  for(const which of ['dm','group']){
   const p=target(which);
   await p.locator('textarea').fill('本地演示：长中文输入，以及 <script> 字符不会作为代码执行。');
   await p.locator('[data-action=emoji]').click();
   assert(await p.locator('textarea').inputValue()!=='',concept+': attachment keeps draft');
   await p.locator('[data-action=insert-emoji]').first().click();
   await p.locator('.send-button').click();await page.waitForTimeout(720);
   assert(await p.locator('.message.me').last().innerText().then(t=>t.includes('已发送')),concept+which+': local send');
   await p.locator('.reply-action').last().click();assert(await p.locator('.composer-quote').count()===1,'quote visible');
   await p.locator('[data-action=cancel-quote]').click();assert(await p.locator('.composer-quote').count()===0,'quote removed');
   await p.locator('[data-action=details]').last().click();await p.locator('[data-action=toggle-mute]').click();assert(await p.locator('[data-action=toggle-mute] .switch').getAttribute('aria-checked')==='true','mute toggle');
   await page.keyboard.press('Escape');assert(await p.locator('.overlay').count()===0,'Escape closes sheet');
  }
  if(concept==='b'){
   await target('inbox').locator('[data-action=track][data-id=group]').click();assert(await target('inbox').locator('.room-card').count()===3,'B grouped directory');
   await target('group').locator('[data-action=group-tab][data-id=images]').click();assert(await target('group').locator('.gallery button').count()===4,'B group gallery');
   await target('group').locator('[data-action=group-tab][data-id=info]').click();assert(await target('group').locator('.group-room-summary').count()===1,'B group info');
   await target('group').locator('[data-action=group-tab][data-id=chat]').click();
   await target('inbox').locator('[data-action=track][data-id=dm]').click();
  }
  // Reset local sends for clean concept portraits.
  await page.click('#reset');
  await page.screenshot({path:path.join(screenshots,`${concept}-overview.png`),fullPage:true});
  for(const which of ['inbox','dm','group'])await target(which).screenshot({path:path.join(screenshots,`${concept}-${which}.png`)});
  await page.click('#themeToggle');await page.screenshot({path:path.join(screenshots,`${concept}-dark.png`),fullPage:true});await page.click('#themeToggle');
  checks.push(`${concept}: render, send, draft preservation, emoji, quote/cancel, mute, Escape, light/dark`);
 }
 await page.click('button.concept[data-concept=a]');
 await target('inbox').locator('input[type=search]').fill('不存在的名字');assert(await target('inbox').locator('.empty-state').count()===1,'search empty');await target('inbox').locator('[data-action=clear-search]').click();
 await target('inbox').locator('input[type=search]').fill('小夏');await target('inbox').locator('[data-action=open]').click();assert(await target('dm').locator('.chat-heading strong').innerText()==='小夏','conversation selection');
 await target('dm').locator('[data-action=back]').click();assert(await target('inbox').locator('input').inputValue()==='小夏','return preserves search');
 await target('inbox').locator('input').fill('');
 await target('inbox').locator('[data-action=requests]').click();await target('inbox').locator('[data-action=accept]').click();assert(await target('dm').locator('.chat-heading strong').innerText()==='橘子海','accept correct identity');
 await page.selectOption('#stateSelect','error');await target('dm').locator('textarea').fill('保留失败消息');await target('dm').locator('.send-button').click();await page.waitForTimeout(720);assert(await target('dm').locator('.retry-send').count()===1,'failed send');await target('dm').locator('.retry-send').click();await page.waitForTimeout(620);assert(await target('dm').locator('.retry-send').count()===0,'retry success');
 await page.selectOption('#stateSelect','empty');assert(await target('inbox').locator('.empty-state').count()===1,'empty state');await target('inbox').locator('[data-action=restore]').click();
 await target('dm').locator('.chat-scroll').evaluate(e=>e.scrollTop=0);await target('dm').locator('[data-action=earlier]').click();assert(await target('dm').locator('[data-message^=old-]').count()===1,'earlier messages');
 checks.push('search/no-results, selected identity, return context, stranger acceptance, empty/error, failed-send retry, earlier history');
 for(const width of [390,320]){
  await page.setViewportSize({width,height:1000});
  for(const concept of ['a','b','c']){
   await page.click(`button.concept[data-concept=${concept}]`);await page.click('#reset');
   for(const which of ['inbox','dm','group']){
    await page.click(`[data-pane=${which}]`);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${width}/${concept}/${which}: viewport overflow`);
    const overflow=await target(which).evaluate(root=>[...root.querySelectorAll('button,textarea,.message-bubble')].filter(e=>{const b=e.getBoundingClientRect(),p=root.getBoundingClientRect();return b.width>0&&(b.left<p.left-1||b.right>p.right+1)}).map(e=>e.className));
    assert(!overflow.length,`${width}/${concept}/${which}: internal overflow ${overflow}`);
    if(width===390)await target(which).screenshot({path:path.join(screenshots,`${concept}-mobile-${which}.png`)});
   }
   await page.click('#fontToggle');await page.click('#themeToggle');
   for(const which of ['inbox','dm','group']){await page.click(`[data-pane=${which}]`);assert(await target(which).evaluate(e=>e.scrollWidth<=e.clientWidth),`${width}/${concept}/${which}: large-type overflow`);}
   if(width===320)await target('group').screenshot({path:path.join(screenshots,`${concept}-320-dark-large.png`)});
   await page.click('#fontToggle');await page.click('#themeToggle');
  }
 }
 checks.push('390px/320px × 3 concepts × 3 screens, light/dark, large type; no horizontal overflow');
 await page.emulateMedia({reducedMotion:'reduce'});await target('group').locator('[data-action=details]').last().click();
 assert(await target('group').locator('.sheet').evaluate(e=>getComputedStyle(e).animationName)==='none','reduced motion');await page.keyboard.press('Escape');checks.push('reduced-motion dialog');
 for(const [file,concept]of [['方案A-统一收件箱.html','a'],['方案B-双轨分区.html','b'],['方案C-紧凑对话流.html','c']]){
  await page.goto(pathToFileURL(path.join(__dirname,file)).href);assert(await page.locator('#screens').getAttribute('data-concept')===concept,'standalone default '+concept);
 }
 assert(!errors.length,errors.join('\n'));assert(!network.length,'No network requests permitted');checks.push('3 offline single-file outputs, correct defaults, zero remote requests, zero JS errors');
 fs.writeFileSync(path.join(__dirname,'validation.json'),JSON.stringify({date:'2026-11-14',passed:true,checks,errors,remoteRequests:network,scope:'Desktop Chrome browser validation; no physical-phone or native protocol testing'},null,2));
 console.log(JSON.stringify({passed:true,checks:checks.length,errors,remoteRequests:network}));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
