const fs=require('fs'),path=require('path'),{pathToFileURL}=require('url');
const {chromium}=require('playwright');
const assert=(condition,message)=>{if(!condition)throw new Error(message)};
(async()=>{
const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {}),args:['--autoplay-policy=no-user-gesture-required']});
const page=await browser.newPage({viewport:{width:1440,height:1050}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);await page.waitForFunction(()=>document.querySelector('video').readyState>=2);await page.waitForTimeout(300);
assert(await page.$eval('#device',e=>e.dataset.concept==='b'),'B selected by default');
const r=await page.locator('#player').boundingBox();
for(const [fraction,rate] of [[.12,1.25],[.88,1.5]]){
 await page.$eval('video',(v,rate)=>{v.currentTime=1;v.playbackRate=rate;return v.play()},rate);
 await page.mouse.move(r.x+r.width*fraction,r.y+r.height*.4);await page.mouse.down();await page.waitForTimeout(670);
 assert(await page.$eval('video',v=>v.playbackRate===2),'edge hold doubles speed');
 assert(await page.locator('#holdIndicator').isVisible(),'hold badge visible');
 assert(await page.$eval('#player',e=>e.classList.contains('controls-hidden')),'dock hidden during hold');
 if(fraction===.88)await page.locator('#device').screenshot({path:path.join(__dirname,'screenshots','b-holding.png')});
 await page.mouse.up();await page.waitForTimeout(300);
 assert(await page.$eval('video',(v,rate)=>v.playbackRate===rate,rate),'release restores selected rate');
 assert(!await page.locator('#holdIndicator').isVisible(),'hold badge dismissed');
 assert(await page.$eval('#player',e=>!e.classList.contains('controls-hidden')),'release reveals dock without extra tap');
}
await page.$eval('video',v=>{v.currentTime=1;v.playbackRate=1.25;return v.play()});
await page.mouse.move(r.x+r.width*.12,r.y+r.height*.4);await page.mouse.down();await page.waitForTimeout(650);await page.locator('#pictureTap').dispatchEvent('pointercancel',{pointerId:1});
assert(await page.$eval('video',v=>v.playbackRate===1.25),'cancel restores speed');await page.mouse.up();
await page.$eval('video',v=>{v.pause();v.playbackRate=1.5});await page.mouse.move(r.x+r.width*.88,r.y+r.height*.4);await page.mouse.down();await page.waitForTimeout(650);await page.mouse.up();
assert(await page.$eval('video',v=>v.paused&&v.playbackRate===1.5),'paused hold never starts playback');
await page.$eval('video',v=>{v.currentTime=1;v.playbackRate=1;return v.play()});await page.mouse.move(r.x+r.width*.5,r.y+r.height*.4);await page.mouse.down();await page.waitForTimeout(650);assert(await page.$eval('video',v=>v.playbackRate===1),'center hold does not boost');await page.mouse.up();
await page.mouse.move(r.x+r.width*.12,r.y+r.height*.4);await page.mouse.down();await page.mouse.move(r.x+r.width*.25,r.y+r.height*.4);await page.waitForTimeout(650);assert(await page.$eval('video',v=>v.playbackRate===1),'movement cancels pending hold');await page.mouse.up();
await page.click('#reset');await page.click('.dock-play');await page.waitForTimeout(500);await page.click('#speedButton');await page.waitForTimeout(2700);assert(await page.locator('#speedMenu').isVisible(),'open menu stays visible');await page.click('[data-speed="1.25"]');await page.waitForTimeout(2750);assert(await page.$eval('#player',e=>e.classList.contains('controls-hidden')),'auto-hide restarts after speed menu closes');
await page.mouse.move(r.x+r.width*.12,r.y+r.height*.4);await page.mouse.down();await page.waitForTimeout(650);await page.evaluate(()=>window.dispatchEvent(new Event('blur')));assert(await page.$eval('video',v=>v.playbackRate===1.25),'window blur restores rate');await page.mouse.up();
assert(!errors.length,errors.join('\n'));fs.writeFileSync(path.join(__dirname,'hold-validation.json'),JSON.stringify({date:'2026-11-14',passed:true,scenarios:['B default','left/right hold','restore 1.25 and 1.5 rates','hold badge and hidden dock','pointer cancellation','paused hold','center hold','movement cancellation','menu pins controls and restarts timeout','window blur restoration'],errors},null,2));await browser.close();console.log('Hold and dock tests passed.');
})().catch(e=>{console.error(e);process.exit(1)});
