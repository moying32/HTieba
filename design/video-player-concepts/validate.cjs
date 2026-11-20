const fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
const {chromium}=require('playwright');
const assert=(condition,message)=>{if(!condition)throw new Error(message)};
(async()=>{
const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {}),args:['--autoplay-policy=no-user-gesture-required']});
const page=await browser.newPage({viewport:{width:1440,height:1050},deviceScaleFactor:1});const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);await page.waitForFunction(()=>document.querySelector('video').readyState>=2);await page.waitForTimeout(300);
console.log('Media',await page.$eval('video',v=>({duration:v.duration,time:v.currentTime,width:v.videoWidth,height:v.videoHeight})));
for(const concept of ['a','b','c']){
 await page.click(`button[data-concept=${concept}]`);await page.waitForTimeout(300);
 assert(await page.locator('[data-action=fullscreen]:visible').count()===1,`${concept}: single fullscreen`);
 await page.locator('#device').screenshot({path:path.join(__dirname,'screenshots',concept+'-portrait.png')});
 await page.click('button[data-layout=landscape]');await page.waitForTimeout(300);await page.locator('#device').screenshot({path:path.join(__dirname,'screenshots',concept+'-landscape.png')});await page.click('button[data-layout=portrait]');
}
await page.click('button[data-concept=a]');await page.click('.center-play');await page.waitForTimeout(500);assert(await page.$eval('video',v=>!v.paused),'plays');
await page.waitForTimeout(2600);assert(await page.$eval('#player',e=>e.classList.contains('controls-hidden')),'auto-hides');
await page.click('#pictureTap',{position:{x:175,y:200}});await page.waitForTimeout(450);assert(await page.$eval('#player',e=>!e.classList.contains('controls-hidden')),'tap shows');
await page.click('.center-play');assert(await page.$eval('video',v=>v.paused),'pauses');
await page.click('#speedButton');await page.click('[data-speed="1.5"]');assert(await page.$eval('video',v=>v.playbackRate===1.5),'speed');
await page.click('[data-action=mute]');assert(await page.$eval('video',v=>!v.muted),'unmute');
await page.locator('#seek').fill('7');await page.locator('#seek').dispatchEvent('change');await page.waitForTimeout(250);assert(await page.$eval('video',v=>v.currentTime>6.8),'seek');
await page.click('[data-action=fullscreen]');await page.waitForTimeout(300);assert(await page.evaluate(()=>!!document.fullscreenElement),'fullscreen');await page.click('[data-action=fullscreen]');await page.waitForTimeout(200);assert(await page.evaluate(()=>!document.fullscreenElement),'exit fullscreen');
await page.click('[data-action=close]');assert(await page.locator('#closedPanel').isVisible(),'closed');await page.click('[data-action=reopen]');assert(!await page.locator('#closedPanel').isVisible(),'reopened');
await page.click('button[data-source=landscape]');await page.waitForFunction(()=>document.querySelector('video').videoWidth===1280);await page.waitForTimeout(300);await page.locator('#device').screenshot({path:path.join(__dirname,'screenshots','a-wide-source.png')});
await page.click('#themeToggle');await page.screenshot({path:path.join(__dirname,'screenshots','studio-dark.png'),fullPage:true});await page.click('#themeToggle');await page.click('button[data-source=portrait]');await page.waitForFunction(()=>document.querySelector('video').videoWidth===720);await page.click('#reset');await page.screenshot({path:path.join(__dirname,'screenshots','studio.png'),fullPage:true});
await page.setViewportSize({width:390,height:1000});
for(const concept of ['a','b','c']){
await page.click(`button[data-concept=${concept}]`);for(const layout of ['portrait','landscape']){await page.click(`button[data-layout=${layout}]`);await page.waitForTimeout(280);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${concept} ${layout}: no overflow`);await page.locator('#device').screenshot({path:path.join(__dirname,'screenshots',`${concept}-mobile-${layout}.png`)});const bounds=await page.$$eval('.command-row button',els=>els.filter(e=>getComputedStyle(e).display!=='none').map(e=>{const b=e.getBoundingClientRect(),p=e.closest('.player').getBoundingClientRect();return {right:b.right<=p.right+1,bottom:b.bottom<=p.bottom+1,left:b.left>=p.left-1}}));assert(bounds.every(b=>b.right&&b.bottom&&b.left),`${concept} ${layout}: controls inside screen`)}
}
await page.click('button[data-layout=portrait]');await page.emulateMedia({reducedMotion:'reduce'});await page.click('button[data-concept=b]');await page.click('.dock-play');await page.waitForTimeout(300);assert(await page.$eval('video',v=>!v.paused),'reduced motion play');await page.click('.dock-play');
assert(!errors.length,errors.join('\n'));fs.writeFileSync(path.join(__dirname,'validation.json'),JSON.stringify({date:'2026-11-14',passed:true,scenarios:['3 concepts × portrait/landscape × desktop/mobile','single fullscreen toggle','play/pause','auto-hide and tap reveal','seeking','speed','mute','fullscreen and exit','close and reopen','portrait/landscape media','light/dark canvas','reduced motion'],errors},null,2));console.log('All prototype checks passed.');await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
